package top.mcxiafeng.badger.shared.prefs

import android.content.Context
import java.io.File

/**
 * [KMP K05] Android actual：DataStore 落 `context.filesDir/datastore/`。
 * BadgerApplication.onCreate 注入 filesDir，避免 expect 单例直接持 Context。
 */
actual object PrefsPathFactory {
    @Volatile
    private var appDir: String? = null

    fun inject(context: Context) {
        appDir = context.filesDir.absolutePath
    }

    actual fun storeAppDir(dir: String) {
        appDir = dir
    }

    actual fun prefsDir(): String {
        val base = appDir ?: "datastore"
        return File(base, "datastore").apply { mkdirs() }.absolutePath
    }
}
