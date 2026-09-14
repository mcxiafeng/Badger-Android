package top.mcxiafeng.badger.sync

object OutboxReplayRegistry {

    
    data class ReplayOutcome(val pushedOps: Int, val failedOps: Int)

    @Volatile
    var pushOnceProvider: (suspend (includeBackoff: Boolean) -> ReplayOutcome)? = null

    fun requireProvider(): suspend (Boolean) -> ReplayOutcome =
        pushOnceProvider ?: error("OutboxReplayRegistry.pushOnceProvider not injected (BadgerApplication.onCreate)")
}
