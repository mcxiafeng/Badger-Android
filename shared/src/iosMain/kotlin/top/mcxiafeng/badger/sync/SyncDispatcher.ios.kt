package top.mcxiafeng.badger.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [KMP K09] iOS actual：BGTaskScheduler 骨架 + 前台时机兜底。
 *
 * **语义注释（真机验证登记在 K17 清单）**：
 * - BGTaskScheduler 的 `submit` 必须在 app 退后台前调用（`BGAppRefreshTaskRequest`），
 *   系统择机唤醒；Info.plist 需注册 `BGTaskSchedulerPermittedIdentifiers`（K16 落）。
 * - 前台兜底：App 回前台（生命周期回调，K16 接线）时调 [kick] 直接重放——iOS 无
 *   WorkManager 等价的「进程内常驻约束任务」，前台时机是主要重放窗口。
 * - 后台唤醒窗口约 30s：重放循环须在 `expirationHandler` 触发前让出（行数分批 + 检查点）。
 * - 网络可达性约束用 `BGAppRefreshTask` 系统调度隐含处理，无 Android Constraints 等价物。
 *
 * 当前骨架仅实现 kick 合并去抖（Mutex + 单 flight），BGTask 注册留 K16/K17 真机阶段。
 */
actual class SyncDispatcher(
    private val replay: suspend () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val kickMutex = Mutex()

    @kotlin.concurrent.Volatile
    private var kickPending = false

    actual fun kick() {
        if (kickPending) return
        kickPending = true
        scope.launch {
            kickMutex.withLock {
                kickPending = false
                // TODO(K16/K17): 接 BGTaskScheduler submit + 前台生命周期回调；
                // 骨架阶段直接在 Default 调度器上重放（前台场景语义等价）
                replay()
            }
        }
    }
}
