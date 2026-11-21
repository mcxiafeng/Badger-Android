package top.mcxiafeng.badger.data.sync

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Immutable
data class ImportOption(val key: String, val label: String)
@Immutable
data class LocalImportItem(val key: String, val title: String, val summary: String, val options: List<ImportOption>, val selection: String)
@Immutable
data class LocalImportPlan(val id: String, val items: List<LocalImportItem>)

/** Explicit copy, with a durable preview and stable per-source receipts. Never modifies source business rows. */
@Singleton
class WorkspaceImport @Inject constructor(private val accounts: AccountStore, private val coordinator: SyncCoordinator) {
    suspend fun shouldOffer(scope: AccountScope): Boolean = withContext(Dispatchers.IO) {
        if (scope.isLocal) return@withContext false
        val source = source()
        if (!source.values.any(::meaningful)) return@withContext false
        val fingerprint = fingerprint(source.values)
        coordinator.database(scope).withTransaction {
            SyncLedger.load(coordinator.database(scope)).get(PROMPT) != fingerprint
        }
    }

    suspend fun dismissOffer(scope: AccountScope) = withContext(Dispatchers.IO) {
        val fingerprint = fingerprint(source().values)
        val db = coordinator.database(scope)
        db.withTransaction { SyncLedger.load(db).put(PROMPT, fingerprint) }
    }

    suspend fun prepare(scope: AccountScope): LocalImportPlan = withContext(Dispatchers.IO) {
        require(!scope.isLocal)
        coordinator.synchronize(scope)
        coordinator.locked(scope) {
            coordinator.requireReadyForImport(scope)
            val source = source()
            require(source.values.any(::meaningful)) { "没有可导入的本地资料" }
            val db = coordinator.database(scope)
            db.withTransaction {
                val ledger = SyncLedger.load(db)
                ledger.get(CURRENT)?.let { return@withTransaction view(ledger, it) }
                val target = WorkspaceCodec(ledger, coordinator.scopedContext(scope)).capture().mapValues { (_, root) ->
                    root.copy(version = requireNotNull(ledger.shadow(root.key)).version)
                }
                val batch = UUID.randomUUID().toString()
                for (root in source.values) stageRoot(ledger, batch, root, target.values)
                ledger.put(CURRENT, batch)
                ledger.put("import/$batch/fingerprint", fingerprint(source.values))
                ledger.put(PROMPT, fingerprint(source.values))
                Log.d(TAG, "本地资料导入预览已保存 records=${source.size}")
                view(ledger, batch)
            }
        }
    }

    private suspend fun stageRoot(ledger: SyncLedger, batch: String, root: SyncRoot, targets: Collection<SyncRoot>) {
        val prior = ledger.get("imported/${root.key}")?.let(SyncJson::parse)
        val priorId = prior?.str("targetId")
        val candidates = targets.filter { it.kind == root.kind && (it.id == priorId || candidate(root, it)) }
            .sortedBy { if (it.id == priorId) 0 else 1 }
        for (target in candidates) ledger.put("import/$batch/target/${target.key}", SyncJson.encode(target.json()))
        val equivalent = root.kind == "fieldDefinition" && candidates.firstOrNull()?.let { sameDefinition(root, it) } == true
        val unchanged = prior?.str("fingerprint") == fingerprint(listOf(root))
        val default = when {
            unchanged -> SKIP
            candidates.isNotEmpty() && (root.kind in setOf("selfCard", "fieldDefinition") || priorId != null) -> candidates.first().key
            else -> NEW
        }
        val record = SyncJson.obj("source" to root.json(), "candidates" to candidates.map { it.key },
            "selection" to default, "visible" to !equivalent)
        ledger.put("import/$batch/source/${root.key}", SyncJson.encode(record))
    }

    suspend fun rebuild(scope: AccountScope): LocalImportPlan = withContext(Dispatchers.IO) {
        coordinator.locked(scope) {
            val db = coordinator.database(scope)
            db.withTransaction {
                val ledger = SyncLedger.load(db)
                val batch = ledger.get(CURRENT)
                if (batch != null) for (key in ledger.entries("import/$batch/").keys) ledger.remove(key)
                ledger.remove(CURRENT)
                Log.d(TAG, "旧导入预览已废弃，重新读取源资料和目标基线")
            }
        }
        prepare(scope)
    }

    suspend fun select(scope: AccountScope, key: String, choice: String): LocalImportPlan = withContext(Dispatchers.IO) {
        val db = coordinator.database(scope)
        db.withTransaction {
            val ledger = SyncLedger.load(db)
            val batch = requireNotNull(ledger.get(CURRENT))
            val storage = "import/$batch/source/$key"
            val record = SyncJson.parse(requireNotNull(ledger.get(storage)))
            val root = SyncRoot.read(record["source"].asJsonObject)
            val candidates = SyncJson.array(record, "candidates").map { it.asString }
            require(choice == SKIP || (choice == NEW && root.kind != "selfCard") || choice in candidates)
            record.addProperty("selection", choice)
            ledger.put(storage, SyncJson.encode(record))
            view(ledger, batch)
        }
    }

    suspend fun confirm(scope: AccountScope, batch: String): Int = withContext(Dispatchers.IO) {
        val count = coordinator.locked(scope) {
            coordinator.requireReadyForImport(scope)
            val db = coordinator.database(scope)
            workspaceTransaction(db) {
                val ledger = SyncLedger.load(db)
                require(ledger.get(CURRENT) == batch) { "导入预览已变化，请重新查看" }
                val records = ledger.entries("import/$batch/source/").values.map(SyncJson::parse)
                val working = WorkspaceCodec(ledger, coordinator.scopedContext(scope)).capture()
                val selected = records.filter { it.str("selection") != SKIP }
                validateTargets(selected)
                val mapping = buildMapping(records, ledger, working)
                val remapper = ImportRemapper(ledger, coordinator.scopedContext(accounts.localScope()),
                    coordinator.scopedContext(scope), mapping)
                var changed = 0
                val roots = mutableListOf<SyncRoot>()
                for (record in selected) {
                    val source = SyncRoot.read(record["source"].asJsonObject)
                    val choice = requireNotNull(record.str("selection"))
                    val target = if (choice == NEW) null else SyncRoot.read(SyncJson.parse(
                        requireNotNull(ledger.get("import/$batch/target/$choice"))))
                    require(target == null || target.value == working[target.key]?.value) { "云端资料已有更新，请重新建立导入预览" }
                    val imported = remapper.remap(source, requireNotNull(mapping[source.id]), target)
                    val result = if (target != null) mergeImport(imported, target, ledger) else imported
                    if (target == null || result.value != target.value) { roots.add(result); changed++ }
                    ledger.put("imported/${source.key}", SyncJson.encode(SyncJson.obj(
                        "targetId" to result.id, "fingerprint" to fingerprint(listOf(source)))))
                }
                require(roots.map { it.key }.distinct().size == roots.size) { "同一批次存在重复写入目标，请重新选择合并方式" }
                WorkspaceApplier(ledger).apply(roots)
                val actual = WorkspaceCodec(ledger, coordinator.scopedContext(scope)).capture()
                for (root in roots) {
                    val conflict = ledger.get("conflict/${root.key}")?.let(StoredConflict::read) ?: continue
                    ledger.put("conflict/${root.key}", SyncJson.encode(conflict.copy(local = actual[root.key]).json()))
                }
                ledger.put("import-result/$batch", SyncJson.encode(SyncJson.obj("copied" to changed,
                    "conflicts" to ledger.entries("conflict/").size)))
                for (key in ledger.entries("import/$batch/").keys) ledger.remove(key)
                ledger.remove(CURRENT)
                Log.d(TAG, "本地资料副本已事务保存 records=$changed；原件保持不变")
                changed
            }
        }
        coordinator.synchronize(scope)
        count
    }

    private fun validateTargets(selected: List<JsonObject>) {
        require(selected.isNotEmpty()) { "请至少选择一项资料" }
        val targets = selected.mapNotNull { record ->
            val source = SyncRoot.read(record["source"].asJsonObject)
            record.str("selection")?.takeIf { it != NEW && source.kind != "fieldDefinition" }
        }
        require(targets.distinct().size == targets.size) { "同一批次不能将多条资料合并到同一个目标，请选择新建副本或分批导入" }
    }

    private suspend fun buildMapping(records: List<JsonObject>, ledger: SyncLedger, working: Map<String, SyncRoot>): Map<String, String> {
        val mapping = linkedMapOf<String, String>()
        for (record in records) {
            val root = SyncRoot.read(record["source"].asJsonObject)
            val choice = record.str("selection")
            val candidates = SyncJson.array(record, "candidates").map { it.asString }
            when {
                choice != null && choice != NEW && choice != SKIP -> mapping[root.id] = choice.substringAfter('/')
                choice == NEW -> mapping[root.id] = UUID.randomUUID().toString()
                root.kind in setOf("fieldDefinition", "selfCard") && candidates.isNotEmpty() -> mapping[root.id] = candidates.first().substringAfter('/')
                else -> {
                    val old = ledger.get("imported/${root.key}")?.let(SyncJson::parse)?.str("targetId")
                    if (old != null && working.containsKey("${root.kind}/$old")) mapping[root.id] = old
                }
            }
        }
        return mapping
    }

    private suspend fun mergeImport(incoming: SyncRoot, target: SyncRoot, ledger: SyncLedger): SyncRoot {
        if (incoming.kind == "fieldDefinition" && sameDefinition(incoming, target)) return target
        val supplemented = ImportRemapper.supplement(requireNotNull(incoming.value), requireNotNull(target.value))
        val result = SyncMerge.merge(null, supplemented, target.value)
        val root = target.copy(value = result.value?.asJsonObject)
        if (result.conflicts.isNotEmpty()) ledger.put("conflict/${target.key}", SyncJson.encode(
            StoredConflict(target.key, null, root, target, result.conflicts).json()))
        return root
    }

    private fun view(ledger: SyncLedger, batch: String): LocalImportPlan {
        val items = ledger.entries("import/$batch/source/").values.map(SyncJson::parse).filter { it.flag("visible") }.map { record ->
            val source = SyncRoot.read(record["source"].asJsonObject)
            val options = buildList {
                add(ImportOption(SKIP, "不导入"))
                if (source.kind != "selfCard") add(ImportOption(NEW, if (source.kind == "tag") "新建副本（同名标签会重命名）" else "新建副本"))
                for (key in SyncJson.array(record, "candidates").map { it.asString }) {
                    val target = SyncRoot.read(SyncJson.parse(requireNotNull(ledger.get("import/$batch/target/$key"))))
                    add(ImportOption(key, "合并到：${WorkspaceConflicts.title(target.value, target.kind)}${summary(target.value)}"))
                }
            }
            LocalImportItem(source.key, "${kindLabel(source.kind)} · ${WorkspaceConflicts.title(source.value, source.kind)}",
                summary(source.value).ifBlank { "本地资料" }, options, record.str("selection") ?: SKIP)
        }
        return LocalImportPlan(batch, items)
    }

    private suspend fun source(): Map<String, SyncRoot> {
        val local = accounts.localScope()
        val db = coordinator.database(local)
        return workspaceTransaction(db) { WorkspaceCodec(SyncLedger.load(db), coordinator.scopedContext(local)).capture() }
    }

    private fun candidate(source: SyncRoot, target: SyncRoot): Boolean {
        val left = source.value ?: return false
        val right = target.value ?: return false
        return when (source.kind) {
            "selfCard" -> true
            "fieldDefinition" -> if (left.flag("isSystem")) right.flag("isSystem") && left.str("fieldKey") == right.str("fieldKey")
                else !right.flag("isSystem") && left.str("fieldName") == right.str("fieldName") && left.str("fieldType") == right.str("fieldType") && left.str("options") == right.str("options")
            "collection", "tag" -> left.str("name")?.let(::tagNameKey) == right.str("name")?.let(::tagNameKey)
            "person" -> WorkspaceConflicts.title(left, source.kind).equals(WorkspaceConflicts.title(right, target.kind), true) ||
                left.details()?.items("platformDetails").orEmpty().any { a -> !a.str("value").isNullOrBlank() &&
                    right.details()?.items("platformDetails").orEmpty().any { b -> a.str("platformKey") == b.str("platformKey") && a.str("value") == b.str("value") } }
            else -> false
        }
    }

    companion object {
        private const val TAG = "WorkspaceImportTester"
        private const val CURRENT = "import/current"
        private const val PROMPT = "import/promptedFingerprint"
        const val NEW = "new"
        const val SKIP = "skip"
        private fun fingerprint(roots: Collection<SyncRoot>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            roots.sortedBy { it.key }.forEach { digest.update(SyncJson.canonical(it.json()).toByteArray(Charsets.UTF_8)) }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
        private fun sameDefinition(left: SyncRoot, right: SyncRoot): Boolean {
            val a = left.value?.deepCopy()?.apply { remove("createTime") }
            val b = right.value?.deepCopy()?.apply { remove("createTime") }
            return a == b
        }
        private fun meaningful(root: SyncRoot): Boolean = when (root.kind) {
            "person", "tag" -> true
            "selfCard" -> root.value?.let { value ->
                val profile = value["profile"].asJsonObject
                profile.str("circleName") !in setOf(null, "", "用户") || profile.items("accounts").isNotEmpty() ||
                    value.details()?.let { !it.str("bio").isNullOrBlank() || !it.str("notes").isNullOrBlank() ||
                        it.items("fields").isNotEmpty() || it.items("platformDetails").isNotEmpty() ||
                        profile.str("avatarAssetId") != null || it.str("cardImageAssetId") != null } == true
            } == true
            "collection" -> root.value?.let { it.str("name") != "默认名片夹" || it.details()?.items("records")?.isNotEmpty() == true || it.details()?.str("backgroundAssetId") != null } == true
            "fieldDefinition" -> root.value?.let { !isDefaultSystemDefinition(it) } == true
            else -> false
        }
        private fun summary(value: JsonObject?): String = value?.details()?.let { details ->
            val parts = mutableListOf<String>()
            if (details.has("platformDetails")) parts.addAll(details.items("platformDetails").map { "${it.str("platformKey")}: ${it.str("value") ?: it.str("jumpLink").orEmpty()}" })
            if (details.has("fields")) parts.addAll(details.items("fields").mapNotNull { it.str("value") })
            listOf("bio", "notes", "description").mapNotNullTo(parts) { details.str(it) }
            value.str("notes")?.let(parts::add)
            parts.joinToString("\n")
        } ?: value?.str("fieldName").orEmpty()
        private fun kindLabel(kind: String): String = when (kind) {
            "person" -> "联系人"; "selfCard" -> "我的名片"; "collection" -> "名片夹"; "tag" -> "标签"; else -> "字段设置"
        }
    }
}
