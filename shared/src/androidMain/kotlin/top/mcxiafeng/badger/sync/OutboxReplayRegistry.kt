package top.mcxiafeng.badger.sync

/**
 * [KMP K09] Outbox 重放回调注册表（androidMain）。
 *
 * OutboxWorker（shared androidMain）与 SyncEngine（app 源集，依赖 repository 链）的
 * 依赖解耦点：BadgerApplication 启动时注入 `SyncEngine::pushOnce`，Worker 消费时经
 * [pushOnceProvider] 取回调。Koin 不进 shared androidMain。
 */
object OutboxReplayRegistry {

    /** 一次重放的量化结果（对齐 SyncEngine.PushOutcome 的 Worker 消费字段）。 */
    data class ReplayOutcome(val pushedOps: Int, val failedOps: Int)

    @Volatile
    var pushOnceProvider: (suspend (includeBackoff: Boolean) -> ReplayOutcome)? = null

    fun requireProvider(): suspend (Boolean) -> ReplayOutcome =
        pushOnceProvider ?: error("OutboxReplayRegistry.pushOnceProvider not injected (BadgerApplication.onCreate)")
}
