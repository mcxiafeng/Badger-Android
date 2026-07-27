package top.mcxiafeng.badger.sync

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

/**
 * [V2-P4] Koin-aware [WorkerFactory],让 WorkManager 在拉起 [PendingUploadWorker] /
 * [RevertStuckOpWorker] 时从 Koin 容器解析依赖。
 *
 * 注入链路(Koin 模式):
 * ```
 *   BadgerApplication.workManagerConfiguration.setWorkerFactory(SyncWorkerFactory)
 *     → SyncWorkerFactory.createWorker(PendingUploadWorker::class) 命中
 *       → GlobalContext.get().get<PendingUploadDao>() + get<PendingUploadExecutor>()
 *       → 手动 new PendingUploadWorker(...)
 * ```
 *
 * [§14.2] 与原 Hilt 实现的差异:
 * - 原:`EntryPointAccessors.fromApplication(context, SyncWorkerEntryPoint::class.java)`
 *   静态获取依赖。
 * - 现:`GlobalContext.get().get<T>()` 直接拿单例。**前提**:BadgerApplication.onCreate
 *   内已 startKoin{},且 [pendingDao] / [executor] / [deviceIdProvider] / [serverApi] /
 *   [contactCacheDao] / [historyDao] 都已在 KoinModule 注册。
 *
 * 安全设计:任何 Koin 解析失败(Koin 还没 start,或注册缺失)都立即抛错让 WorkManager
 * 重试(类似网络抖),而不是 silent null 让 Worker NPE — 这是 [修复防御] 级处理。
 */
class SyncWorkerFactory(private val context: Context) : WorkerFactory() {

    private val koin by lazy { GlobalContext.get() }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return try {
            when (workerClassName) {
                PendingUploadWorker::class.java.name -> PendingUploadWorker(
                    appContext = appContext,
                    params = workerParameters,
                    pendingDao = koin.get(),
                    executor = koin.get(),
                )

                RevertStuckOpWorker::class.java.name -> RevertStuckOpWorker(
                    appContext = appContext,
                    params = workerParameters,
                    pendingDao = koin.get(),
                    historyDao = koin.get(),
                    contactCacheDao = koin.get(),
                )

                else -> {
                    // 未知 worker — 返 null 让 WorkManager 走默认 factory(基本不会有;
                    // 这里存在是因为 WorkManager 在初始化时可能也会查其他 worker 类名)。
                    null
                }
            }
        } catch (e: Throwable) {
            // [修复防御]: 解析失败绝不能让 WorkManager 静默吞掉 —— 这里抛 RuntimeException
            // 暴露给 WorkManager,后者会走 retry + 指数退避,日志里能看到根因。
            Log.e(TAG, "createWorker: Koin 解析失败 worker=$workerClassName", e)
            throw RuntimeException("SyncWorkerFactory Koin resolve failed: $workerClassName", e)
        }
    }

    private companion object {
        const val TAG = "SyncWorkerFactory"
    }
}