package top.mcxiafeng.badger.shared.prefs

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * [KMP K05] iOS actual：DataStore 落 NSDocumentDirectory/datastore/。
 */
actual object PrefsPathFactory {
    actual fun storeAppDir(dir: String) {
        // iOS 不需要注入（NSDocumentDirectory 直接可查），保留空实现对齐签名
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
