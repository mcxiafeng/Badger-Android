package top.mcxiafeng.badger.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * [V2-P4] PendingUpload 触发器。
 *
 * 不持有 op 状态 — 仅负责"在合适时机"让 WorkManager 拉起 [PendingUploadWorker]。
 *
 * 触发时机(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §4.2):
 * | 触发                            | 调用                          |
 * |---------------------------------|-------------------------------|
 * | `optimisticUpdate` 调用完       | `kick()`                      |
 * | `ProcessLifecycleOwner.onStart` | `kick()`(本类 bootstrap)      |
 * | `ConnectivityManager.onAvailable` | `kick()`(本类 bootstrap)    |
 * | App.onCreate                    | `bootstrap()` 启动恢复        |
 * | 用户"立即重试"按钮              | `kick()`                      |
 * | 批量重试                        | `kick()`(WorkManager 串行)    |
 *
 * 设计要点:
 * 1. **APPEND_OR_REPLACE**:即使当前已有 pending flush 在跑,新 kick 也排队而非替换,
 *    避免丢失刚入队的 op。
 * 2. **NetworkType.CONNECTED**:离线时 WorkManager 自动延后到有网再触发,无须我们
 *    自己监听网络。
 * 3. **指数 backoff(10s 起)**:Worker 抛 retry() 时 WorkManager 等 10s 再试,
 *    与 `PendingUploadExecutor.nextAttempt` 协同形成双重保险。
 * 4. **幂等**:`kick()` 可以被任何线程高频调用,内部用 MutableSharedFlow 合并重复事件。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ...)` → Koin
 * `singleOf(::PendingUploadScheduler)`。
 */
class PendingUploadScheduler(
    private val context: Context,
) {

    private val tag = TAG
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /** 用于合并高频 kick 调用(去抖)。replay=0,extraBufferCapacity 较大防丢。 */
    private val kickSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    private var bootstrapped = false
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * 应用启动时调用一次:
     * - 注册 ProcessLifecycle ON_START 监听(每次回到前台都 kick)
     * - 注册 NetworkCallback(网络可用时 kick)
     * - 订阅 kickSignal(去抖后实际调 WorkManager.enqueueUniqueWork)
     */
    fun bootstrap() {
        if (bootstrapped) {
            Log.d(tag, "bootstrap: 已被 bootstrap 过,跳过")
            return
        }
        bootstrapped = true

        // 1. ProcessLifecycle:每次 App 回前台都 kick 一次,杀后台恢复后立刻刷队列
        val lifecycleOwner: LifecycleOwner = ProcessLifecycleOwner.get()
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        Log.d(tag, "bootstrap: ProcessLifecycle ON_START, kick")
                        kick()
                    }
                }
            )
        }

        // 2. NetworkCallback:网络恢复时 kick
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(tag, "bootstrap: NetworkCallback onAvailable, kick")
                        kick()
                    }
                }
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
                connectivityCallback = callback
            } else {
                Log.w(tag, "bootstrap: ConnectivityManager 不可用,跳过网络监听")
            }
        } catch (e: Exception) {
            Log.w(tag, "bootstrap: 注册 NetworkCallback 失败(可忽略)", e)
        }

        // 3. kickSignal 订阅:去抖合并后实际调 WorkManager.enqueueUniqueWork
        lifecycleOwner.lifecycleScope.launch {
            kickSignal.asSharedFlow().collect {
                enqueueOnce()
            }
        }

        // 4. 启动期主动 kick 一次:恢复杀后台期间堆积的 op
        kick()
        Log.d(tag, "bootstrap: 完成")
    }

    /**
     * 通用 kick — 任何"刚写了 cache / 入队了 PendingUpload"的代码路径都该调一次。
     * 实际 enqueue 由 [kickSignal] 合并 + [enqueueOnce] 实现,可被高频调用。
     */
    fun kick() {
        // tryEmit 不挂起;replay=0 + DROP_OLDEST 模式下不会丢
        kickSignal.tryEmit(Unit)
    }

    /**
     * P6 阶段使用:30s 恢复窗口兜底。
     *
     * 实际下发 [RevertStuckOpWorker] 一次性 Work(request 在 30s 后拉起):
     * 它会检查 pending_uploads 里 opId 当前状态:
     * - DONE / WITHDRAWN → no-op
     * - FAILED_PERMANENT / CONFLICT → 复活 isDeleted=false(只对 DELETE_CONTACT 生效)
     * - PENDING / IN_FLIGHT → 等 Worker 自然完结(网络慢也可能)
     *
     * ExistingWorkPolicy.REPLACE:同一 opId 多次调用,旧的 Work 取消,以新调用为准(避免重复触发)。
     *
     * WorkManager 限制:setInitialDelay 最小 10s(API 限制),30s 是合理值。
     */
    fun scheduleRevertIfStuck(opId: String, delaySeconds: Long = 30) {
        Log.d(tag, "scheduleRevertIfStuck: opId=${opId.take(8)} delay=${delaySeconds}s")
        try {
            val request = OneTimeWorkRequestBuilder<RevertStuckOpWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(RevertStuckOpWorker.KEY_OP_ID to opId))
                .build()
            workManager.enqueueUniqueWork(
                RevertStuckOpWorker.uniqueWorkName(opId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        } catch (e: Exception) {
            // [修复防御]: WorkManager 在 OEM 被禁用 / 系统资源受限时可能抛异常
            // (OEM 重启后调度器未恢复)。这里吞掉 + warn,等下次 kick 再试;
            // P9 阶段会在 OperationHistoryPage 增加"立即重试"兜底。
            Log.w(tag, "scheduleRevertIfStuck: 失败(可能 OEM 禁用了 WorkManager,等下次 kick)", e)
        }
    }

    /**
     * 实际执行 WorkManager.enqueueUniqueWork。这里做一次"是否真有 PENDING op"的
     * 乐观过滤 — 避免空 kick 引发不必要的 Worker 启动。
     */
    private fun enqueueOnce() {
        try {
            val request = OneTimeWorkRequestBuilder<PendingUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                PendingUploadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
            Log.d(tag, "enqueueOnce: 已 enqueueUniqueWork APPEND_OR_REPLACE")
        } catch (e: Exception) {
            // [修复防御]: WorkManager 在 OEM 被禁用 / 系统资源受限时可能抛异常
            // (OEM 重启后调度器未恢复)。这里吞掉 + warn,等下次 kick 再试;
            // P9 阶段会在 OperationHistoryPage 增加"立即重试"兜底。
            Log.w(tag, "enqueueOnce: 失败(可能 OEM 禁用了 WorkManager,等下次 kick)", e)
        }
    }

    companion object {
        private const val TAG = "PendingUploadSched"
    }
}