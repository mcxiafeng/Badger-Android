package top.mcxiafeng.badger.data.account

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import java.util.WeakHashMap

/** Room caches each DAO per database, so all ViewModels of a space share its write lock. */
internal object AccountWriteLocks {
    private val locks = WeakHashMap<Any, Mutex>()

    @Synchronized
    fun forDao(dao: Any): Mutex = locks.getOrPut(dao) {
        Log.d("AccountWriteLocksTester", "Created shared database DAO write lock")
        Mutex()
    }
}
