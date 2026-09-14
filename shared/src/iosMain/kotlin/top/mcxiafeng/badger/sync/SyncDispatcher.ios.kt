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

    

    fun registerBackgroundTask() {
        val center = NSNotificationCenter.defaultCenter
        
        center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            BadgerLog.d(TAG, "willEnterForeground → kick()（前台主要重放窗口）")
            kick()
        }
        
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            BadgerLog.d(TAG, "didEnterBackground → submit BGAppRefreshTaskRequest")
            submitRefreshRequest()
        }
        
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
            
            submitRefreshRequest()
            refreshTask.setTaskCompletedWithSuccess(success)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun submitRefreshRequest() {
        val request = BGAppRefreshTaskRequest(identifier = BG_REFRESH_IDENTIFIER)
        val ok = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        if (!ok) {
            
            BadgerLog.w(TAG, "BGAppRefreshTaskRequest submit 失败（模拟器不支持 / identifier 未注册）")
        }
    }

    companion object {
        
        const val BG_REFRESH_IDENTIFIER = "top.mcxiafeng.badger.sync.refresh"
    }
}
