package top.mcxiafeng.badger.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** One relational result for paging, group counts, explicit select-all and anchor ranks. */
internal object PersonContactQueries {
    private const val TAG_LIMIT = 30
    private const val SORT_LETTER = "CASE WHEN length(c.pinyinInitial) = 1 AND c.pinyinInitial BETWEEN 'A' AND 'Z' THEN c.pinyinInitial ELSE '#' END"
    private const val ORDER = "sectionOrder, groupId, sortLetter, name, id"
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun page(query: String): SupportSQLiteQuery = build(query, "SELECT * FROM person_rows ORDER BY $ORDER")

    fun sections(query: String): SupportSQLiteQuery = build(query, """
        SELECT sectionOrder, groupId, groupName, groupColor, COUNT(*) AS count
        FROM person_rows GROUP BY sectionOrder, groupId, groupName, groupColor ORDER BY sectionOrder, groupId
    """.trimIndent())

    fun ids(query: String): SupportSQLiteQuery = build(query, "SELECT DISTINCT id FROM person_rows")

    fun position(query: String, anchor: PersonContactAnchor): SupportSQLiteQuery = build(query, """
        , anchor_row AS (SELECT sectionOrder, groupId, sortLetter, name, id FROM person_rows WHERE id = ? AND groupId = ?),
        target AS (SELECT * FROM anchor_row UNION ALL SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS(SELECT 1 FROM anchor_row))
        SELECT COUNT(*) FROM person_rows r CROSS JOIN target t WHERE
            r.sectionOrder < t.sectionOrder
            OR (r.sectionOrder = t.sectionOrder AND r.groupId < t.groupId)
            OR (r.sectionOrder = t.sectionOrder AND r.groupId = t.groupId AND r.sortLetter < t.sortLetter)
            OR (r.sectionOrder = t.sectionOrder AND r.groupId = t.groupId AND r.sortLetter = t.sortLetter AND r.name < t.name)
            OR (r.sectionOrder = t.sectionOrder AND r.groupId = t.groupId AND r.sortLetter = t.sortLetter AND r.name = t.name AND r.id < t.id)
    """.trimIndent(), listOf(anchor.contactId, anchor.groupId, anchor.sectionOrder, anchor.groupId,
        anchor.sortLetter, anchor.name, anchor.contactId))

    // Android's basic FTS4 parser treats an explicit AND as a token; spaces work in both modes.
    private fun build(query: String, result: String, extra: List<Any> = emptyList()): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val rows = if (query.isBlank()) mainRows else {
            val fts = words.findAll(query).joinToString(" ") { "\"${it.value}*\"" }
                .ifEmpty { "\"personsearchnomatchtoken\"" }
            val grams = gramQuery(query)
            args.addAll(listOf(fts, grams, query, grams, query))
            searchRows
        }
        args.addAll(extra)
        return SimpleSQLiteQuery("WITH $rows $result", args.toTypedArray())
    }

    // SQLite lower() and LIKE both fold ASCII only. Match its behavior, including supplementary
    // Unicode code points, rather than applying JVM Unicode lowercasing to the query alone.
    private fun gramQuery(query: String): String {
        val points = query.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("").codePoints().toArray()
        val width = minOf(ContactSearchIndex.MAX_GRAM_WIDTH, points.size)
        return (0..points.size - width).map { index ->
            "x" + String(points, index, width).toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }.distinct().joinToString(" ")
    }

    private val mainRows = """
        person_rows AS (SELECT c.*, $SORT_LETTER AS sortLetter,
            '' AS sectionOrder, 0 AS groupId, '' AS groupName, 0 AS groupColor
            FROM contacts c WHERE c.id != -1)
    """.trimIndent()

    private val searchRows = """
        text_hits AS (
            SELECT rowid AS id FROM contacts_fts WHERE contacts_fts MATCH ?
            UNION SELECT ownerId AS id FROM ${ContactSearchIndex.TABLE}
                WHERE ${ContactSearchIndex.TABLE} MATCH ? AND kind IN (${ContactSearchIndex.FIELD_KIND}, ${ContactSearchIndex.PLATFORM_KIND})
                AND instr(lower(rawText), lower(?)) > 0
        ), tag_hits AS (
            SELECT t.* FROM tags t JOIN ${ContactSearchIndex.TABLE} g ON g.ownerId = t.id
            WHERE ${ContactSearchIndex.TABLE} MATCH ? AND g.kind = ${ContactSearchIndex.TAG_KIND}
                AND instr(lower(g.rawText), lower(?)) > 0
            ORDER BY t.pinyinInitial, t.name, t.id LIMIT $TAG_LIMIT
        ), person_rows AS (
            SELECT c.*, $SORT_LETTER AS sortLetter,
                '' AS sectionOrder, 0 AS groupId, '' AS groupName, 0 AS groupColor
            FROM contacts c WHERE c.id != -1 AND c.id IN (SELECT id FROM text_hits)
            UNION ALL
            SELECT c.*, $SORT_LETTER AS sortLetter,
                't' || t.pinyinInitial || char(1) || t.name AS sectionOrder,
                t.id AS groupId, t.name AS groupName, t.color AS groupColor
            FROM tag_hits t JOIN contact_tag ct ON ct.tagId = t.id JOIN contacts c ON c.id = ct.contactId
            WHERE c.id != -1 AND c.id NOT IN (SELECT id FROM text_hits)
        )
    """.trimIndent()
}
