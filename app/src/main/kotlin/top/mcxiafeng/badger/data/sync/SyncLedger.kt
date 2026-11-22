package top.mcxiafeng.badger.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.SyncStateEntity
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import java.util.UUID

/** Created and used inside one Room transaction; persisted state belongs to this database only. */
internal class SyncLedger private constructor(
    private val db: AppDatabase,
    private val values: MutableMap<String, String>,
) {
    private var localKeyIndex: MutableMap<String, String>? = null

    fun get(key: String): String? = values[key]
    fun entries(prefix: String): Map<String, String> = values.filterKeys { it.startsWith(prefix) }
    suspend fun put(key: String, value: String) {
        if (values[key] == value) return
        db.syncStateDao().put(SyncStateEntity(key, value))
        val previous = values.put(key, value)
        updateIdIndex(key, previous)
    }
    suspend fun remove(key: String) {
        db.syncStateDao().delete(key)
        val previous = values.remove(key)
        updateIdIndex(key, previous)
    }
    fun shadow(key: String): SyncRoot? = get("shadow/$key")?.let { SyncRoot.read(SyncJson.parse(it)) }
    suspend fun shadow(root: SyncRoot, verified: Boolean = true) {
        put("shadow/${root.key}", SyncJson.encode(root.json()))
        val unverified = "unverified/${root.key}"
        if (verified && get(unverified) != null) remove(unverified)
        if (get("template/${root.key}") != null) remove("template/${root.key}")
    }

    suspend fun remote(kind: String, local: String): String {
        val key = "id/$kind/$local"
        val previous = values[key]
        if (previous != null && shadow("$kind/$previous")?.deleted != true) return previous
        return UUID.randomUUID().toString().also { put(key, it) }
    }
    fun local(kind: String, remote: String): String? {
        val index = localKeyIndex ?: buildIdIndex().also { localKeyIndex = it }
        return index["$kind/$remote"]?.removePrefix("id/$kind/")
    }

    private fun buildIdIndex(): MutableMap<String, String> = mutableMapOf<String, String>().apply {
        for ((key, remote) in this@SyncLedger.values) idKind(key)?.let { putIfAbsent("$it/$remote", key) }
        Log.d(TAG, "已建立事务内标识反向索引 entries=$size")
    }

    private fun updateIdIndex(key: String, previous: String?) {
        val index = localKeyIndex ?: return
        val kind = idKind(key) ?: return
        if (previous != null && index["$kind/$previous"] == key) {
            index.remove("$kind/$previous")
            values.entries.firstOrNull { it.key.startsWith("id/$kind/") && it.value == previous }
                ?.let { index["$kind/$previous"] = it.key }
        }
        val current = values[key] ?: return
        val lookup = "$kind/$current"
        if (index[lookup] == null) index[lookup] = key
        else {
            // Preserve the original first-key semantics even for duplicate legacy mappings.
            index[lookup] = values.entries.first { it.key.startsWith("id/$kind/") && it.value == current }.key
            Log.d(TAG, "保留已有重复标识映射的查找顺序")
        }
    }

    private fun idKind(key: String): String? {
        val separator = key.indexOf('/', ID_PREFIX_LENGTH)
        return if (key.startsWith("id/") && separator >= ID_PREFIX_LENGTH) key.substring(ID_PREFIX_LENGTH, separator) else null
    }
    suspend fun bind(kind: String, local: String, remote: String) {
        val existing = this.local(kind, remote)
        require(existing == null || existing == local) { "同步记录标识冲突" }
        put("id/$kind/$local", remote)
    }
    suspend fun move(kind: String, local: String): String {
        val next = UUID.randomUUID().toString()
        put("id/$kind/$local", next)
        Log.d(TAG, "为已删除资料创建独立副本 kind=$kind")
        return next
    }
    suspend fun highWater(revision: String) {
        if (SyncJson.counter(revision) > SyncJson.counter(get(HIGH_WATER) ?: "0")) put(HIGH_WATER, revision)
    }
    fun sql(): SupportSQLiteDatabase = db.openHelper.writableDatabase

    companion object {
        const val HIGH_WATER = "water/observed"
        const val SNAPSHOT_REVISION = "water/snapshot"
        private const val ID_PREFIX_LENGTH = 3
        private const val TAG = "SyncLedgerTester"
        suspend fun load(db: AppDatabase): SyncLedger = SyncLedger(db,
            db.syncStateDao().getAll().associate { it.key to it.value }.toMutableMap())
    }
}

internal fun SupportSQLiteDatabase.rows(table: String, where: String = "", args: Array<Any> = emptyArray()): List<JsonObject> {
    require(table in BUSINESS_TABLES || table == "sqlite_sequence")
    return query("SELECT * FROM $table${if (where.isBlank()) "" else " WHERE $where"}", args).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(JsonObject().apply {
                cursor.columnNames.forEachIndexed { index, name ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> add(name, JsonNull.INSTANCE)
                        Cursor.FIELD_TYPE_INTEGER -> addProperty(name, cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> addProperty(name, cursor.getDouble(index))
                        Cursor.FIELD_TYPE_STRING -> addProperty(name, cursor.getString(index))
                        else -> error("不支持的业务字段类型")
                    }
                }
            })
        }
    }
}

internal fun SupportSQLiteDatabase.nextId(table: String): Long {
    require(table in BUSINESS_TABLES)
    val maximum = query("SELECT COALESCE(MAX(id), 0) FROM $table").use { it.moveToFirst(); it.getLong(0) }
    val sequence = query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table))
        .use { if (it.moveToFirst()) it.getLong(0) else 0L }
    return Math.addExact(maxOf(maximum, sequence), 1L)
}

internal fun SupportSQLiteDatabase.upsert(table: String, id: Long, values: JsonObject) {
    require(table in BUSINESS_TABLES)
    val fields = values.keySet().toList()
    require(fields.all { COLUMN.matches(it) })
    val content = ContentValues()
    for (key in fields) {
        val value = values[key]
        when {
            value.isJsonNull -> content.putNull(key)
            value.asJsonPrimitive.isString -> content.put(key, value.asString)
            value.asJsonPrimitive.isBoolean -> content.put(key, if (value.asBoolean) 1L else 0L)
            value.asBigDecimal.stripTrailingZeros().scale() <= 0 -> content.put(key, value.asBigDecimal.longValueExact())
            else -> content.put(key, value.asDouble)
        }
    }
    // Android 8/9 ship SQLite versions without ON CONFLICT DO UPDATE. REPLACE would cascade-delete children.
    val updated = update(table, SQLiteDatabase.CONFLICT_ABORT, content, "id = ?", arrayOf(id))
    if (updated == 0) {
        content.put("id", id)
        // MY_CONTACT_ID is a legitimate rowid of -1; CONFLICT_ABORT propagates actual insert failures.
        insert(table, SQLiteDatabase.CONFLICT_ABORT, content)
    }
}

internal val BUSINESS_TABLES = arrayOf("contacts", "contact_fields", "custom_fields", "contact_field_values",
    "contact_platforms", "card_collections", "scan_results", "user_profile", "tags", "contact_tag")
private val COLUMN = Regex("[A-Za-z][A-Za-z0-9_]*")
