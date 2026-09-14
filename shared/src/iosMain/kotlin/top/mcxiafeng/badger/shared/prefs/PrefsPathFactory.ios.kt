package top.mcxiafeng.badger.shared.prefs

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual object PrefsPathFactory {
    actual fun storeAppDir(dir: String) {
        
    }

    actual fun prefsDir(): String {
        @OptIn(ExperimentalForeignApi::class)
        val documentDir: NSURL = NSFileManager.defaultManager
            .URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )!!
        return documentDir.path + "/datastore"
    }
}
