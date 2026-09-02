package top.mcxiafeng.badger.data.queue

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.gson.Gson
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.ProfileDto
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable outbox for pending PUT /api/user/persons/{uuid} requests.
 *
 * This is deliberately separate from the retired `pending_uploads` table and from
 * `ContactCacheEntity.isLocalOnly`: the latter is reserved for create-on-push and
 * its persisted client UUID. Mixing the two could turn a failed PUT into a POST.
 *
 * The table is created lazily on the Room database connection. It is intentionally
 * outside the Room entity graph because this queue is an integration outbox, not
 * application data. Access still uses the same Room SQLite connection.
 */
class PendingPersonUpdateStore(
    private val database: AppDatabase,
) {
    private val gson = Gson()
    private val initialized = AtomicBoolean(false)
    private val initLock = Any()

    fun enqueue(serverId: String, name: String?, profile: ProfileDto?): String {
        require(serverId.isNotBlank()) { "serverId must not be blank" }
        ensureTable()

        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val db = database.openHelper.writableDatabase
        // [F4] 同 serverId 再入队时做字段级 merge，而不是 CONFLICT_REPLACE 整行覆盖：
        // name=null 的半载 PUT（如 updateContactBio）不得覆盖已排队的改名。
        // SELECT + INSERT 包在同一事务内，避免并发 enqueue 互相丢字段。
        db.beginTransaction()
        try {
            var existingName: String? = null
            var existingProfileJson: String? = null
            db.query(
                "SELECT $COL_NAME, $COL_PROFILE_JSON FROM $TABLE WHERE $COL_SERVER_ID = ? LIMIT 1",
                arrayOf(serverId),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) existingName = cursor.getString(0)
                    if (!cursor.isNull(1)) existingProfileJson = cursor.getString(1)
                }
            }
            val mergedName = name ?: existingName
            val mergedProfile = profile
                ?: existingProfileJson?.let {
                    runCatching { gson.fromJson(it, ProfileDto::class.java) }
                        .onFailure { e -> Log.e(TAG, "enqueue: 旧 profileJson 反序列化失败 serverId=${serverId.take(8)}", e) }
                        .getOrNull()
                }
            if (name == null && existingName != null) {
                Log.d(TAG, "enqueue: merge 保留已排队 name（新 payload name=null）serverId=${serverId.take(8)}")
            }
            val values = ContentValues().apply {
                put(COL_SERVER_ID, serverId)
                put(COL_REQUEST_ID, requestId)
                if (mergedName == null) putNull(COL_NAME) else put(COL_NAME, mergedName)
                put(COL_PROFILE_JSON, gson.toJson(mergedProfile))
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_ATTEMPTS, 0)
                put(COL_NEXT_ATTEMPT_AT, now)
                putNull(COL_LAST_ATTEMPT_AT)
                putNull(COL_LAST_ERROR)
            }
            db.insert(TABLE, SQLiteDatabase.CONFLICT_REPLACE, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return requestId
    }

    fun getReady(
        limit: Int = 50,
        now: Long = System.currentTimeMillis(),
    ): List<PendingPersonUpdate> {
        require(limit > 0) { "limit must be positive" }
        ensureTable()
        val db = database.openHelper.writableDatabase
        val result = ArrayList<PendingPersonUpdate>(limit)
        db.query(
            """
            SELECT $COL_SERVER_ID, $COL_REQUEST_ID, $COL_NAME, $COL_PROFILE_JSON,
                   $COL_CREATED_AT, $COL_ATTEMPTS, $COL_NEXT_ATTEMPT_AT,
                   $COL_LAST_ATTEMPT_AT, $COL_LAST_ERROR
            FROM $TABLE
            WHERE $COL_NEXT_ATTEMPT_AT <= ?
            ORDER BY $COL_CREATED_AT ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(now.toString(), limit.toString()),
        ).use { cursor ->
            val serverIdIndex = cursor.getColumnIndexOrThrow(COL_SERVER_ID)
            val requestIdIndex = cursor.getColumnIndexOrThrow(COL_REQUEST_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(COL_NAME)
            val profileIndex = cursor.getColumnIndexOrThrow(COL_PROFILE_JSON)
            val createdAtIndex = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            val attemptsIndex = cursor.getColumnIndexOrThrow(COL_ATTEMPTS)
            val nextAttemptAtIndex = cursor.getColumnIndexOrThrow(COL_NEXT_ATTEMPT_AT)
            val lastAttemptAtIndex = cursor.getColumnIndexOrThrow(COL_LAST_ATTEMPT_AT)
            val lastErrorIndex = cursor.getColumnIndexOrThrow(COL_LAST_ERROR)

            while (cursor.moveToNext()) {
                result += PendingPersonUpdate(
                    serverId = cursor.getString(serverIdIndex),
                    requestId = cursor.getString(requestIdIndex),
                    name = cursor.getString(nameIndex),
                    profile = gson.fromJson(cursor.getString(profileIndex), ProfileDto::class.java),
                    createdAt = cursor.getLong(createdAtIndex),
                    attempts = cursor.getInt(attemptsIndex),
                    nextAttemptAt = cursor.getLong(nextAttemptAtIndex),
                    lastAttemptAt = cursor.getLongOrNull(lastAttemptAtIndex),
                    lastError = cursor.getString(lastErrorIndex),
                )
            }
        }
        return result
    }

    fun deleteIfRequest(serverId: String, requestId: String) {
        ensureTable()
        database.openHelper.writableDatabase.delete(
            TABLE,
            "$COL_SERVER_ID = ? AND $COL_REQUEST_ID = ?",
            arrayOf(serverId, requestId),
        )
    }

    fun recordFailure(
        serverId: String,
        requestId: String,
        error: Throwable,
        now: Long = System.currentTimeMillis(),
    ) {
        ensureTable()
        val attempts = currentAttempts(serverId, requestId) + 1
        val values = ContentValues().apply {
            put(COL_ATTEMPTS, attempts)
            put(COL_NEXT_ATTEMPT_AT, now + retryDelayMillis(attempts))
            put(COL_LAST_ATTEMPT_AT, now)
            put(COL_LAST_ERROR, error.message?.take(500) ?: error.javaClass.simpleName)
            put(COL_UPDATED_AT, now)
        }
        database.openHelper.writableDatabase.update(
            TABLE,
            SQLiteDatabase.CONFLICT_NONE,
            values,
            "$COL_SERVER_ID = ? AND $COL_REQUEST_ID = ?",
            arrayOf(serverId, requestId),
        )
    }

    private fun currentAttempts(serverId: String, requestId: String): Int {
        database.openHelper.writableDatabase.query(
            "SELECT $COL_ATTEMPTS FROM $TABLE WHERE $COL_SERVER_ID = ? AND $COL_REQUEST_ID = ? LIMIT 1",
            arrayOf(serverId, requestId),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 6)
        return 10_000L * (1L shl exponent)
    }

    private fun ensureTable() {
        if (initialized.get()) return
        synchronized(initLock) {
            if (initialized.get()) return
            val db = database.openHelper.writableDatabase
            db.execSQL(CREATE_TABLE_SQL)
            db.execSQL(CREATE_INDEX_SQL)
            initialized.set(true)
        }
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    data class PendingPersonUpdate(
        val serverId: String,
        val requestId: String,
        val name: String?,
        val profile: ProfileDto?,
        val createdAt: Long,
        val attempts: Int,
        val nextAttemptAt: Long,
        val lastAttemptAt: Long?,
        val lastError: String?,
    )

    private companion object {
        private const val TAG = "PendingPersonUpdateStore"
        const val TABLE = "pending_person_updates"
        const val COL_SERVER_ID = "serverId"
        const val COL_REQUEST_ID = "requestId"
        const val COL_NAME = "name"
        const val COL_PROFILE_JSON = "profileJson"
        const val COL_CREATED_AT = "createdAt"
        const val COL_UPDATED_AT = "updatedAt"
        const val COL_ATTEMPTS = "attempts"
        const val COL_NEXT_ATTEMPT_AT = "nextAttemptAt"
        const val COL_LAST_ATTEMPT_AT = "lastAttemptAt"
        const val COL_LAST_ERROR = "lastError"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS pending_person_updates (
                serverId TEXT NOT NULL PRIMARY KEY,
                requestId TEXT NOT NULL,
                name TEXT,
                profileJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                nextAttemptAt INTEGER NOT NULL,
                lastAttemptAt INTEGER,
                lastError TEXT
            )
        """

        const val CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS index_pending_person_updates_nextAttemptAt
            ON pending_person_updates(nextAttemptAt)
        """
    }
}
