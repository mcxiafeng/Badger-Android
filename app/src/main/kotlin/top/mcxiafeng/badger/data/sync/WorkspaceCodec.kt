package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import java.net.URI
import java.text.Normalizer

/** Full private business projection. Local row IDs and filesystem paths never leave the device. */
internal class WorkspaceCodec(private val ledger: SyncLedger, private val context: Context) {
    private val sql = ledger.sql()
    private val assets = WorkspaceAssets(context)

    suspend fun capture(): Map<String, SyncRoot> {
        val files = context.filesDir.canonicalPath
        if (ledger.get("space/filesRoot") != files) ledger.put("space/filesRoot", files)
        val roots = buildList {
            addAll(definitions())
            sql.rows("contacts").forEach { add(contact(it)) }
            sql.rows("card_collections").forEach { add(collection(it)) }
            sql.rows("tags").forEach { add(tag(it)) }
        }
        Log.d(TAG, "已读取用户空间业务快照 roots=${roots.size}")
        return roots.associateBy { it.key }
    }

    private suspend fun definitions(): List<SyncRoot> = buildList {
        for (row in sql.rows("contact_fields")) {
            val id = ledger.remote("fieldDefinition", "system/${row.num("id")}")
            add(SyncRoot("fieldDefinition", id, value = SyncJson.obj(
                "fieldName" to row.str("fieldName"), "fieldKey" to row.str("fieldKey"), "icon" to row.str("icon"),
                "fieldType" to null, "options" to null, "sortOrder" to row.num("sortOrder"),
                "isSystem" to true, "isEnabled" to row.flag("isEnabled"), "createTime" to row.num("createTime"))))
        }
        for (row in sql.rows("custom_fields")) {
            val id = ledger.remote("fieldDefinition", "custom/${row.num("id")}")
            val base = ledger.shadow("fieldDefinition/$id")?.value
            val options = if (row.str("options").isNullOrEmpty() && base?.get("options")?.isJsonNull == true) null else row.str("options")
            add(SyncRoot("fieldDefinition", id, value = SyncJson.obj(
                "fieldName" to row.str("fieldName"), "fieldKey" to null, "icon" to base?.get("icon"),
                "fieldType" to row.str("fieldType"), "options" to options, "sortOrder" to row.num("sortOrder"),
                "isSystem" to false, "isEnabled" to row.flag("isEnabled"), "createTime" to row.num("createTime"))))
        }
    }

    private suspend fun contact(row: JsonObject): SyncRoot {
        val localId = row.num("id")
        val kind = if (localId == MY_CONTACT_ID) "selfCard" else "person"
        val id = ledger.remote(kind, localId.toString())
        val base = ledger.shadow("$kind/$id")?.value ?: ledger.get("template/$kind/$id")?.let(SyncJson::parse)
        val profile = base?.get("profile")?.asJsonObject?.deepCopy() ?: SyncJson.obj(
            "circleName" to null, "accounts" to emptyList<Any>(), "interestTags" to emptyList<Any>(), "avatarAssetId" to null)
        val name = row.str("name").orEmpty()
        val baseAlias = base?.str("alias")
        if (kind == "selfCard" || base == null || baseAlias == null) profile.add("circleName", SyncJson.element(name.ifEmpty { null }))
        profile.add("avatarAssetId", SyncJson.element(assets.reference(row.str("avatarPath"), ledger)))
        val platforms = platforms(localId, row.str("platforms"))
        profile.add("accounts", SyncJson.element(platforms.first))
        val details = SyncJson.obj("bio" to row.str("bio"), "avatarUrl" to row.str("avatarUrl"),
            "createTime" to row.num("createTime"), "updateTime" to row.num("updateTime"),
            "fields" to fields(localId), "platformDetails" to platforms.second)
        val value = if (kind == "selfCard") selfCard(profile, details, row) else SyncJson.obj(
            "alias" to if (base == null || baseAlias != null) name.ifEmpty { null } else null,
            "notes" to row.str("note"), "source" to base?.get("source"), "profile" to profile, "clientData" to details)
        return SyncRoot(kind, id, value = value)
    }

    private suspend fun selfCard(profile: JsonObject, details: JsonObject, contact: JsonObject): JsonObject {
        val row = sql.rows("user_profile", "id = 1").firstOrNull()
        details.add("cardImageAssetId", SyncJson.element(assets.reference(row?.str("cardImagePath"), ledger)))
        details.add("defaultPlatform", SyncJson.element(row?.str("defaultPlatform")))
        details.add("notes", SyncJson.element(contact.str("note")))
        details.addProperty("profileUpdateTime", row?.num("updateTime") ?: contact.num("updateTime"))
        return SyncJson.obj("profile" to profile, "clientData" to details)
    }

    private suspend fun fields(contactId: Long): List<JsonObject> = buildList {
        for (row in sql.rows("contact_field_values", "contactId = ? ORDER BY id", arrayOf(contactId))) {
            val system = row["fieldId"]?.takeUnless { it.isJsonNull }?.asLong
            val custom = row["customFieldId"]?.takeUnless { it.isJsonNull }?.asLong
            require((system == null) != (custom == null)) { "联系方式缺少唯一字段定义" }
            val definition = ledger.remote("fieldDefinition", if (system != null) "system/$system" else "custom/$custom")
            add(SyncJson.obj("id" to ledger.remote("fieldValue", row.num("id").toString()),
                "definitionId" to definition, "value" to row.str("value"),
                "createTime" to row.num("createTime"), "updateTime" to row.num("updateTime")))
        }
    }.sortedBy { it.str("id") }

    private suspend fun platforms(contactId: Long, legacy: String?): Pair<List<JsonObject>, List<JsonObject>> {
        val rows = sql.rows("contact_platforms", "contactId = ? ORDER BY id", arrayOf(contactId))
            .associateBy { requireNotNull(it.str("platformKey")) }.toMutableMap()
        if (!legacy.isNullOrBlank()) {
            val entries = SyncJson.parse(legacy)
            for ((key, value) in entries.entrySet()) if (!rows.containsKey(key) && value.isJsonObject) {
                rows[key] = value.asJsonObject.deepCopy().apply { addProperty("platformKey", key) }
            }
        }
        val core = mutableListOf<JsonObject>()
        val details = mutableListOf<JsonObject>()
        for ((key, row) in rows.toSortedMap()) {
            val id = ledger.remote("platform", "$contactId/$key")
            val value = row.str("value")
            val link = row.str("jumpLink").orEmpty()
            details.add(SyncJson.obj("id" to id, "platformKey" to key, "value" to value, "jumpLink" to link,
                "displayName" to row.str("displayName"), "originalLink" to row.str("originalLink"), "avatarUrl" to row.str("avatarUrl")))
            if (core.size < MAX_CORE_ACCOUNTS && corePlatform(value, link, row.str("displayName"))) {
                core.add(SyncJson.obj("id" to id, "platform" to display(key), "handle" to value,
                    "label" to row.str("displayName")?.let(::display)?.ifEmpty { null }, "url" to link.ifBlank { null }))
            }
        }
        return core.sortedBy { it.str("id") } to details.sortedBy { it.str("id") }
    }

    private fun corePlatform(value: String?, link: String, label: String?): Boolean {
        if (value.isNullOrEmpty() || value.codePointCount(0, value.length) > MAX_HANDLE ||
            value != value.trim { it.isWhitespace() || it == '\u0085' } || value.any(::coreControl)) return false
        if (label != null && (display(label).codePointCount(0, display(label).length) > MAX_NAME || display(label).any(::coreControl))) return false
        if (link.isBlank()) return true
        return try {
            val uri = URI(link)
            uri.scheme in setOf("http", "https") && uri.host != null && uri.rawUserInfo == null && link.length <= MAX_URL
        } catch (e: Exception) {
            Log.e(TAG, "平台链接无法投影到兼容字段，完整私有条目保留；type=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun collection(row: JsonObject): SyncRoot {
        val local = row.num("id")
        val records = buildList {
            for (record in sql.rows("scan_results", "collectionId = ? ORDER BY id", arrayOf(local))) {
                val person = referenceContact(record.num("contactId"))
                add(SyncJson.obj("id" to ledger.remote("scanRecord", record.num("id").toString()), "personId" to person,
                    "scannedTime" to record.num("scannedTime"), "sourceType" to record.str("sourceType"),
                    "rawData" to record.str("rawData"), "ocrText" to record.str("ocrText"),
                    "qrCodeContent" to record.str("qrCodeContent"), "confidence" to record["confidence"]))
            }
        }.sortedBy { it.str("id") }
        val selfId = ledger.remote("selfCard", MY_CONTACT_ID.toString())
        val members = records.mapNotNull { it.str("personId") }.filter { it != selfId }.distinct().sorted()
        val value = SyncJson.obj("name" to row.str("name"), "memberIds" to members,
            "clientData" to SyncJson.obj("description" to row.str("description"),
                "backgroundAssetId" to assets.reference(row.str("backgroundImagePath"), ledger),
                "dominantColor" to row["dominantColor"]?.takeUnless { it.isJsonNull }?.asLong?.and(ARGB_MASK),
                "createTime" to row.num("createTime"), "records" to records))
        return SyncRoot("collection", ledger.remote("collection", local.toString()), value = value)
    }

    private suspend fun tag(row: JsonObject): SyncRoot {
        val local = row.num("id")
        val members = buildList {
            for (relation in sql.rows("contact_tag", "tagId = ? ORDER BY contactId", arrayOf(local))) {
                add(SyncJson.obj("personId" to referenceContact(relation.num("contactId")),
                    "source" to relation.str("source"), "confidence" to relation["confidence"], "createTime" to relation.num("createTime")))
            }
        }.sortedBy { it.str("personId") }
        val selfId = ledger.remote("selfCard", MY_CONTACT_ID.toString())
        val people = members.mapNotNull { it.str("personId") }.filter { it != selfId }.distinct().sorted()
        val value = SyncJson.obj("name" to row.str("name"), "memberIds" to people,
            "clientData" to SyncJson.obj("color" to (row.num("color") and ARGB_MASK), "source" to row.str("source"),
                "showDot" to row.flag("showDot"), "createTime" to row.num("createTime"), "members" to members))
        return SyncRoot("tag", ledger.remote("tag", local.toString()), value = value)
    }

    private suspend fun referenceContact(local: Long): String = ledger.remote(
        if (local == MY_CONTACT_ID) "selfCard" else "person", local.toString())

    companion object {
        private const val TAG = "WorkspaceCodecTester"
        private const val ARGB_MASK = 0xFFFFFFFFL
        private const val MAX_CORE_ACCOUNTS = 32
        private const val MAX_NAME = 100
        private const val MAX_HANDLE = 256
        private const val MAX_URL = 2048
        private fun display(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
            .trim { it.isWhitespace() || it == '\u0085' }
        private fun coreControl(char: Char): Boolean = char.isISOControl() ||
            char.category == CharCategory.LINE_SEPARATOR || char.category == CharCategory.PARAGRAPH_SEPARATOR
    }
}

internal fun JsonObject.str(key: String): String? = SyncJson.text(this, key)
internal fun JsonObject.num(key: String): Long = get(key)?.takeUnless(JsonElement::isJsonNull)?.asBigDecimal?.longValueExact() ?: 0L
internal fun JsonObject.flag(key: String): Boolean = get(key)?.let {
    if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else !it.isJsonNull && it.asLong != 0L
} ?: false
internal fun JsonObject.details(): JsonObject? = get("clientData")?.takeUnless(JsonElement::isJsonNull)?.asJsonObject
internal fun JsonObject.items(key: String): List<JsonObject> = SyncJson.array(this, key).map { it.asJsonObject }
internal fun JsonObject.putNullable(key: String, value: JsonElement?) = add(key, value ?: JsonNull.INSTANCE)
