package top.mcxiafeng.badger.data.sync

import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import top.mcxiafeng.badger.utils.PinyinUtils

/** Applies only verified roots, inside the same Room transaction as their shadows. */
internal class WorkspaceApplier(private val ledger: SyncLedger) {
    private val sql = ledger.sql()
    private val allocatedIds = mutableMapOf<String, Long>()

    suspend fun bindExisting(roots: List<SyncRoot>) {
        val tags = sql.rows("tags").groupBy { tagNameKey(requireNotNull(it.str("name"))) }
        for (root in roots.filter { !it.deleted }) when (root.kind) {
            "selfCard" -> ledger.bind(root.kind, MY_CONTACT_ID.toString(), root.id)
            "tag" -> {
                val name = requireNotNull(root.value?.str("name"))
                val candidate = tags[tagNameKey(name)]?.singleOrNull()
                if (candidate != null) {
                    val local = candidate.num("id").toString()
                    val old = ledger.get("id/tag/$local")
                    if (old == null || old == root.id || ledger.shadow("tag/$old") == null) ledger.bind("tag", local, root.id)
                }
            }
            "fieldDefinition" -> {
                val value = requireNotNull(root.value)
                if (value.flag("isSystem")) {
                    val row = sql.rows("contact_fields", "fieldKey = ?", arrayOf(requireNotNull(value.str("fieldKey")))).firstOrNull()
                    if (row != null) {
                        val key = "system/${row.num("id")}"
                        val old = ledger.get("id/fieldDefinition/$key")
                        if (old == null || old == root.id || ledger.shadow("fieldDefinition/$old") == null) ledger.bind(root.kind, key, root.id)
                    }
                }
            }
        }
    }

    suspend fun apply(roots: List<SyncRoot>) {
        val live = roots.filter { !it.deleted }.sortedBy { ORDER.indexOf(it.kind) }
        for (root in live) when (root.kind) {
            "fieldDefinition" -> definition(root)
            "person", "selfCard" -> contact(root)
            "collection" -> collection(root)
            "tag" -> tag(root)
        }
        for (root in roots.filter { it.deleted }.sortedByDescending { ORDER.indexOf(it.kind) }) remove(root)
        if (roots.isNotEmpty()) Log.d(TAG, "已应用远端业务变更 roots=${roots.size}")
    }

    private suspend fun localId(root: SyncRoot): Long {
        ledger.local(root.kind, root.id)?.let { return it.substringAfterLast('/').toLong() }
        if (root.kind == "selfCard") {
            ledger.bind(root.kind, MY_CONTACT_ID.toString(), root.id)
            return MY_CONTACT_ID
        }
        val namespace = if (root.kind == "fieldDefinition") {
            if (requireNotNull(root.value).flag("isSystem")) "system/" else "custom/"
        } else ""
        val table = table(root.kind, namespace)
        val allocationKey = "${root.kind}/$namespace"
        val local = allocatedIds[allocationKey]?.let { Math.addExact(it, 1L) } ?: run {
            val reserved = ledger.entries("id/${root.kind}/$namespace").keys
                .mapNotNull { it.substringAfterLast('/').toLongOrNull() }.maxOrNull() ?: 0L
            Log.d(TAG, "计算事务内本机标识起点 kind=${root.kind}")
            maxOf(sql.nextId(table), Math.addExact(reserved, 1L))
        }
        ledger.bind(root.kind, "$namespace$local", root.id)
        allocatedIds[allocationKey] = local
        return local
    }

    private suspend fun definition(root: SyncRoot) {
        val value = requireNotNull(root.value)
        val id = localId(root)
        if (value.flag("isSystem")) {
            sql.upsert("contact_fields", id, SyncJson.obj("fieldName" to value.str("fieldName"),
                "fieldKey" to value.str("fieldKey"), "icon" to value.str("icon"), "sortOrder" to value.num("sortOrder"),
                "isSystem" to true, "isEnabled" to value.flag("isEnabled"), "createTime" to value.num("createTime")))
        } else {
            sql.upsert("custom_fields", id, SyncJson.obj("fieldName" to value.str("fieldName"), "fieldType" to value.str("fieldType"),
                "options" to value.str("options").orEmpty(), "sortOrder" to value.num("sortOrder"),
                "isEnabled" to value.flag("isEnabled"), "createTime" to value.num("createTime")))
        }
    }

    private suspend fun contact(root: SyncRoot) {
        val value = requireNotNull(root.value)
        val profile = value["profile"].asJsonObject
        val details = value.details()
        val local = localId(root)
        val previous = sql.rows("contacts", "id = ?", arrayOf(local)).firstOrNull()
        val name = value.str("alias") ?: profile.str("circleName").orEmpty()
        val avatar = referenceAsset(ledger, profile.str("avatarAssetId"), "${root.kind}/${root.id}/avatar")
        val values = SyncJson.obj("name" to name, "avatarUrl" to (if (details != null) details.str("avatarUrl") else previous?.str("avatarUrl")),
            "avatarPath" to avatar, "note" to if (root.kind == "selfCard") details?.str("notes") else value.str("notes"),
            "bio" to (details?.str("bio") ?: if (details == null) previous?.str("bio") else null),
            "pinyinInitial" to PinyinUtils.getContactPinyinInitial(name), "platforms" to null,
            "createTime" to (details?.num("createTime") ?: previous?.num("createTime") ?: 0L),
            "updateTime" to (details?.num("updateTime") ?: previous?.num("updateTime") ?: 0L))
        sql.upsert("contacts", local, values)
        platforms(local, profile, details)
        if (details != null) fields(local, details)
        if (root.kind == "selfCard") userProfile(local, values, details)
    }

    private suspend fun platforms(contactId: Long, profile: JsonObject, details: JsonObject?) {
        val raw = details?.items("platformDetails")
        val entries = if (raw != null) raw else profile.items("accounts").map { account ->
            SyncJson.obj("id" to account.str("id"), "platformKey" to account.str("platform"), "value" to account.str("handle"),
                "jumpLink" to account.str("url").orEmpty(), "displayName" to account.str("label"),
                "originalLink" to null, "avatarUrl" to null)
        }
        val keys = entries.map { requireNotNull(it.str("platformKey")) }
        require(keys.distinct().size == keys.size) { "远端平台标识重复" }
        sql.execSQL("DELETE FROM contact_platforms WHERE contactId = ?", arrayOf(contactId))
        for (entry in entries) {
            val key = requireNotNull(entry.str("platformKey"))
            ledger.bind("platform", "$contactId/$key", requireNotNull(entry.str("id")))
            sql.upsert("contact_platforms", sql.nextId("contact_platforms"), SyncJson.obj("contactId" to contactId,
                "platformKey" to key, "value" to entry.str("value"), "displayName" to entry.str("displayName"),
                "jumpLink" to entry.str("jumpLink").orEmpty(), "originalLink" to entry.str("originalLink"), "avatarUrl" to entry.str("avatarUrl")))
        }
    }

    private suspend fun fields(contactId: Long, details: JsonObject) {
        sql.execSQL("DELETE FROM contact_field_values WHERE contactId = ?", arrayOf(contactId))
        for (field in details.items("fields")) {
            val definition = requireNotNull(ledger.local("fieldDefinition", requireNotNull(field.str("definitionId")))) {
                "远端联系方式缺少字段定义"
            }
            val definitionId = definition.substringAfterLast('/').toLong()
            val local = childId("fieldValue", requireNotNull(field.str("id")), "contact_field_values")
            sql.upsert("contact_field_values", local, SyncJson.obj("contactId" to contactId,
                "fieldId" to if (definition.startsWith("system/")) definitionId else null,
                "customFieldId" to if (definition.startsWith("custom/")) definitionId else null,
                "value" to field.str("value"), "createTime" to field.num("createTime"), "updateTime" to field.num("updateTime")))
        }
    }

    private fun userProfile(contactId: Long, contact: JsonObject, details: JsonObject?) {
        require(contactId == MY_CONTACT_ID)
        val old = sql.rows("user_profile", "id = 1").firstOrNull()
        val platforms = JsonObject()
        for (entry in sql.rows("contact_platforms", "contactId = ?", arrayOf(contactId))) {
            platforms.add(requireNotNull(entry.str("platformKey")), SyncJson.obj("value" to entry.str("value"),
                "displayName" to entry.str("displayName"), "jumpLink" to entry.str("jumpLink"),
                "originalLink" to entry.str("originalLink"), "avatarUrl" to entry.str("avatarUrl")))
        }
        sql.upsert("user_profile", 1L, SyncJson.obj("name" to contact.str("name"), "bio" to contact.str("bio"),
            "avatarPath" to contact.str("avatarPath"), "platforms" to SyncJson.encode(platforms),
            "cardImagePath" to if (details != null) referenceAsset(ledger, details.str("cardImageAssetId"),
                "selfCard/${ledger.get("id/selfCard/-1")}/card") else old?.str("cardImagePath"),
            "defaultPlatform" to if (details != null) details.str("defaultPlatform") else old?.str("defaultPlatform"),
            "updateTime" to (details?.num("profileUpdateTime") ?: old?.num("updateTime") ?: 0L)))
    }

    private suspend fun collection(root: SyncRoot) {
        val value = requireNotNull(root.value)
        val details = value.details()
        val local = localId(root)
        val old = sql.rows("card_collections", "id = ?", arrayOf(local)).firstOrNull()
        sql.upsert("card_collections", local, SyncJson.obj("name" to value.str("name"),
            "description" to if (details != null) details.str("description") else old?.str("description"),
            "backgroundImagePath" to if (details != null) referenceAsset(ledger, details.str("backgroundAssetId"),
                "collection/${root.id}/background") else old?.str("backgroundImagePath"),
            "dominantColor" to (details?.get("dominantColor") ?: old?.get("dominantColor")),
            "createTime" to (details?.num("createTime") ?: old?.num("createTime") ?: 0L)))
        val records = details?.items("records") ?: legacyRecords(root)
        sql.execSQL("DELETE FROM scan_results WHERE collectionId = ?", arrayOf(local))
        for (record in records) {
            val id = childId("scanRecord", requireNotNull(record.str("id")), "scan_results")
            sql.upsert("scan_results", id, SyncJson.obj("contactId" to personId(requireNotNull(record.str("personId"))),
                "collectionId" to local, "scannedTime" to record.num("scannedTime"), "sourceType" to record.str("sourceType"),
                "rawData" to record.str("rawData"), "ocrText" to record.str("ocrText"),
                "qrCodeContent" to record.str("qrCodeContent"), "confidence" to (record["confidence"]?.takeUnless { it.isJsonNull } ?: SyncJson.element(0))))
        }
    }

    private suspend fun legacyRecords(root: SyncRoot): List<JsonObject> = buildList {
        for (person in SyncJson.array(requireNotNull(root.value), "memberIds")) {
            val id = ledger.remote("legacyScan", "${root.id}/${person.asString}")
            add(SyncJson.obj("id" to id, "personId" to person.asString, "scannedTime" to 0,
                "sourceType" to "sync", "rawData" to null, "ocrText" to null, "qrCodeContent" to null, "confidence" to 1))
        }
    }

    private suspend fun tag(root: SyncRoot) {
        val value = requireNotNull(root.value)
        val details = value.details()
        val local = localId(root)
        val old = sql.rows("tags", "id = ?", arrayOf(local)).firstOrNull()
        val name = requireNotNull(value.str("name"))
        sql.upsert("tags", local, SyncJson.obj("name" to name, "pinyinInitial" to PinyinUtils.getContactPinyinInitial(name),
            "color" to (details?.num("color") ?: old?.num("color") ?: DEFAULT_TAG_COLOR),
            "source" to (details?.str("source") ?: old?.str("source") ?: "import"),
            "showDot" to (details?.flag("showDot") ?: old?.flag("showDot") ?: true),
            "createTime" to (details?.num("createTime") ?: old?.num("createTime") ?: 0L)))
        val members = details?.items("members") ?: SyncJson.array(value, "memberIds").map {
            SyncJson.obj("personId" to it.asString, "source" to "import", "confidence" to 1, "createTime" to 0)
        }
        sql.execSQL("DELETE FROM contact_tag WHERE tagId = ?", arrayOf(local))
        for (member in members) sql.execSQL(
            "INSERT INTO contact_tag(contactId,tagId,source,confidence,createTime) VALUES (?,?,?,?,?)",
            arrayOf<Any?>(personId(requireNotNull(member.str("personId"))), local, member.str("source"),
                member["confidence"]?.takeUnless { it.isJsonNull }?.asDouble ?: 1.0, member.num("createTime")))
    }

    private suspend fun childId(kind: String, remote: String, table: String): Long {
        ledger.local(kind, remote)?.let { return it.toLong() }
        val id = sql.nextId(table)
        ledger.bind(kind, id.toString(), remote)
        return id
    }

    private fun personId(remote: String): Long = (ledger.local("person", remote) ?: ledger.local("selfCard", remote))
        ?.toLong() ?: error("远端成员缺少对应联系人")

    private fun remove(root: SyncRoot) {
        if (root.kind !in ORDER) return
        val key = ledger.local(root.kind, root.id) ?: return
        require(root.kind != "selfCard") { "不能删除我的名片" }
        val local = key.substringAfterLast('/').toLong()
        sql.execSQL("DELETE FROM ${table(root.kind, key)} WHERE id = ?", arrayOf(local))
    }

    private fun table(kind: String, key: String): String = when (kind) {
        "fieldDefinition" -> if (key.startsWith("system/")) "contact_fields" else "custom_fields"
        "person", "selfCard" -> "contacts"
        "collection" -> "card_collections"
        "tag" -> "tags"
        else -> error("不支持的业务资料类型")
    }

    companion object {
        private const val TAG = "WorkspaceApplierTester"
        private const val DEFAULT_TAG_COLOR = 0xFF1976D2L
        private val ORDER = listOf("fieldDefinition", "person", "selfCard", "collection", "tag")
    }
}
