package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase

/**
 * [KMP K13b] 跨端 Room 事务边界（ContactWriter / TagRepositoryImpl 的多语句写事务）。
 *
 * - Android actual：`RoomDatabase.withTransaction`（room-ktx，语义与迁移前完全一致）。
 * - iOS actual：`useWriterConnection` 独占写连接 + SQLite 手动 BEGIN/COMMIT——
 *   Room KMP 的写连接池为单连接串行，块内 suspend DAO 全部路由到同一连接，
 *   SQLite 事务按连接绑定，事务语义成立。
 *
 * block 内抛异常 → 整体回滚（ContactWriter 三入口的原子性契约）。
 */
expect suspend fun <T> RoomDatabase.dbTransaction(block: suspend () -> T): T
