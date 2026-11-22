package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Immutable
data class ConflictDifference(val field: String, val local: String, val remote: String,
    val localImage: String? = null, val remoteImage: String? = null)
@Immutable
data class ConflictItem(val key: String, val title: String, val differences: List<ConflictDifference>, val canKeepBoth: Boolean,
    val canAdoptLocal: Boolean = true, val notice: String? = null, val reviewToken: String = "")

internal class ConflictChangedException : IllegalStateException("资料差异已更新，请重新核对")

@Singleton
class WorkspaceConflicts @Inject constructor(private val coordinator: SyncCoordinator) {
    suspend fun list(scope: AccountScope): List<ConflictItem> = withContext(Dispatchers.IO) {
        val db = coordinator.database(scope)
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            ledger.entries("conflict/").values.map { encoded ->
                val conflict = StoredConflict.read(encoded)
                val differences = conflict.paths.map { path ->
                    val local = at(conflict.local?.value, path)
                    val remote = at(conflict.remote.value, path)
                    ConflictDifference(label(path), if (path.contains("AssetId")) imageLabel(local) else render(local, ledger),
                        if (conflict.remote.version == "0" && remote == null) "尚未创建"
                        else if (path.contains("AssetId")) imageLabel(remote) else render(remote, ledger),
                        image(local, path, ledger), image(remote, path, ledger))
                }.distinct()
                ConflictItem(conflict.key, title(conflict.local?.value ?: conflict.remote.value, conflict.remote.kind),
                    differences, conflict.remote.kind == "person" && conflict.local?.value != null && !conflict.remote.deleted,
                    conflict.canAdoptLocal, conflict.notice, reviewToken(encoded))
            }
        }
    }

    suspend fun resolve(scope: AccountScope, item: ConflictItem, preferLocal: Boolean, keepBoth: Boolean = false) =
        withContext(Dispatchers.IO) {
            val key = item.key
            coordinator.locked(scope) {
                val db = coordinator.database(scope)
                workspaceTransaction(db) {
                    val ledger = SyncLedger.load(db)
                    val encoded = ledger.get("conflict/$key")
                    if (encoded == null || reviewToken(encoded) != item.reviewToken) {
                        Log.d(TAG, "冲突内容已变化，拒绝提交旧的裁决")
                        throw ConflictChangedException()
                    }
                    val conflict = StoredConflict.read(encoded)
                    require(!preferLocal || conflict.canAdoptLocal) { "请先处理关联资料，当前选择尚不能安全应用" }
                    val current = WorkspaceCodec(ledger, coordinator.scopedContext(scope)).capture()[key]
                    require(current?.value == conflict.local?.value) { "本机资料刚有更新，请同步后重新核对" }
                    if (keepBoth) copyPerson(conflict, ledger) else adopt(conflict, preferLocal, ledger, coordinator.scopedContext(scope))
                    ledger.remove("conflict/$key")
                    ledger.remove("constraint/$key")
                    Log.d(TAG, "冲突选择已保存 kind=${conflict.remote.kind}, keepBoth=$keepBoth, preferLocal=$preferLocal")
                }
            }
            coordinator.synchronize(scope)
        }

    private fun reviewToken(encoded: String): String = SyncJson.hash(encoded.toByteArray(Charsets.UTF_8))

    private suspend fun adopt(conflict: StoredConflict, preferLocal: Boolean, ledger: SyncLedger, context: Context) {
        val merged = if (conflict.forced) {
            if (preferLocal) conflict.local?.value else conflict.remote.value
        } else SyncMerge.merge(conflict.base?.value, conflict.local?.value, conflict.remote.value,
            conflict.paths.associateWith { preferLocal }).value?.asJsonObject
        val applier = WorkspaceApplier(ledger)
        if (conflict.remote.version == "0") {
            if (!preferLocal) applier.apply(listOf(conflict.remote))
            return
        }
        ledger.shadow(conflict.remote)
        if (merged != null && conflict.remote.deleted) {
            val local = requireNotNull(ledger.local(conflict.remote.kind, conflict.remote.id))
            val id = ledger.move(conflict.remote.kind, local)
            applier.apply(listOf(SyncRoot(conflict.remote.kind, id, value = merged)))
        } else applier.apply(listOf(conflict.remote.copy(value = merged, deleted = merged == null)))
        if (conflict.remote.kind == "fieldDefinition" && !preferLocal) releaseHeld(conflict.remote.id, ledger, context)
    }

    private suspend fun releaseHeld(definitionId: String, ledger: SyncLedger, context: Context) {
        val waiting = ledger.entries("held/$definitionId/")
        val held = waiting.filterValues { encoded -> definitionsAvailable(SyncRoot.read(SyncJson.parse(encoded)), ledger) }
        if (held.isEmpty()) {
            if (waiting.isNotEmpty()) Log.d(TAG, "联系人仍有其他未恢复字段，继续保留挂起资料")
            return
        }
        val local = WorkspaceCodec(ledger, context).capture()
        val updates = mutableListOf<SyncRoot>()
        for ((_, encoded) in held) {
            val remote = SyncRoot.read(SyncJson.parse(encoded))
            val prior = ledger.get("conflict/${remote.key}")?.let(StoredConflict::read)
            val base = if (prior != null) prior.base else ledger.shadow(remote.key)
            val mine = local[remote.key]?.value?.deepCopy()
            if (mine != null) restoreFieldValues(mine, requireNotNull(remote.value), definitionId)
            val merged = SyncMerge.merge(base?.value, mine, remote.value)
            val working = remote.copy(value = merged.value?.asJsonObject, deleted = merged.value == null)
            if (merged.conflicts.isNotEmpty()) ledger.put("conflict/${remote.key}", SyncJson.encode(
                StoredConflict(remote.key, base, working, remote, merged.conflicts).json()))
            else { ledger.shadow(remote); ledger.remove("conflict/${remote.key}") }
            updates.add(working)
        }
        WorkspaceApplier(ledger).apply(updates)
        val actual = WorkspaceCodec(ledger, context).capture()
        for (root in updates) {
            val conflict = ledger.get("conflict/${root.key}")?.let(StoredConflict::read) ?: continue
            ledger.put("conflict/${root.key}", SyncJson.encode(conflict.copy(local = actual[root.key]).json()))
        }
        val released = updates.map { it.key }.toSet()
        for (key in ledger.entries("held/").keys) {
            if (key.removePrefix("held/").substringAfter('/') in released) ledger.remove(key)
        }
        Log.d(TAG, "恢复字段后已重新核对受影响联系人 count=${updates.size}")
    }

    private fun definitionsAvailable(root: SyncRoot, ledger: SyncLedger): Boolean {
        val fields = root.value?.details()?.items("fields").orEmpty()
        return fields.all { field ->
            val local = ledger.local("fieldDefinition", requireNotNull(field.str("definitionId"))) ?: return@all false
            val table = when {
                local.startsWith("system/") -> "contact_fields"
                local.startsWith("custom/") -> "custom_fields"
                else -> error("字段映射格式不兼容，请保留本机资料")
            }
            ledger.sql().rows(table, "id = ?", arrayOf(local.substringAfterLast('/').toLong())).isNotEmpty()
        }
    }

    private fun restoreFieldValues(local: JsonObject, remote: JsonObject, definitionId: String) {
        val details = local.details() ?: return
        val incoming = remote.details() ?: return
        val fields = JsonArray()
        details.items("fields").filter { it.str("definitionId") != definitionId }.forEach(fields::add)
        incoming.items("fields").filter { it.str("definitionId") == definitionId }.forEach(fields::add)
        details.add("fields", fields)
    }

    private suspend fun copyPerson(conflict: StoredConflict, ledger: SyncLedger) {
        require(conflict.remote.kind == "person" && !conflict.remote.deleted)
        val sourceId = requireNotNull(ledger.local("person", conflict.remote.id)).toLong()
        val sql = ledger.sql()
        val scans = sql.rows("scan_results", "contactId = ?", arrayOf(sourceId))
        val tags = sql.rows("contact_tag", "contactId = ?", arrayOf(sourceId))
        val copyId = ledger.move("person", sourceId.toString())
        // Preserve opaque core fields (public circle name, interests and provenance) absent from Room columns.
        ledger.put("template/person/$copyId", SyncJson.encode(requireNotNull(conflict.local?.value)))
        copyAvatarReference(conflict, ledger, sourceId, copyId)
        for (row in sql.rows("contact_platforms", "contactId = ?", arrayOf(sourceId)))
            ledger.move("platform", "$sourceId/${row.str("platformKey")}")
        for (row in sql.rows("contact_field_values", "contactId = ?", arrayOf(sourceId)))
            ledger.move("fieldValue", row.num("id").toString())
        WorkspaceApplier(ledger).apply(listOf(conflict.remote))
        val targetId = requireNotNull(ledger.local("person", conflict.remote.id)).toLong()
        for (scan in scans) {
            val copy = scan.deepCopy().apply { remove("id"); addProperty("contactId", targetId) }
            sql.upsert("scan_results", sql.nextId("scan_results"), copy)
        }
        for (tag in tags) sql.execSQL("INSERT INTO contact_tag(contactId,tagId,source,confidence,createTime) VALUES (?,?,?,?,?)",
            arrayOf<Any?>(targetId, tag.num("tagId"), tag.str("source"), tag["confidence"].asDouble, tag.num("createTime")))
        ledger.shadow(conflict.remote)
    }

    private suspend fun copyAvatarReference(conflict: StoredConflict, ledger: SyncLedger, localId: Long, copyId: String) {
        val assetId = conflict.local?.value?.get("profile")?.asJsonObject?.str("avatarAssetId") ?: return
        if (ledger.get("assetPath/$assetId") == null) {
            val root = File(requireNotNull(ledger.get("space/filesRoot")))
            val staged = File(root, "sync-assets/${SyncJson.id(assetId)}.source")
            require(staged.isFile) { "本机头像副本尚未准备完整，请重新同步后选择" }
            ledger.put("assetPath/$assetId", staged.canonicalPath)
        }
        val path = referenceAsset(ledger, assetId, "person/$copyId/avatar")
        ledger.sql().execSQL("UPDATE contacts SET avatarPath = ? WHERE id = ?", arrayOf<Any?>(path, localId))
        Log.d(TAG, "保留两份联系人时已分离本机头像文件")
    }

    private fun imageLabel(value: JsonElement?): String = if (value == null || value.isJsonNull) "未设置图片" else "已设置图片"

    private fun image(value: JsonElement?, path: String, ledger: SyncLedger): String? {
        if (!path.contains("AssetId") || value == null || value.isJsonNull || !value.isJsonPrimitive) return null
        return ledger.get("assetPath/${value.asString}")
    }

    private fun render(value: JsonElement?, ledger: SyncLedger): String = when {
        value == null -> "已删除"
        value.isJsonNull -> "空"
        value.isJsonArray -> value.asJsonArray.map { render(it, ledger) }.joinToString("\n").ifEmpty { "无" }
        value.isJsonObject -> {
            val obj = value.asJsonObject
            val person = obj.str("personId")?.let { personName(it, ledger) }
            val selected = DISPLAY_KEYS.filter { obj.has(it) && !obj[it].isJsonNull }.map { render(obj[it], ledger) }
            val nested = listOf("profile", "clientData", "fields", "platformDetails", "records", "members")
                .filter { obj.has(it) && !obj[it].isJsonNull }.map { render(obj[it], ledger) }
            (listOfNotNull(person) + selected + nested).distinct().joinToString(" · ").ifEmpty { "关联资料" }
        }
        value.asJsonPrimitive.isBoolean -> if (value.asBoolean) "开启" else "关闭"
        value.asJsonPrimitive.isString -> {
            val text = value.asString
            if (UUID_PATTERN.matches(text)) personName(text, ledger) else text.ifEmpty { "空" }
        }
        else -> value.asString
    }

    private fun personName(id: String, ledger: SyncLedger): String {
        val local = ledger.local("person", id) ?: ledger.local("selfCard", id)
        if (local != null) return ledger.sql().rows("contacts", "id = ?", arrayOf(local.toLong())).firstOrNull()?.str("name") ?: "联系人"
        val shadow = ledger.shadow("person/$id") ?: ledger.shadow("selfCard/$id")
        return title(shadow?.value, shadow?.kind ?: "person")
    }

    private fun at(root: JsonObject?, path: String): JsonElement? {
        var value: JsonElement? = root
        if (path in setOf("资料", "关联资料")) return value
        for (part in path.split('.')) {
            val match = SEGMENT.matchEntire(part) ?: return value
            value = value?.takeIf { it.isJsonObject }?.asJsonObject?.get(match.groupValues[1]) ?: return null
            val id = match.groupValues[2]
            if (id.isNotEmpty()) value = value.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull {
                it.isJsonObject && (it.asJsonObject.str("id") == id || it.asJsonObject.str("personId") == id)
            }
        }
        return value
    }

    companion object {
        private const val TAG = "WorkspaceConflictsTester"
        private val SEGMENT = Regex("([^\\[.]+)(?:\\[([^]]+)])?")
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val DISPLAY_KEYS = listOf("alias", "name", "circleName", "fieldName", "platformKey", "displayName", "value", "notes", "bio", "description", "sourceType")
        internal fun title(value: JsonObject?, kind: String): String = value?.str("name") ?: value?.str("fieldName") ?: value?.str("alias")
            ?: value?.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject?.str("circleName")
            ?: when (kind) { "selfCard" -> "我的名片"; "collection" -> "名片夹"; "tag" -> "标签"; "fieldDefinition" -> "字段设置"; else -> "联系人" }
        private fun label(path: String): String = when {
            path.contains("platformDetails") || path.contains("accounts") -> "平台账号"
            path.contains("fields") -> "联系方式"
            path.contains("records") || path.contains("memberIds") -> "名片夹成员"
            path.contains("members") -> "标签关联"
            path.contains("avatar") -> "头像"
            path.contains("cardImage") || path.contains("background") -> "背景图片"
            path.contains("bio") -> "个人介绍"
            path.contains("notes") -> "备注"
            path.contains("description") -> "名片夹说明"
            path.contains("color", ignoreCase = true) -> "颜色"
            path.contains("circleName") || path == "alias" -> "姓名"
            else -> FIELD_LABELS[path.substringAfterLast('.')] ?: "资料"
        }
        private val FIELD_LABELS = mapOf("defaultPlatform" to "默认平台", "name" to "名称", "fieldName" to "字段名称",
            "fieldType" to "字段类型", "options" to "可选项", "sortOrder" to "排序", "isEnabled" to "是否启用",
            "showDot" to "标签色点", "createTime" to "创建时间", "source" to "来源")
    }
}
