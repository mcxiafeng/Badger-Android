package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.utils.BadgerLog

private val dbWriteMutex = Mutex()

actual suspend fun <T> RoomDatabase.dbTransaction(block: suspend () -> T): T =
    dbWriteMutex.withLock {
        try {
            block()
        } catch (e: Throwable) {
            BadgerLog.w("DbTransaction.ios", "dbTransaction block 失败（iOS 无事务回滚，交由上层语义兜底）", e)
            throw e
        }
    }
