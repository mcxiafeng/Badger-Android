package top.mcxiafeng.badger.shared.db

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * [K02 spike] Room KMP databaseBuilder 的平台边界（expect/actual）。
 * K07 接入真实 AppDatabase 时沿用此模式：Android 走 Context 文件路径，iOS 走 Documents 目录。
 */
expect fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase>

/**
 * [KMP K07] 通用平台数据库 builder 工厂（expect）。
 *
 * Android actual：Room.databaseBuilder(context, klass, name) + BundledSQLiteDriver，
 * 路径 = context.getDatabasePath(name)，与旧库完全同一文件。
 * iOS actual：NSDocumentDirectory/name + BundledSQLiteDriver。
 */
// [KMP K07→K08 注] 泛型 expect builder 在 iOS 侧需要 reified（Room.databaseBuilder(name)
// 经 reified T 找 RoomDatabaseConstructor），actual 不能是 inline——故 K07 阶段 Android 侧
// 直接走 SpikeContextHolder + Room.databaseBuilder（bundled driver）；iOS 侧 K08 把
// AppDatabase 迁入 commonMain 后用 expect object AppDatabaseConstructor 模式落地。
// 本 expect 仅保留 Context 注入口。

/**
 * [KMP K07] 平台 Context 注入口（Android actual 由 Application 启动期调用）。
 * iOS/JVM 为 no-op。
 */
expect object PlatformContextHolder {
    fun inject(context: Any)
}
