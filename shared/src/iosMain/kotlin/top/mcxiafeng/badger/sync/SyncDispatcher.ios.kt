package top.mcxiafeng.badger.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "SyncDispatcher.ios"

/**
 * [KMP K16] iOS actual 实接：BGAppRefreshTask 系统调度 + 前台时机兜底。
 *
 * **时序语义（真机验证登记 K17，模拟器不触发 BGTask）**：
 * - 注册：`BGTaskScheduler.registerForTaskWithIdentifier` **必须在 app didFinishLaunching 结束前调用**——
 *   Swift 壳在 `App.init()` 内同步构造 `MainViewController()`，Kotlin bootstrap 由此在 launch 窗口内完成注册；
 * - 前台兜底（主要重放窗口）：回前台通知 → [kick] 直接重放（iOS 无 WorkManager 的「进程内常驻约束任务」等价物）；
 * - 后台调度：进后台时 submit `BGAppRefreshTaskRequest`，系统择机唤醒（网络可达性由
 *   BGAppRefreshTask 语义隐含，无 Android Constraints 等价物）；唤醒窗口约 30s，
 *   `expirationHandler` 触发前必须让出——重放循环行数分批 + 检查点由 SyncEngine 保证；
 * - 重放完成后**重新 submit** 一次请求，维持下一轮调度机会（Apple 官方推荐模式）；
 * - `includeBackoff=false`：BGTask 触发与 Android WorkManager 触发同语义（尊重行级退避）。
 */
@OptIn(ExperimentalForeignApi::class)
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
                try {
                    replay()
                } catch (e: Throwable) {
                    BadgerLog.e(TAG, "kick replay 失败（下次时机重试）", e)
                }
            }
        }
    }

    /**
     * 启动期注册（IosAppBootstrap.initialize 调用，launch 窗口内）：
     * BGTask handler + 前后台生命周期观察者。
     */
    fun registerBackgroundTask() {
        val center = NSNotificationCenter.defaultCenter
        // 前台兜底：回前台 → kick()（iOS 主要重放窗口）
        center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            BadgerLog.d(TAG, "willEnterForeground → kick()（前台主要重放窗口）")
            kick()
        }
        // 进后台 → submit BGAppRefreshTaskRequest（系统择机唤醒）
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            BadgerLog.d(TAG, "didEnterBackground → submit BGAppRefreshTaskRequest")
            submitRefreshRequest()
        }
        // 注册 BGTask handler（必须在 didFinishLaunching 结束前调用）
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = BG_REFRESH_IDENTIFIER,
            usingQueue = NSOperationQueue.mainQueue,
            launchHandler = { task: BGTask? ->
                if (task != null) handleBackgroundTask(task)
            },
        )
        BadgerLog.d(TAG, "BGAppRefreshTask 已注册: $BG_REFRESH_IDENTIFIER")
    }

    private fun handleBackgroundTask(task: BGTask) {
        val refreshTask = task as? BGAppRefreshTask
        if (refreshTask == null) {
            BadgerLog.w(TAG, "BGTask 类型非 AppRefresh: ${task::class.simpleName}")
            task.setTaskCompletedWithSuccess(false)
            return
        }
        BadgerLog.d(TAG, "BGTask 唤醒，开始 Outbox 重放（后台窗口 ~30s）")
        refreshTask.expirationHandler = {
            BadgerLog.w(TAG, "BGTask expirationHandler 触发，中止重放（剩余行留队）")
        }
        scope.launch {
            val success = try {
                kickMutex.withLock { replay() }
                true
            } catch (e: Throwable) {
                BadgerLog.e(TAG, "BGTask 重放失败", e)
                false
            }
            // 完成后重新 submit，维持下一轮调度机会（Apple 官方推荐模式）
            submitRefreshRequest()
            refreshTask.setTaskCompletedWithSuccess(success)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun submitRefreshRequest() {
        val request = BGAppRefreshTaskRequest(identifier = BG_REFRESH_IDENTIFIER)
        val ok = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        if (!ok) {
            // 模拟器恒 false（BGTask 不触发）+ 真机过早 submit 也会 false——只记日志不 crash
            BadgerLog.w(TAG, "BGAppRefreshTaskRequest submit 失败（模拟器不支持 / identifier 未注册）")
        }
    }

    companion object {
        /** 与 iosApp/Info.plist `BGTaskSchedulerPermittedIdentifiers` 保持一致。 */
        const val BG_REFRESH_IDENTIFIER = "top.mcxiafeng.badger.sync.refresh"
    }
}
