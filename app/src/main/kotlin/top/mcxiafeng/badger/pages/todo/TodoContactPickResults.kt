package top.mcxiafeng.badger.pages.todo

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Miuix Nav v1 has no result channel, so a full-page picker hands its picks back through this
 * process-local box keyed by a token the editor remembers. The editor consumes (and thereby clears)
 * the entry on resume; an unconsumed token from a discarded editor is dropped on the next publish.
 */
internal object TodoContactPickResults {
    private val results = ConcurrentHashMap<String, List<Long>>()

    fun publish(token: String, contacts: List<Long>) {
        Log.d(TAG, "Published contact picks; count=${contacts.size}")
        results[token] = contacts.distinct().filter { it > 0 }
    }

    /** Returns the picks and clears them so a second resume cannot apply them twice. */
    fun consume(token: String): List<Long>? = results.remove(token).also {
        if (it != null) Log.d(TAG, "Consumed contact picks; count=${it.size}")
    }

    fun discard(token: String) {
        results.remove(token)
    }

    private const val TAG = "TodoContactPickResults"
}
