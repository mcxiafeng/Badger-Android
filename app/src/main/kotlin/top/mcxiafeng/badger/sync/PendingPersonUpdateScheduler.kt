package top.mcxiafeng.badger.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.queue.PendingPersonUpdateStore
import top.mcxiafeng.badger.network.ServerApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-lifetime worker for the durable person PUT outbox.
 *
 * The queue itself is durable; this scheduler only provides prompt replay while the
 * process is alive. Startup kicks the worker once, and every enqueue kicks it again.
 */
class PendingPersonUpdateScheduler(
    private val store: PendingPersonUpdateStore,
    private val apiProvider: () -> ServerApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun kick() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val updates = store.getReady()
                    if (updates.isEmpty()) break
                    var blocked = false
                    for (update in updates) {
                        try {
                            apiProvider().replayPendingPersonUpdate(update)
                            store.deleteIfRequest(update.serverId, update.requestId)
                        } catch (e: Exception) {
                            store.recordFailure(update.serverId, update.requestId, e)
                            blocked = true
                            break
                        }
                    }
                    if (blocked) break
                }
            } finally {
                running.set(false)
                if (store.hasReady()) kick()
            }
        }
    }
}
