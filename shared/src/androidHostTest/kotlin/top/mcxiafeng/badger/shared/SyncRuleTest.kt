package top.mcxiafeng.badger.shared

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.PullStep
import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.network.core.CollectionApi
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.network.core.TagsApi
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

/**
 * 同步规则集成探针：Room 内存库 + 直连运行中的 Badger-Server，验证三条本地优先规则。
 *
 * 规则1 本地有数据无 pending → refresh 服务端覆盖本地
 * 规则2 本地有 pending → refresh 跳过；模拟 push 成功（真实调 API）+ 清 pending → 拉取恢复
 * 规则3 本地无数据 → refresh 直接拉取
 * 附加：Collection/Tags 实体 decode 对齐验证（建夹/建标 → 拉取 → 清理）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRuleTest {

    private val lines = mutableListOf<String>()
    private var failed = 0

    // 开发后端预置 ID（与 EntityApiTest 同源）
    private val seedUserUuid = TestSession.SEED_USER_UUID
    private val seedProfileUuid = Uuid.parse("0d4e43ee-59e2-417b-b474-690d86f28847")

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
            lines.add("✓ $name")
            println("✓ $name")
        } catch (e: Throwable) {
            failed++
            val msg = "✗ $name — ${e.javaClass.simpleName}: ${e.message}"
            lines.add(msg)
            println(msg)
            println("  stack: " + e.stackTrace.take(12).joinToString(" <- "))
        }
    }

    private fun createdUuid(result: HttpResult): Uuid? {
        val body = (result as? HttpResult.Success)?.body ?: return null
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return null
        return data["uuid"]?.jsonPrimitive?.content?.let { Uuid.parse(it) }
    }

    @Test
    fun syncRules() = runTest {
        println("==================================================================================================")
        println("SyncRuleTest: 开始（后端连接走 PublicApi 配置）")
        println("==================================================================================================")
        TestSession.ensureLoggedIn()

        val context = RuntimeEnvironment.getApplication()
        val cache = TestSession.cacheDb(context).build()
        val system = TestSession.systemDb(context).build()
        // autoPush=false：规则原语验证需要确定性（不被后台自动重放抢先消费 atom）
        val repo = SocialRepository(cache, system, autoPush = false)

        // ---------------- 规则3：本地无数据 → 直接拉取 ----------------
        step("规则3 本地空 refreshAll 直接拉取") {
            check(cache.personDao().observeAllPersons().first().isEmpty()) { "前置：本地应为空" }
            val report = repo.refreshAll()
            // 服务端 person 列表可为空（Updated(0)）——规则语义是"服务端覆盖本地"，非空断言不成立
            check(report.persons is PullStep.Updated) { "persons=${report.persons}" }
        }

        // ---------------- 规则1：本地有数据无 pending → 服务端覆盖 ----------------
        step("规则1 无 pending 再拉取") {
            val s = repo.refreshPersons()
            check(s is PullStep.Updated) { "s=$s" }
            check(system.syncAtomDao().pendingCount(EntityKind.PERSON) == 0) { "前置：无 pending" }
        }

        // ---------------- Collection/Tags decode 对齐验证 ----------------
        var collectionUuid: Uuid? = null
        step("Collection 建夹→拉取（decode 对齐）") {
            val c = Collection(
                uuid = Uuid.random(),
                name = "SyncRuleTest夹",
                description = "decode 验证",
                ownerUuid = seedUserUuid,
                createTime = System.currentTimeMillis(),
            )
            val r = CollectionApi.createCollection(c)
            check(r is HttpResult.Success) { "createCollection: $r" }
            collectionUuid = createdUuid(r)
            val s = repo.refreshCollections()
            check(s is PullStep.Updated) { "s=$s" }
            check(cache.collectionDao().observeAllCollections().first().any { it.uuid == collectionUuid }) {
                "拉取后本地 collection 缺失"
            }
        }
        var tagUuid: Uuid? = null
        step("Tag 建标→拉取（decode 对齐）") {
            val t = Tags(uuid = Uuid.random(), name = "SyncRuleTest标")
            val r = TagsApi.createTag(t)
            check(r is HttpResult.Success) { "createTag: $r" }
            tagUuid = createdUuid(r)
            val s = repo.refreshTags()
            check(s is PullStep.Updated) { "s=$s" }
            check(cache.tagsDao().observeAllTags().first().any { it.uuid == tagUuid }) { "拉取后本地 tag 缺失" }
        }

        // ---------------- 规则2：有 pending → 拉取跳过 ----------------
        val optimistic = Person(
            uuid = Uuid.random(),
            ownerId = seedUserUuid,
            profileId = seedProfileUuid,
            name = "SyncRuleTest乐观人",
            createTime = System.currentTimeMillis(),
        )
        step("规则2 乐观写记 pending → refreshPersons 跳过") {
            repo.savePerson(optimistic)
            check(system.syncAtomDao().pendingCount(EntityKind.PERSON) > 0) { "savePerson 后应有 pending" }
            val s = repo.refreshPersons()
            check(s is PullStep.SkippedByPending) { "s=$s" }
            check(cache.personDao().observeAllPersons().first().any { it.uuid == optimistic.uuid }) {
                "pending 期间本地乐观数据不应被覆盖"
            }
        }
        step("规则2 push 成功（真实 API）+ 清 pending → 拉取恢复") {
            val r = PersonApi.createPerson(optimistic)
            check(r is HttpResult.Success) { "createPerson: $r" }
            system.syncAtomDao().deleteAtomsByKind(EntityKind.PERSON)
            val s = repo.refreshPersons()
            check(s is PullStep.Updated) { "s=$s" }
            check(cache.personDao().observeAllPersons().first().any { it.uuid == optimistic.uuid }) {
                "push 后服务端应有此人，本地不应丢"
            }
        }

        // ---------------- pushPending 闭环：自动重放 + 成功即拉取（规则2 完整形态） ----------------
        val autoRepo = SocialRepository(cache, system, autoPush = true)
        step("闭环 pushPending 自动重放 INSERT 并拉取") {
            val p = Person(
                uuid = Uuid.random(),
                ownerId = seedUserUuid,
                profileId = seedProfileUuid,
                name = "SyncRuleTest闭环人",
                createTime = System.currentTimeMillis(),
            )
            autoRepo.savePerson(p)
            autoRepo.pushPending()
            check(system.syncAtomDao().pendingCount(EntityKind.PERSON) == 0) { "atom 未被消费" }
            check(cache.personDao().observeAllPersons().first().any { it.uuid == p.uuid }) {
                "推送成功后未自动拉取"
            }
            PersonApi.deletePerson(p.uuid)
            repo.refreshPersons()
        }
        step("闭环 UPDATE 打到不存在的人 → 失败记 attempts 并继续门控") {
            val ghost = Person(
                uuid = Uuid.random(),
                ownerId = seedUserUuid,
                profileId = seedProfileUuid,
                name = "SyncRuleTest幽灵更新",
                createTime = System.currentTimeMillis(),
            )
            system.syncAtomDao().insertAtom(
                SyncAtom(
                    entityKind = EntityKind.PERSON,
                    syncType = SyncType.UPDATE,
                    data = Json.encodeToJsonElement(ghost).jsonObject,
                    createdAt = System.currentTimeMillis(),
                )
            )
            autoRepo.pushPending()
            check(system.syncAtomDao().pendingCount(EntityKind.PERSON) > 0) { "失败的 atom 应保留" }
            val atom = system.syncAtomDao().getAllAtoms()
                .first { it.entityKind == EntityKind.PERSON && it.syncType == SyncType.UPDATE }
            check(atom.attempts >= 1) { "attempts 未累计" }
            check(atom.lastError != null) { "lastError 未记录" }
            check(autoRepo.refreshPersons() is PullStep.SkippedByPending) { "失败 pending 应继续门控拉取" }
            system.syncAtomDao().deleteAtomsByKind(EntityKind.PERSON)
        }
        step("闭环 Collection create 服务端保留客户端 uuid") {
            val c = Collection(
                uuid = Uuid.random(),
                name = "SyncRuleTest回填夹",
                description = "uuid 保留验证",
                ownerUuid = seedUserUuid,
                createTime = System.currentTimeMillis(),
            )
            autoRepo.saveCollection(c)
            autoRepo.pushPending()
            check(system.syncAtomDao().pendingCount(EntityKind.COLLECTION) == 0) { "atom 未被消费" }
            val serverList = CollectionApi.getCollections() ?: error("拉取失败")
            check(serverList.any { it.uuid == c.uuid }) { "服务端应保留客户端 uuid" }
            check(cache.collectionDao().getCollections().any { it.uuid == c.uuid }) { "本地行应在" }
            CollectionApi.deleteCollection(c.uuid)
            repo.refreshCollections()
        }

        // ---------------- 清理 ----------------
        step("清理 optimistic person + 复验拉取") {
            repo.deletePerson(optimistic.uuid)
            val r = PersonApi.deletePerson(optimistic.uuid)
            check(r is HttpResult.Success) { "deletePerson: $r" }
            system.syncAtomDao().deleteAtomsByKind(EntityKind.PERSON)
            val s = repo.refreshPersons()
            check(s is PullStep.Updated) { "s=$s" }
            check(cache.personDao().observeAllPersons().first().none { it.uuid == optimistic.uuid }) {
                "清理后本地不应再有此人"
            }
        }
        step("清理 collection") {
            val u = collectionUuid ?: error("无 collectionUuid")
            val r = CollectionApi.deleteCollection(u)
            check(r is HttpResult.Success) { "deleteCollection: $r" }
            val s = repo.refreshCollections()
            check(s is PullStep.Updated) { "s=$s" }
        }
        step("清理 tag") {
            val u = tagUuid ?: error("无 tagUuid")
            val r = TagsApi.deleteTag(u)
            check(r is HttpResult.Success) { "deleteTag: $r" }
            val s = repo.refreshTags()
            check(s is PullStep.Updated) { "s=$s" }
        }

        // 收尾释放：掐断 Room InvalidationTracker 刷新任务跨测试类残留
        cache.close()
        system.close()
        TestSession.closeHolderDb()
        println("==================================================================================================")
        println("SyncRuleTest: 汇总 —— 共 ${lines.size} 步，失败 $failed")
        lines.forEach { println("  $it") }
        println("==================================================================================================")
        if (failed > 0) {
            throw AssertionError("SyncRuleTest 存在 $failed 项失败，详见上方输出")
        }
    }
}
