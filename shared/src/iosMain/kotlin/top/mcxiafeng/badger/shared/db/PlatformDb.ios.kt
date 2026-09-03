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
    val documentDirectory: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    ).let { requireNotNull(it) { "NSDocumentDirectory unavailable" } }
    val dbFilePath = documentDirectory.path + "/" + name
    return Room.databaseBuilder<SpikeDatabase>(dbFilePath)
        .setDriver(BundledSQLiteDriver())
}
