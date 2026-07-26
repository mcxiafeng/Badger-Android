package top.mcxiafeng.badger.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.network.ServerApi

/**
 * [V2-P4] Hilt-aware [WorkerFactory],让 WorkManager 在拉起 [PendingUploadWorker] 时
 * 走 Hilt 注入 [PendingUploadDao] / [PendingUploadExecutor] 等依赖。
 *
 * 注入链路:
 * ```
 *   BadgerApplication.workManagerConfiguration.setWorkerFactory(SyncWorkerFactory)
 *     → SyncWorkerFactory.createWorker(PendingUploadWorker::class) 命中
 *       → HiltEntryPoint 拿到 PendingUploadDao / Executor
 *       → @AssistedInject 构造 Worker
 * ```
 *
 * P6 阶段新增 [RevertStuckOpWorker] 分支,30s 恢复窗口兜底。
 */
class SyncWorkerFactory(private val context: Context) : WorkerFactory() {

    private val entryPoint: SyncWorkerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context, SyncWorkerEntryPoint::class.java)
    }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            PendingUploadWorker::class.java.name -> PendingUploadWorker(
                appContext = appContext,
                params = workerParameters,
                pendingDao = entryPoint.pendingUploadDao(),
                executor = entryPoint.pendingUploadExecutor(),
            )
            RevertStuckOpWorker::class.java.name -> RevertStuckOpWorker(
                appContext = appContext,
                params = workerParameters,
                pendingDao = entryPoint.pendingUploadDao(),
                historyDao = entryPoint.operationHistoryDao(),
                contactCacheDao = entryPoint.contactCacheDao(),
            )
            else -> {
                // 未知 worker — 返 null 让 WorkManager 走默认 factory(基本不会有;
                // 这里存在是因为 WorkManager 在初始化时可能也会查其他 worker 类名)。
                null
            }
        }
    }
}

/**
 * Worker 工厂用的 Hilt EntryPoint — 把 Worker 需要的依赖集中一处声明,
 * SyncWorkerFactory 通过 EntryPointAccessors 拿到实例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun pendingUploadDao(): PendingUploadDao
    fun operationHistoryDao(): OperationHistoryDao
    fun pendingUploadExecutor(): PendingUploadExecutor
    fun deviceIdProvider(): DeviceIdProvider
    fun serverApi(): ServerApi
    fun contactCacheDao(): ContactCacheDao
}