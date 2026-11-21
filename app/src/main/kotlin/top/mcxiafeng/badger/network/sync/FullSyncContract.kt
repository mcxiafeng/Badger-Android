package top.mcxiafeng.badger.network.sync

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal

/** Strict schema-2 inbound boundary. Unknown/missing fields stop application rather than erasing data. */
internal object FullSyncContract {
    private const val MAX_SAFE_INTEGER = 9007199254740991L
    private const val MAX_ARGB = 4294967295L

    fun snapshot(roots: List<SyncRoot>) {
        roots.forEach(::validate)
        val live = roots.filter { !it.deleted }
        val people = live.filter { it.kind == "person" || it.kind == "selfCard" }.map { it.id }.toSet()
        val definitions = live.filter { it.kind == "fieldDefinition" }.map { it.id }.toSet()
        val assets = live.filter { it.kind == "asset" }.map { it.id }.toSet()
        val fieldIds = mutableSetOf<String>()
        val platformIds = mutableSetOf<String>()
        val recordIds = mutableSetOf<String>()
        for (root in live) {
            val value = requireNotNull(root.value)
            val profile = value["profile"]?.takeIf { it.isJsonObject }?.asJsonObject
            profile?.get("avatarAssetId")?.takeUnless { it.isJsonNull }?.let { require(it.asString in assets) }
            val data = value["clientData"]?.takeUnless { it.isJsonNull }?.asJsonObject
            if (data == null) {
                profile?.getAsJsonArray("accounts")?.forEach { require(platformIds.add(it.asJsonObject["id"].asString)) }
                continue
            }
            for (key in listOf("cardImageAssetId", "backgroundAssetId"))
                data[key]?.takeUnless { it.isJsonNull }?.let { require(it.asString in assets) }
            data.getAsJsonArray("fields")?.forEach {
                val item = it.asJsonObject
                require(fieldIds.add(item["id"].asString) && item["definitionId"].asString in definitions)
            }
            data.getAsJsonArray("platformDetails")?.forEach { require(platformIds.add(it.asJsonObject["id"].asString)) }
            data.getAsJsonArray("records")?.forEach {
                val item = it.asJsonObject
                require(recordIds.add(item["id"].asString) && item["personId"].asString in people)
            }
            data.getAsJsonArray("members")?.forEach { require(it.asJsonObject["personId"].asString in people) }
        }
    }

    fun validate(root: SyncRoot) {
        if (root.deleted || root.kind in setOf("asset", "publishedCard")) return
        val value = requireNotNull(root.value)
        when (root.kind) {
            "person" -> {
                shape(value, setOf("alias", "notes", "source", "profile"), setOf("clientData"))
                text(value, "alias", true); text(value, "notes", true)
                value["source"].takeUnless { it.isJsonNull }?.asJsonObject?.let {
                    shape(it, setOf("kind", "capturedAt", "text")); text(it, "kind"); text(it, "capturedAt"); text(it, "text", true)
                }
                profile(value["profile"].asJsonObject)
            }
            "selfCard" -> { shape(value, setOf("profile"), setOf("clientData")); profile(value["profile"].asJsonObject) }
            "collection", "tag" -> {
                shape(value, setOf("name", "memberIds"), setOf("clientData")); text(value, "name")
                SyncJson.array(value, "memberIds").forEach { uuid(it) }
            }
            "fieldDefinition" -> {
                shape(value, setOf("fieldName", "fieldKey", "icon", "fieldType", "options", "sortOrder", "isSystem", "isEnabled", "createTime"))
                text(value, "fieldName"); listOf("fieldKey", "icon", "fieldType", "options").forEach { text(value, it, true) }
                bool(value, "isSystem"); bool(value, "isEnabled"); time(value, "createTime")
                require(SyncJson.long(value, "sortOrder") in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
            }
        }
        value["clientData"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let { details(root.kind, it) }
    }

    private fun profile(value: JsonObject) {
        shape(value, setOf("circleName", "accounts", "interestTags", "avatarAssetId"))
        text(value, "circleName", true); nullableId(value, "avatarAssetId")
        SyncJson.array(value, "interestTags").forEach { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
        val ids = mutableSetOf<String>()
        for (account in SyncJson.array(value, "accounts")) {
            val item = account.asJsonObject
            shape(item, setOf("id", "platform", "handle", "label", "url"))
            require(ids.add(uuid(item["id"])))
            text(item, "platform"); text(item, "handle"); text(item, "label", true); text(item, "url", true)
        }
    }

    private fun details(kind: String, value: JsonObject) {
        when (kind) {
            "person", "selfCard" -> {
                val fields = setOf("bio", "avatarUrl", "createTime", "updateTime", "fields", "platformDetails") +
                    if (kind == "selfCard") setOf("cardImageAssetId", "defaultPlatform", "notes", "profileUpdateTime") else emptySet()
                shape(value, fields)
                text(value, "bio", true); text(value, "avatarUrl", true); time(value, "createTime"); time(value, "updateTime")
                for (field in SyncJson.array(value, "fields")) field.asJsonObject.let {
                    shape(it, setOf("id", "definitionId", "value", "createTime", "updateTime"))
                    uuid(it["id"]); uuid(it["definitionId"]); text(it, "value"); time(it, "createTime"); time(it, "updateTime")
                }
                for (platform in SyncJson.array(value, "platformDetails")) platform.asJsonObject.let {
                    shape(it, setOf("id", "platformKey", "value", "jumpLink", "displayName", "originalLink", "avatarUrl"))
                    uuid(it["id"]); text(it, "platformKey"); text(it, "jumpLink")
                    listOf("value", "displayName", "originalLink", "avatarUrl").forEach { key -> text(it, key, true) }
                }
                if (kind == "selfCard") {
                    nullableId(value, "cardImageAssetId"); text(value, "defaultPlatform", true); text(value, "notes", true); time(value, "profileUpdateTime")
                }
            }
            "collection" -> {
                shape(value, setOf("description", "backgroundAssetId", "dominantColor", "createTime", "records"))
                text(value, "description", true); nullableId(value, "backgroundAssetId"); time(value, "createTime")
                if (!value["dominantColor"].isJsonNull) color(value, "dominantColor")
                for (record in SyncJson.array(value, "records")) record.asJsonObject.let {
                    shape(it, setOf("id", "personId", "scannedTime", "sourceType", "rawData", "ocrText", "qrCodeContent", "confidence"))
                    uuid(it["id"]); uuid(it["personId"]); time(it, "scannedTime"); text(it, "sourceType")
                    listOf("rawData", "ocrText", "qrCodeContent").forEach { key -> text(it, key, true) }; confidence(it)
                }
            }
            "tag" -> {
                shape(value, setOf("color", "source", "showDot", "createTime", "members"))
                color(value, "color"); text(value, "source"); bool(value, "showDot"); time(value, "createTime")
                for (member in SyncJson.array(value, "members")) member.asJsonObject.let {
                    shape(it, setOf("personId", "source", "confidence", "createTime"))
                    uuid(it["personId"]); text(it, "source", true); confidence(it); time(it, "createTime")
                }
            }
        }
    }

    private fun shape(value: JsonObject, required: Set<String>, optional: Set<String> = emptySet()) {
        require(value.keySet().containsAll(required) && value.keySet().all { it in required || it in optional }) { "同步资料格式不兼容，已保留原资料" }
    }
    private fun text(value: JsonObject, key: String, nullable: Boolean = false) {
        require(value.has(key))
        val item = value[key]
        require((nullable && item.isJsonNull) || item.isJsonPrimitive && item.asJsonPrimitive.isString)
    }
    private fun uuid(value: JsonElement): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return SyncJson.id(value.asString)
    }
    private fun nullableId(value: JsonObject, key: String) { require(value.has(key)); if (!value[key].isJsonNull) uuid(value[key]) }
    private fun time(value: JsonObject, key: String) { require(SyncJson.long(value, key) in 0..MAX_SAFE_INTEGER) }
    private fun color(value: JsonObject, key: String) { require(SyncJson.long(value, key) in 0..MAX_ARGB) }
    private fun bool(value: JsonObject, key: String) { require(value[key]?.isJsonPrimitive == true && value[key].asJsonPrimitive.isBoolean) }
    private fun confidence(value: JsonObject) {
        val item = requireNotNull(value["confidence"])
        require(item.isJsonNull || item.isJsonPrimitive && item.asJsonPrimitive.isNumber && item.asBigDecimal in BigDecimal.ZERO..BigDecimal.ONE)
    }
}
