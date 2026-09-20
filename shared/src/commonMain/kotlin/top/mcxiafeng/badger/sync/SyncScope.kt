package top.mcxiafeng.badger.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 全局同步协程域：push 重放、应用启动触发共用；进程级单例。 */
object SyncScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
