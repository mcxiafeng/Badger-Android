package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.icu.lang.UCharacter
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import top.mcxiafeng.badger.utils.atomicWrite
import java.io.File
import java.text.Normalizer
import java.util.UUID

internal fun tagNameKey(value: String): String = Normalizer.normalize(
    UCharacter.foldCase(Normalizer.normalize(value, Normalizer.Form.NFKC), UCharacter.FOLD_CASE_DEFAULT), Normalizer.Form.NFC)

/** Remaps identities and copies physical files; remote rows never point at the local user's originals. */
internal class ImportRemapper(
    private val ledger: SyncLedger,
    private val sourceContext: Context,
    private val targetContext: Context,
    private val mapping: Map<String, String>,
) {
    private val images = mutableMapOf<String, String>()
    private val children = mutableMapOf<String, String>()
    private val tagNames by lazy { ledger.sql().rows("tags").mapNotNull { it.str("name") }.map(::tagNameKey).toMutableSet() }

    suspend fun remap(source: SyncRoot, id: String, target: SyncRoot?): SyncRoot {
        val value = requireNotNull(source.value).deepCopy()
        alignChildren(value, target?.value)
        val translated = translate(value, "").asJsonObject
        if (source.kind == "tag" && target == null) translated.addProperty("name", uniqueTagName(requireNotNull(translated.str("name"))))
        if (target != null) {
            if (translated.has("memberIds")) translated.add("memberIds", target.value?.get("memberIds")?.deepCopy())
            val profile = translated["profile"]?.takeIf { it.isJsonObject }?.asJsonObject
            val existingProfile = target.value?.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
            if (profile != null && existingProfile != null) {
                profile.add("accounts", existingProfile["accounts"].deepCopy())
                if (source.kind == "person" && target.value?.str("alias") != null)
                    profile.add("circleName", existingProfile["circleName"].deepCopy())
            }
        }
        return SyncRoot(source.kind, id, target?.version ?: "0", value = translated)
    }

    private fun alignChildren(source: JsonObject, target: JsonObject?) {
        val details = source.details() ?: return
        val targetDetails = target?.details()
        if (details.has("platformDetails")) for (platform in details.items("platformDetails")) {
            val old = requireNotNull(platform.str("id"))
            val matched = targetDetails?.items("platformDetails")?.firstOrNull { it.str("platformKey") == platform.str("platformKey") }
            children[old] = matched?.str("id") ?: UUID.randomUUID().toString()
        }
        if (details.has("fields")) for (field in details.items("fields")) {
            val old = requireNotNull(field.str("id"))
            val definition = mapping[field.str("definitionId")]
            val existing = targetDetails?.items("fields")?.firstOrNull {
                it.str("definitionId") == definition && it.str("value") == field.str("value")
            }
            if (existing != null) {
                field.addProperty("createTime", existing.num("createTime"))
                field.addProperty("updateTime", existing.num("updateTime"))
            }
            children[old] = existing?.str("id") ?: UUID.randomUUID().toString()
        }
        if (details.has("records")) for (record in details.items("records"))
            children[requireNotNull(record.str("id"))] = UUID.randomUUID().toString()
    }

    private suspend fun translate(element: JsonElement, key: String): JsonElement {
        if (element.isJsonObject) return JsonObject().apply {
            for ((name, value) in element.asJsonObject.entrySet()) {
                val translated = when {
                    name in ASSET_FIELDS && !value.isJsonNull -> SyncJson.element(copyImage(value.asString))
                    name in REFERENCE_FIELDS && !value.isJsonNull -> SyncJson.element(requireNotNull(mapping[value.asString]) {
                        "请同时导入所选联系人的自定义字段定义"
                    })
                    name == "id" && !value.isJsonNull -> SyncJson.element(children[value.asString] ?: value.asString)
                    else -> translate(value, name)
                }
                add(name, translated)
            }
        }
        if (element.isJsonArray) return JsonArray().apply {
            for (item in element.asJsonArray) {
                if (key == "memberIds") {
                    mapping[item.asString]?.let { add(it) }
                } else if (key in setOf("records", "members") && item.isJsonObject && !mapping.containsKey(item.asJsonObject.str("personId"))) {
                    Log.d(TAG, "未选择导入的联系人不建立成员关系")
                } else add(translate(item, key))
            }
        }
        return element.deepCopy()
    }

    private suspend fun copyImage(sourceId: String): String {
        images[sourceId]?.let { return it }
        SyncJson.id(sourceId)
        val source = File(sourceContext.filesDir, "sync-assets/$sourceId.source")
        require(source.isFile) { "导入图片副本不完整，请重新建立预览" }
        val target = File(targetContext.filesDir, "import_${UUID.randomUUID()}.source")
        atomicWrite(target) { output -> source.inputStream().use { it.copyTo(output) } }
        val id = requireNotNull(WorkspaceAssets(targetContext).reference(target.path, ledger))
        ledger.put("assetPath/$id", target.canonicalPath)
        images[sourceId] = id
        return id
    }

    private fun uniqueTagName(name: String): String {
        if (tagNames.add(tagNameKey(name))) return name
        for (index in 1..MAX_RENAMES) {
            val suffix = "（本地导入$index）"
            val prefix = name.take(MAX_NAME - suffix.length).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
            val candidate = prefix + suffix
            if (tagNames.add(tagNameKey(candidate))) return candidate
        }
        error("同名标签过多，请先整理后重试")
    }

    companion object {
        private const val TAG = "ImportRemapperTester"
        private const val MAX_NAME = 100
        private const val MAX_RENAMES = 10_000
        private val ASSET_FIELDS = setOf("avatarAssetId", "cardImageAssetId", "backgroundAssetId")
        private val REFERENCE_FIELDS = setOf("personId", "definitionId")

        /** Absence in a standalone local dataset is not an instruction to clear existing cloud data. */
        fun supplement(source: JsonObject, target: JsonObject): JsonObject = source.deepCopy().apply {
            for ((key, existing) in target.entrySet()) {
                val incoming = get(key)
                when {
                    incoming == null || incoming.isJsonNull || key == "createTime" -> add(key, existing.deepCopy())
                    incoming.isJsonArray && incoming.asJsonArray.size() == 0 -> add(key, existing.deepCopy())
                    incoming.isJsonObject && existing.isJsonObject -> add(key, supplement(incoming.asJsonObject, existing.asJsonObject))
                }
            }
        }
    }
}
