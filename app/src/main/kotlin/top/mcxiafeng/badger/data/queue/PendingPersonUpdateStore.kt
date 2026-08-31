package top.mcxiafeng.badger.data.queue

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.ProfileDto
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable outbox for failed/pending PUT /api/user/persons/{uuid} requests.
 *
 * This is deliberately separate from the retired `pending_uploads` table and from
 * `ContactCacheEntity.isLocalOnly`: the latter is reserved for create-on-push and
 * its persisted client UUID. Mixing the two would turn a failed PUT into a POST.
 *
 * The table is created lazily on the Room database connection. It is intentionally
 * kept outside the Room entity graph because this queue is an integration outbox,
 * not application data; all access still uses the same Room SQLite connection so
 * it participates in the database's transaction/locking semantics.
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
        val values = ContentValues().apply {
            put(COL_SERVER_ID, serverId)
            put(COL_REQUEST_ID, requestId)
            name?.let { put(COL_NAME, it) } ?: putNull(COL_NAME)
            put(COL_PROFILE_JSON, gson.toJson(profile))
            put(COL_CREATED_AT, now)
            put(COL_UPDATED_AT, now)
            put(COL_ATTEMPTS, 0)
            put(COL_NEXT_ATTEMPT_AT, now)
            putNull(COL_LAST_ATTEMPT_AT)
            putNull(COL_LAST_ERROR)
        }
        database.openHelper.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        return requestId
    }

    fun getReady(limit: Int = 50, now: Long = System.currentTimeMillis()): List<PendingPersonUpdate> {
        require(limit > 0) { "limit must be positive" }
        ensureTable()
        val db = database.openHelper.writableDatabase
        val result = ArrayList<PendingPersonUpdate>(limit)
        db.query(
            TABLE,
            arrayOf(
                COL_SERVER_ID,
                COL_REQUEST_ID,
                COL_NAME,
                COL_PROFILE_JSON,
                COL_CREATED_AT,
                COL_ATTEMPTS,
                COL_NEXT_ATTEMPT_AT,
                COL_LAST_ATTEMPT_AT,
                COL_LAST_ERROR,
            ),
            "$COL_NEXT_ATTEMPT_AT <= ?",
            arrayOf(now.toString()),
            null,
            null,
            "$COL_CREATED_AT ASC",
            limit.toString(),
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

    fun hasReady(now: Long = System.currentTimeMillis()): Boolean {
        ensureTable()
        database.openHelper.writableDatabase.query(
            TABLE,
            arrayOf(COL_SERVER_ID),
            "$COL_NEXT_ATTEMPT_AT <= ?",
            arrayOf(now.toString()),
            null,
            null,
            null,
            "1",
        ).use { return it.moveToFirst() }
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
        val db = database.openHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_ATTEMPTS, "MAX($COL_ATTEMPTS, 0) + 1")
            put(COL_NEXT_ATTEMPT_AT, now + retryDelayMillis(currentAttempts(serverId, requestId) + 1))
            put(COL_LAST_ATTEMPT_AT, now)
            put(COL_LAST_ERROR, error.message?.take(500) ?: error.javaClass.simpleName)
            put(COL_UPDATED_AT, now)
        }
        // ContentValues cannot express SQL expressions, so perform the increment and
        // next-attempt calculation in a single UPDATE after reading the current count.
        val attempts = currentAttempts(serverId, requestId)
        values.put(COL_ATTEMPTS, attempts + 1)
        values.put(COL_NEXT_ATTEMPT_AT, now + retryDelayMillis(attempts + 1))
        db.update(
            TABLE,
            values,
            "$COL_SERVER_ID = ? AND $COL_REQUEST_ID = ?",
            arrayOf(serverId, requestId),
        )
    }

    private fun currentAttempts(serverId: String, requestId: String): Int {
        database.openHelper.writableDatabase.query(
            TABLE,
            arrayOf(COL_ATTEMPTS),
            "$COL_SERVER_ID = ? AND $COL_REQUEST_ID = ?",
            arrayOf(serverId, requestId),
            null,
            null,
            null,
            "1",
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
            database.openHelper.writableDatabase.execSQL(CREATE_TABLE_SQL)
            database.openHelper.writableDatabase.execSQL(CREATE_INDEX_SQL)
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
