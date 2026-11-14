package top.mcxiafeng.badger.network.auth

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录态本地载体（对应 dev 分支 System 库 UserInfo 的角色）。
 * token 唯一来源；syncVersion/lastSyncTime 为未来同步引擎保留游标，登录时原样延续。
 *
 * 注意：SharedPreferences 为明文存储，与项目既有 ai_ocr_config 等同级已知安全限制。
 */
data class AuthSession(
    val userUuid: String,
    val token: String,
    val syncVersion: Long = 0L,
    val lastSyncTime: Long = 0L,
)

@Singleton
class AuthSessionStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userUuid = prefs.getString(KEY_USER_UUID, null) ?: return null
        return AuthSession(
            userUuid = userUuid,
            token = token,
            syncVersion = prefs.getLong(KEY_SYNC_VERSION, 0L),
            lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L),
        )
    }

    fun save(session: AuthSession) {
        Log.d(TAG, "会话写入 user=${session.userUuid}, 游标=${session.syncVersion}")
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_UUID, session.userUuid)
            .putLong(KEY_SYNC_VERSION, session.syncVersion)
            .putLong(KEY_LAST_SYNC_TIME, session.lastSyncTime)
            .apply()
    }

    fun clear() {
        Log.d(TAG, "会话已清空")
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "AuthSessionStoreTester"
        private const val PREFS_NAME = "auth_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_SYNC_VERSION = "sync_version"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }
}
