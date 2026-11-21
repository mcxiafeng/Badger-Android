package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot

internal data class StoredConflict(
    val key: String,
    val base: SyncRoot?,
    val local: SyncRoot?,
    val remote: SyncRoot,
    val paths: List<String>,
    val forced: Boolean = false,
    val canAdoptLocal: Boolean = true,
    val notice: String? = null,
) {
    fun json(): JsonObject = SyncJson.obj("key" to key, "base" to base?.json(), "local" to local?.json(),
        "remote" to remote.json(), "paths" to paths, "forced" to forced, "canAdoptLocal" to canAdoptLocal, "notice" to notice)
    companion object {
        fun read(value: String): StoredConflict {
            val obj = SyncJson.parse(value)
            fun root(key: String): SyncRoot? = obj[key]?.takeUnless { it.isJsonNull }?.let { SyncRoot.read(it.asJsonObject) }
            return StoredConflict(requireNotNull(obj.str("key")), root("base"), root("local"), requireNotNull(root("remote")),
                SyncJson.array(obj, "paths").map { it.asString }, obj.flag("forced"),
                if (obj.has("canAdoptLocal")) obj.flag("canAdoptLocal") else true, obj.str("notice"))
        }
    }
}

/** Pure merge decisions are applied together with metadata in the caller's Room transaction. */
internal class SyncReconciler(private val ledger: SyncLedger, private val context: Context) {
    suspend fun accept(remoteRows: List<SyncRoot>, revision: String) {
        val incoming = completeRoots(remoteRows)
        val applier = WorkspaceApplier(ledger)
        applier.bindExisting(incoming.values.toList())
        var local = WorkspaceCodec(ledger, context).capture()
        if (ledger.get(INITIALIZED) != "true") local = discardSeeds(local, incoming, applier)
        requireUniqueTagNames(local, incoming)
        val markers = constraints(local)
        val holds = heldDefinitions(local, incoming, markers)
        val heldKeys = holds.values.flatten().map { it.key }.toSet()
        val blocked = dependencyConflicts(local, incoming) + markers + holds.keys
        val changes = mutableListOf<SyncRoot>()
        for ((key, remote) in incoming) {
            val base = ledger.shadow(key)
            val mine = local[key]
            verifyVersion(base, remote)
            if (key in heldKeys) {
                for ((definition, roots) in holds) if (roots.any { it.key == key })
                    ledger.put("held/${definition.substringAfter('/')}/$key", SyncJson.encode(remote.json()))
                continue
            }
            val prior = ledger.get("conflict/$key")?.let(StoredConflict::read)
            val mergeBase = if (prior != null) prior.base else base
            val result = SyncMerge.merge(mergeBase?.value, mine?.value, remote.value)
            val forced = key in blocked
            if (result.conflicts.isNotEmpty() || forced) {
                val conflict = StoredConflict(key, mergeBase, mine, remote,
                    if (forced && result.conflicts.isEmpty()) listOf("关联资料") else result.conflicts, forced,
                    canAdoptLocal = key !in holds,
                    notice = if (key in holds) "云端仍有联系人使用此字段。请先采用云端定义，核对相关联系人后再删除；本机已确认的其他更改会保留。" else null)
                ledger.put("conflict/$key", SyncJson.encode(conflict.json()))
                continue
            }
            ledger.remove("conflict/$key")
            if (result.value != mine?.value) changes.add(remote.copy(
                deleted = result.value == null, value = result.value?.asJsonObject))
            ledger.shadow(remote)
        }
        captureUncreatedConflicts(local, incoming, blocked)
        applier.apply(changes)
        ledger.put(INITIALIZED, "true")
        ledger.put(SyncLedger.SNAPSHOT_REVISION, revision)
        ledger.highWater(revision)
        Log.d(TAG, "完整快照已核对 changes=${changes.size}, conflicts=${ledger.entries("conflict/").size}")
    }

    private fun verifyVersion(base: SyncRoot?, remote: SyncRoot) {
        if (base == null) return
        require(SyncJson.counter(remote.version) >= SyncJson.counter(base.version)) { "资料版本回退，原资料已保留" }
        require(!base.deleted || remote.deleted) { "已删除记录被原标识复活，已暂停应用" }
        require(remote.version != base.version || (remote.deleted == base.deleted && remote.value == base.value) ||
            ledger.get("unverified/${remote.key}") == remote.version) { "相同版本的资料内容不一致" }
    }

    private fun completeRoots(roots: List<SyncRoot>): Map<String, SyncRoot> {
        val incoming = roots.filter { it.kind in KINDS }.associateBy { it.key }
        require(incoming.values.count { it.kind == "selfCard" && !it.deleted } == 1) { "快照缺少唯一的我的名片" }
        for (key in ledger.entries("shadow/").keys.map { it.removePrefix("shadow/") })
            require(incoming.containsKey(key)) { "完整快照缺少已知记录，已暂停应用" }
        return incoming
    }

    private suspend fun constraints(local: Map<String, SyncRoot>): Set<String> {
        val active = mutableSetOf<String>()
        for ((storage, encoded) in ledger.entries("constraint/")) {
            val root = SyncRoot.read(SyncJson.parse(encoded))
            if (root.value == local[root.key]?.value) active.add(root.key)
            else {
                ledger.remove(storage)
                val conflict = ledger.get("conflict/${root.key}")?.let(StoredConflict::read)
                if (conflict?.remote?.version == "0") ledger.remove("conflict/${root.key}")
            }
        }
        return active
    }

    private suspend fun heldDefinitions(local: Map<String, SyncRoot>, remote: Map<String, SyncRoot>,
        markers: Set<String>): Map<String, List<SyncRoot>> {
        val result = linkedMapOf<String, List<SyncRoot>>()
        for (definition in remote.values.filter { it.kind == "fieldDefinition" && !it.deleted && it.key !in local }) {
            if (ledger.shadow(definition.key) == null) continue
            val references = remote.values.filter { !it.deleted && references(it, definition.id) }
            val newReferences = references.any {
                referencesPart(it, definition.id) != ledger.shadow(it.key)?.let { base -> referencesPart(base, definition.id) }
            }
            if (references.isNotEmpty() && (newReferences || definition.key in markers)) result[definition.key] = references
        }
        val wanted = result.flatMap { (key, roots) -> roots.map { "held/${key.substringAfter('/')}/${it.key}" } }.toSet()
        for (key in ledger.entries("held/").keys) if (key !in wanted) ledger.remove(key)
        return result
    }

    private suspend fun captureUncreatedConflicts(local: Map<String, SyncRoot>, remote: Map<String, SyncRoot>, blocked: Set<String>) {
        for (root in local.values.filter { it.key in blocked && it.key !in remote }) {
            val invalidParent = root.value?.details()?.let { details ->
                listOf("fields", "records", "members").filter { details.has(it) }.flatMap { details.items(it) }.any { item ->
                    val id = item.str("personId") ?: item.str("definitionId")
                    remote.values.any { it.id == id && it.deleted }
                }
            } == true
            val absent = root.copy(version = "0", deleted = true, value = null)
            ledger.put("conflict/${root.key}", SyncJson.encode(StoredConflict(root.key, null, root, absent,
                listOf("关联资料"), forced = true, canAdoptLocal = !invalidParent,
                notice = "云端尚未创建此记录。先处理关联资料，再继续同步；采用云端内容会移除这条未上传的本机记录。").json()))
        }
        for ((key, encoded) in ledger.entries("conflict/")) {
            val conflict = StoredConflict.read(encoded)
            if (conflict.remote.version == "0" && conflict.key !in blocked && conflict.key !in remote) ledger.remove(key)
        }
    }

    private fun requireUniqueTagNames(local: Map<String, SyncRoot>, incoming: Map<String, SyncRoot>) {
        val mine = local.values.filter { it.kind == "tag" }.groupBy { tagNameKey(requireNotNull(it.value?.str("name"))) }
        for (root in incoming.values.filter { it.kind == "tag" && !it.deleted }) {
            val duplicates = mine[tagNameKey(requireNotNull(root.value?.str("name")))].orEmpty()
            if (duplicates.any { it.id != root.id }) throw TagNameConflictException()
        }
    }

    private suspend fun discardSeeds(local: Map<String, SyncRoot>, incoming: Map<String, SyncRoot>,
        applier: WorkspaceApplier): Map<String, SyncRoot> {
        val seeds = local.values.filter { isSeed(it) || (it.kind == "fieldDefinition" && it.key in incoming &&
            it.value?.let(::isDefaultSystemDefinition) == true) }
        applier.apply(seeds.filter { it.kind == "collection" }.map { it.copy(deleted = true, value = null) })
        return local - seeds.map { it.key }.toSet()
    }

    private fun isSeed(root: SyncRoot): Boolean {
        val value = root.value ?: return false
        val details = value.details() ?: return false
        return when (root.kind) {
            "selfCard" -> {
                val profile = value["profile"].asJsonObject
                profile.str("circleName") in setOf(null, "", "用户") && profile.items("accounts").isEmpty() &&
                    details.str("bio").isNullOrBlank() && details.str("notes").isNullOrBlank() && details.str("avatarUrl").isNullOrBlank() &&
                    profile.str("avatarAssetId") == null && details.str("cardImageAssetId") == null &&
                    details.items("fields").isEmpty() && details.items("platformDetails").isEmpty() && details.str("defaultPlatform") == null
            }
            "collection" -> value.str("name") == "默认名片夹" && details.str("description") == "所有新扫描的联系人将添加到此处" &&
                details.items("records").isEmpty() && details.str("backgroundAssetId") == null
            else -> false
        }
    }

    /** Preserve edits and relationships while a referenced person/definition deletion is unresolved. */
    private fun dependencyConflicts(local: Map<String, SyncRoot>, remote: Map<String, SyncRoot>): Set<String> {
        val blocked = mutableSetOf<String>()
        for (deleted in remote.values.filter { it.deleted && it.kind in setOf("person", "fieldDefinition") }) {
            val mine = local[deleted.key] ?: continue
            val references = local.values.filter { references(it, deleted.id) }
            val edited = mine.value != ledger.shadow(mine.key)?.value || references.any {
                referencesPart(it, deleted.id) != ledger.shadow(it.key)?.let { base -> referencesPart(base, deleted.id) }
            }
            if (edited) {
                blocked.add(deleted.key)
                blocked.addAll(references.map { it.key })
            }
        }
        return blocked
    }

    private fun references(root: SyncRoot, id: String): Boolean = referencesPart(root, id).isNotEmpty()
    private fun referencesPart(root: SyncRoot, id: String): List<JsonObject> {
        val details = root.value?.details() ?: return emptyList()
        val field = when (root.kind) { "person", "selfCard" -> "fields"; "collection" -> "records"; "tag" -> "members"; else -> return emptyList() }
        return details.items(field).filter { it.str("personId") == id || it.str("definitionId") == id }
    }

    companion object {
        const val INITIALIZED = "space/initialized"
        val KINDS = setOf("person", "selfCard", "collection", "tag", "fieldDefinition")
        private const val TAG = "SyncReconcilerTester"
    }
}
