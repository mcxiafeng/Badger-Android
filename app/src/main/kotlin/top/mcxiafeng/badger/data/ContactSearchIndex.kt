package top.mcxiafeng.badger.data

import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Disposable local search data. No Room entity or schema version change is needed. */
object ContactSearchIndex : RoomDatabase.Callback() {
    internal const val TABLE = "person_search_grams_v1"
    private const val TAG = "ContactSearchIndexTester"
    internal const val MAX_GRAM_WIDTH = 3
    internal const val FIELD_KIND = 1
    internal const val PLATFORM_KIND = 2
    internal const val TAG_KIND = 3
    private const val ROW_ID_STRIDE = 4

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.beginTransaction()
        try {
            val exists = db.query("SELECT 1 FROM sqlite_master WHERE name = '$TABLE'").use { it.moveToFirst() }
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts4(grams, rawText, ownerId, kind, notindexed=rawText, notindexed=ownerId, notindexed=kind)")
            sources.forEach { installTriggers(db, it) }
            if (!exists) {
                sources.forEach { source ->
                    db.execSQL("INSERT INTO $TABLE(rowid, grams, rawText, ownerId, kind) " +
                        "SELECT id * $ROW_ID_STRIDE + ${source.kind}, ${grams(source.text)}, ${source.text}, ${source.owner}, ${source.kind} FROM ${source.table}")
                }
                Log.d(TAG, "Built local field/platform/tag FTS4 index")
            }
            db.setTransactionSuccessful()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to build local search index", error)
            throw error
        } finally {
            db.endTransaction()
        }
    }

    private fun installTriggers(db: SupportSQLiteDatabase, source: Source) {
        val prefix = "person_search_${source.table}"
        val newText = source.text.replace(Regex("\\b(value|displayName|name)\\b"), "new.$1")
        val newOwner = if (source.owner == "id") "new.id" else "new.${source.owner}"
        val insert = "INSERT INTO $TABLE(rowid, grams, rawText, ownerId, kind) VALUES " +
            "(new.id * $ROW_ID_STRIDE + ${source.kind}, ${grams(newText)}, $newText, $newOwner, ${source.kind});"
        val delete = "DELETE FROM $TABLE WHERE rowid = old.id * $ROW_ID_STRIDE + ${source.kind};"
        db.execSQL("CREATE TRIGGER IF NOT EXISTS ${prefix}_ai AFTER INSERT ON ${source.table} BEGIN $insert END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS ${prefix}_ad AFTER DELETE ON ${source.table} BEGIN $delete END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS ${prefix}_au AFTER UPDATE ON ${source.table} BEGIN $delete $insert END")
    }

    // Hex-encoded 1–3 character grams avoid tokenizer/operator interpretation and support Chinese,
    // punctuation and account-number infixes. Exact instr() verification follows the FTS candidate set.
    private fun grams(text: String): String = """
        (WITH RECURSIVE positions(pos) AS (
            SELECT 1 WHERE length($text) > 0
            UNION ALL SELECT pos + 1 FROM positions WHERE pos < length($text)
        ), widths(width) AS (SELECT 1 UNION ALL SELECT width + 1 FROM widths WHERE width < $MAX_GRAM_WIDTH)
        SELECT group_concat('x' || hex(substr(lower($text), pos, width)), ' ')
        FROM positions CROSS JOIN widths WHERE pos + width - 1 <= length($text))
    """.trimIndent()

    private data class Source(val table: String, val text: String, val owner: String, val kind: Int)
    private val sources = listOf(
        Source("contact_field_values", "value", "contactId", FIELD_KIND),
        Source("contact_platforms", "coalesce(value, '') || char(10) || coalesce(displayName, '')", "contactId", PLATFORM_KIND),
        Source("tags", "name", "id", TAG_KIND),
    )
}
