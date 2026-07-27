package top.mcxiafeng.badger.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.snapshot.ContactSnapshotter
import top.mcxiafeng.badger.data.snapshot.RestoredContact
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerApi
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [V2-P11] 老数据 `isLocalOnly=true` 启动后主动 sync。
 *
 * 背景(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §3.4 + `V2_P1_HANDOFF` §5.3):
 * V2 migration 时不知道服务端 version,把老 contacts 数据**全部**搬运到 `contacts_cache` 并标记
 * `isLocalOnly=1`。本类负责在 App 启动时主动拉服务端权威版本,把本地 `isLocalOnly=true` 行替换为
 * 服务端最新状态。
 *
 * 流程(运行于 `BadgerApplication.appScope`,后台,失败可忽略):
 * ```
 *  1. scan 本地 isLocalOnly=1 行的 serverId 列表
 *  2. 调 ServerApi.listContacts(since=null, limit=200) 拉服务端权威版
 *  3. 按 serverId 关联本地 ↔ 服务端
 *     - 服务端返回 + 本地有对应 serverId → 覆盖本地(isLocalOnly=false, serverVersion=响应)
 *     - 服务端返回 + 本地无对应 serverId → 不处理(P11 不创建跨 serverId 行)
 *     - 本地有 serverId 但服务端未返回(被服务端已删) → 保留乐观写路径,走 PendingUpload CREATE/PATCH
 *  4. bumpContact 触发 InvalidationTracker,UI Flow 刷新
 * ```
 *
 * 设计要点:
 * - **不入 PendingUpload / OperationHistory**:这是 cache 层面的覆盖,不是用户主动 op,
 *   没必要进历史页;OperationHistoryPage 只展示用户可回滚的"写"操作。
 * - **不重试**:`LegacyTagFixup` 同模式,启动一次失败就下次启动再来。低优先级补齐动作。
 * - **幂等**:`AtomicBoolean` 防并发重入(WorkManager 重启 / ConfigChange 可能 in-flight)。
 * - **平台/字段/标签子表不搬**:`fromServerContact` 已用 `emptyList()` 兜底;tags 字段表跨系统复杂,
 *   P11 不处理(避免把不属于服务端的 tag 误覆盖给服务端 owner)。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ...)` → Koin
 * `singleOf(::ContactSyncBootstrapper)`。
 */
class ContactSyncBootstrapper(
    private val context: Context,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactSnapshotter: ContactSnapshotter,
    private val serverApi: ServerApi,
) {

    private val tag = TAG

    /**
     * [修复防御-并发重入]:用 `AtomicBoolean.compareAndSet(false, true)` 二次防御,
     * 避免 WorkManager 重启 / ConfigurationChange 让 onCreate 在 in-flight 时被再次
     * 触发同一个 bootstrap。
     */
    private val started = AtomicBoolean(false)

    /**
     * 启动期一次性调用。
     *
     * @return 实际处理的本地行数(成功 adopt 数 + 跳过数 + 失败数);负数 = 网络层异常
     *   (Application 端不应崩溃,只 log)
     */
    suspend fun runOnce(): Int = withContext(Dispatchers.IO) {
        if (!started.compareAndSet(false, true)) {
            Log.d(tag, "runOnce: 已在进行中,跳过本次触发")
            return@withContext 0
        }
        try {
            doRun()
        } finally {
            // [修复防御]:即使中途异常也允许下次启动重试 ——
            // 设 started=false 让下次再触发可以重新跑。
            started.set(false)
        }
    }

    private suspend fun doRun(): Int {
        val locals = contactCacheDao.getLocalOnlyContactsOnce()
        if (locals.isEmpty()) {
            Log.d(tag, "runOnce: 无 isLocalOnly 数据,跳过")
            return 0
        }
        val serverByLocalId: Map<String, Long> = locals
            .filter { !it.serverId.isNullOrBlank() }
            .associate { it.serverId!! to it.id }
        if (serverByLocalId.isEmpty()) {
            Log.d(
                tag,
                "runOnce: ${locals.size} 条 isLocalOnly 但均无 serverId,本地纯新增联系人,跳过主动 sync"
            )
            return 0
        }

        val page = try {
            serverApi.listContacts(since = null, limit = 200)
        } catch (e: ApiException) {
            Log.w(tag, "runOnce: ServerApi.listContacts 返回 ${e.status},跳过主动 sync", e)
            return -1
        } catch (e: IOException) {
            Log.w(tag, "runOnce: ServerApi.listContacts 网络异常,跳过主动 sync", e)
            return -1
        } catch (e: Exception) {
            Log.w(tag, "runOnce: ServerApi.listContacts 未知异常,跳过主动 sync", e)
            return -1
        }

        var adopted = 0
        var skipped = 0
        var failed = 0

        for (response in page.items) {
            val serverId = response.serverId ?: response.id.takeIf { it.isNotBlank() }
            if (serverId.isNullOrBlank()) {
                skipped++
                continue
            }
            val localId = serverByLocalId[serverId]
            if (localId == null) {
                // 服务端此 serverId 不在本地"待同步"列表,说明本地从未与之关联 —— 跳过
                skipped++
                continue
            }
            val local = locals.firstOrNull { it.id == localId } ?: continue
            val outcome = applyServerSnapshot(local, response, serverId)
            when (outcome) {
                AdoptOutcome.ADOPTED -> adopted++
                AdoptOutcome.SKIPPED -> skipped++
                AdoptOutcome.FAILED -> failed++
            }
        }
        Log.d(
            tag,
            "runOnce 完成: adopted=$adopted skipped=$skipped failed=$failed " +
                "serverItems=${page.items.size}"
        )
        return adopted + skipped + failed
    }

    /**
     * 把单条服务端权威版写回本地。
     * 单条异常一律 catch + warn,**不**抛(避免 bootstrap 中断)。
     */
    private suspend fun applyServerSnapshot(
        local: ContactCacheEntity,
        response: ServerApi.ContactResponse,
        serverId: String,
    ): AdoptOutcome = try {
        val contactJson = gson.toJsonTree(response.contact).asJsonObject
        contactJson.addProperty("id", serverId)
        contactJson.addProperty("server_id", serverId)
        val restored: RestoredContact = contactSnapshotter.fromServerContact(
            serverContactJson = gson.toJson(contactJson),
            contactId = local.id,
        )
        if (restored.contact.name.isBlank() && restored.platforms.isEmpty()) {
            // fromServerContact 兜底 notFound 时 contact 是空名,跳过
            Log.w(tag, "applyServerSnapshot: localId=${local.id} serverId=$serverId 解析失败/为空,跳过")
            return AdoptOutcome.SKIPPED
        }
        // [修复防御]:保留本地的 avatarPath(磁盘文件),response 只有 avatarUrl(远端)
        val avatarPath = local.avatarPath
        val now = System.currentTimeMillis()
        val adopted = restored.contact.copy(
            id = local.id,
            serverId = serverId,
            serverVersion = response.version,
            lastSyncedAt = now,
            isLocalOnly = false,
            isDeleted = false,
            avatarPath = avatarPath,
        )

        contactCacheDao.updateContact(adopted)
        contactPlatformCacheDao.deleteByContact(local.id)
        if (restored.platforms.isNotEmpty()) {
            val platformsAdapted = restored.platforms.map { p ->
                ContactPlatformCacheEntity(
                    id = 0L, // [修复防御]:重新生成 id,避免撞 platform 子表 seq
                    contactId = local.id,
                    platformKey = p.platformKey,
                    value = p.value,
                    displayName = p.displayName,
                    jumpLink = p.jumpLink,
                    originalLink = p.originalLink,
                    avatarUrl = p.avatarUrl,
                    serverVersion = response.version,
                    isLocalOnly = false,
                )
            }
            contactPlatformCacheDao.insertPlatforms(platformsAdapted)
        }
        contactCacheDao.bumpContact(local.id)
        Log.d(
            tag,
            "applyServerSnapshot: localId=${local.id} serverId=$serverId version=${response.version} " +
                "platforms=${restored.platforms.size} OK"
        )
        AdoptOutcome.ADOPTED
    } catch (e: Exception) {
        Log.w(tag, "applyServerSnapshot: localId=${local.id} serverId=$serverId 失败", e)
        AdoptOutcome.FAILED
    }

    private enum class AdoptOutcome { ADOPTED, SKIPPED, FAILED }

    companion object {
        private const val TAG = "ContactSyncBootstrap"
        private val gson = Gson()
    }
}
