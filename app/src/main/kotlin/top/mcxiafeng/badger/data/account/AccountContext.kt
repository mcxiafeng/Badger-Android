package top.mcxiafeng.badger.data.account

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountScopedContext

/** Fixed scope wrapper. Device/UI preferences remain device-wide; legacy local paths stay intact. */
class AccountContext(base: Context, val scope: AccountScope) : ContextWrapper(base) {
    init {
        require(SAFE_SPACE_KEY.matches(scope.key)) { "Invalid account space key" }
        Log.d(TAG, "Created fixed business context; local=${scope.isLocal}")
    }

    override fun getApplicationContext(): Context =
        if (baseContext.applicationContext === baseContext) this
        else AccountContext(baseContext.applicationContext, scope)

    // Context's default callback methods delegate through applicationContext. This wrapper is
    // its own applicationContext, so system callbacks must go straight to the real base context.
    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
        baseContext.registerComponentCallbacks(callback)
        Log.d(TAG, "Delegated application component callback registration")
    }

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
        baseContext.unregisterComponentCallbacks(callback)
        Log.d(TAG, "Delegated application component callback removal")
    }

    override fun getFilesDir(): File = directory(baseContext.filesDir)
    override fun getCacheDir(): File = directory(baseContext.cacheDir)
    override fun getNoBackupFilesDir(): File = directory(baseContext.noBackupFilesDir)

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return baseContext.getSharedPreferences(resolvePrefsName(scope, name), mode)
    }

    private fun directory(root: File): File {
        if (scope.isLocal) return root
        return File(root, "accounts/${scope.key}").also { directory ->
            check(directory.isDirectory || directory.mkdirs()) { "Could not create account directory" }
        }
    }

    companion object {
        private const val TAG = "AccountContextTester"
        private val SAFE_SPACE_KEY = Regex("(?:local_[a-zA-Z0-9-]+|remote_[a-f0-9]{64})")
        val BUSINESS_PREFERENCES = setOf(
            "ai_ocr_config", "ai_ocr_credentials", "short_link_settings", "short_link_credentials", "social_prefs",
        )

        fun find(context: Context): AccountContext? {
            var candidate = context
            while (candidate is ContextWrapper) {
                if (candidate is AccountContext) return candidate
                candidate = candidate.baseContext
            }
            return null
        }

        /** 账号专属偏好文件的物理文件名；EncryptedPrefs 的实例缓存键依赖同一映射，必须与 getSharedPreferences 保持一致。 */
        fun resolvePrefsName(scope: AccountScope?, name: String): String =
            if (scope != null && !scope.isLocal && name in BUSINESS_PREFERENCES) "${scope.key}_$name" else name
    }
}
