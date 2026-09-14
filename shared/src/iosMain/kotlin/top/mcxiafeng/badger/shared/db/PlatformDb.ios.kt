package top.mcxiafeng.badger.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformSpikeDatabaseBuilder(name: String): RoomDatabase.Builder<SpikeDatabase> {
    return Room.databaseBuilder<SpikeDatabase>(iosDocumentsDbPath(name))
        .setDriver(BundledSQLiteDriver())
}

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
        
    }
}
