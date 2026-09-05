package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [KMP K13b] iOS actual：写事务互斥（骨架）。
 *
 * Room 2.8.4 的 `withTransaction` 仅随 room-ktx 发布（无 iOS 变体），iOS 侧暂无
 * 官方 KMP 事务 API。当前以**单进程写互斥**（Mutex）替代：ContactWriter 三入口
 * 的多语句写操作串行化，杜绝并发交错；**中途失败不自动回滚**，完整性由上层
 * Outbox 重试语义兜底。
 *
 * 真机 SQLite 事务绑定验证 + Room KMP 事务 API 跟进（K17）。
 */
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
