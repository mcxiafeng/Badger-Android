package top.mcxiafeng.badger.shared

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserSyncState
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.network.core.CollectionApi
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.sync.SyncPullEngine
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

/**
 * 增量拉取引擎集成探针：Room 内存库 + 直连运行中的 Badger-Server。
 *
 * token 走真实载体链路（SystemDbHolder 全局实例种入 seed 会话 → PublicApi.readCarrierToken），
 * 引擎自身用独立内存库承载游标。验证：首次 bootstrap + 全量重放、ADD/UPDATE/REMOVE
 * 增量重放、游标与 lastSyncTime（服务端下发）回写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPullEngineTest {

    private val lines = mutableListOf<String>()
    private var failed = 0

    private val seedToken = "XVQG98HHhIR5OdDYhxRZLsw56VKbwpeVON1DwvCz3Iw"
    private val seedUserUuid = Uuid.parse("d3d8b11a-9afc-4061-a416-a69432f94624")

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
        }
    }

    @Test
    fun pullReplay() = runTest {
        println("==================================================================================================")
        println("SyncPullEngineTest: 开始（后端=${PublicApi.serverUrl}）")
        println("==================================================================================================")

        val context = RuntimeEnvironment.getApplication()
        // 载体全局实例种入登录会话（PublicApi 走真实 readCarrierToken 链路）
        TestSession.ensureLoggedIn()

        val cache = TestSession.cacheDb(context).build()
        val system = TestSession.systemDb(context).build()
        // 引擎从自己的 system 库读游标：种入同一会话（生产里两者是同一个库）
        system.userSyncStateDao().upsertState(
            UserSyncState(userUuid = TestSession.SEED_USER_UUID, token = TestSession.SEED_TOKEN)
        )
        val social = SocialRepository(cache, system, autoPush = false)
        val engine = SyncPullEngine(cache, system, social)

        val marker = "PullTest-${Uuid.random().toString().take(8)}"
        var collectionUuid: Uuid? = null
        var cursorAfterFirst = 0L

        step("服务端建夹（种子数据）") {
            val c = Collection(
                uuid = Uuid.random(),
                name = marker,
                description = "引擎重放验证",
                ownerUuid = seedUserUuid,
                createTime = System.currentTimeMillis(),
            )
            val r = CollectionApi.createCollection(c)
            check(r is HttpResult.Success) { "createCollection: $r" }
            collectionUuid = Json.parseToJsonElement(r.body).jsonObject["data"]!!
                .jsonObject["uuid"]!!.jsonPrimitive.content.let { Uuid.parse(it) }
        }

        step("首次 pullAll：bootstrap + since=0 全量重放，游标/时间回写") {
            val report = engine.pullAll()
            check(report != null) { "未登录？载体应已有 seed 行" }
            check(report.error == null) { "error=${report.error}" }
            check(report.bootstrap != null) { "游标 0 应触发 bootstrap" }
            check(report.cursor > 0L) { "游标应已推进，=${report.cursor}" }
            check(report.lastSyncTime > 0L) { "lastSyncTime 应为服务端下发值，=${report.lastSyncTime}" }
            check(cache.collectionDao().observeAllCollections().first().any { it.uuid == collectionUuid }) {
                "重放/bootstrap 后本地应有新夹"
            }
            cursorAfterFirst = report.cursor
        }

        step("增量 UPDATE：改名后 pullAll 重放到本地") {
            val renamed = cache.collectionDao().getCollection(collectionUuid!!)!!
                .copy(name = "$marker-renamed")
            val r = CollectionApi.updateCollection(renamed)
            check(r is HttpResult.Success) { "updateCollection: $r" }
            val report = engine.pullAll()!!
            check(report.error == null) { "error=${report.error}" }
            check(report.cursor >= cursorAfterFirst) { "游标单调：${report.cursor} >= $cursorAfterFirst" }
            check(cache.collectionDao().getCollection(collectionUuid!!)!!.name == "$marker-renamed") {
                "本地名应被 UPDATE 重放覆盖"
            }
            cursorAfterFirst = report.cursor
        }

        step("增量 REMOVE：删除后 pullAll 重放到本地") {
            val r = CollectionApi.deleteCollection(collectionUuid!!)
            check(r is HttpResult.Success) { "deleteCollection: $r" }
            val report = engine.pullAll()!!
            check(report.error == null) { "error=${report.error}" }
            check(cache.collectionDao().getCollection(collectionUuid!!) == null) { "本地行应被 REMOVE 重放删除" }
        }

        step("登录行游标未被清空") {
            // 引擎的游标写在自己持有的 system 库（生产中与载体同库）；holder 侧只验证 token 行仍在
            val state = system.userSyncStateDao().getActiveState()!!
            check(state.syncVersion >= cursorAfterFirst) { "游标应持续推进，=${state.syncVersion}" }
            val carrier = SystemDbHolder.get().userSyncStateDao().getActiveState()!!
            check(carrier.token == seedToken) { "载体 token 不应被拉取流程改动" }
        }

        // 收尾释放：掐断 Room InvalidationTracker 刷新任务跨测试类残留
        cache.close()
        system.close()
        TestSession.closeHolderDb()
        println("==================================================================================================")
        println("SyncPullEngineTest: 完成，failed=$failed")
        lines.forEach { println("  $it") }
        println("==================================================================================================")
        check(failed == 0) { "SyncPullEngineTest 存在 $failed 项失败，详见上方输出" }
    }
}
