package top.mcxiafeng.badger.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * [K02 spike] iOS actual：Documents 目录 + bundled driver。
 * iOS 端是全新数据库（空库 + 服务端 pull bootstrap，见 KMP 计划 Q4），无迁移负担。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase> {
    return Room.databaseBuilder<SpikeDatabase>(iosDocumentsDbPath(name))
        .setDriver(BundledSQLiteDriver())
}

/**
 * [KMP K07] iOS Documents 目录路径（非泛型占位，K08 随 AppDatabase 迁 commonMain
 * 时以 expect object RoomDatabaseConstructor 模式落地具体库）。
 */
@OptIn(ExperimentalForeignApi::class)
fun iosDocumentsDbPath(name: String): String {
    val documentDirectory: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    ).let { requireNotNull(it) { "NSDocumentDirectory unavailable" } }
    return documentDirectory.path + "/" + name
}

actual object PlatformContextHolder {
    actual fun inject(context: Any) {
        // iOS 无 Application Context 概念，no-op
    }
}
