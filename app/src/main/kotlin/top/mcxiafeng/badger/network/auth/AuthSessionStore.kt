package top.mcxiafeng.badger.network.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import top.mcxiafeng.badger.utils.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 凭据与实例、账号、数据代次和登录地址绑定；旧调用方仍可读取 userUuid/username。 */
data class AuthSession(
    val userUuid: String,
    val token: String,
    val username: String = "",
    val syncVersion: Long = 0L,
    val lastSyncTime: Long = 0L,
    val serverId: String = "",
    val dataEpoch: String = "",
    val baseUrl: String = "",
    val expiresAt: String = "",
) {
    override fun toString(): String = "AuthSession[redacted]"

    internal fun isUsable(now: Instant = Instant.now()): Boolean = try {
        canonicalUuid(userUuid) && canonicalUuid(serverId) && canonicalUuid(dataEpoch) &&
            TOKEN_PATTERN.matches(token) && validBaseUrl(baseUrl) && Instant.parse(expiresAt).isAfter(now)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "会话绑定格式无效 (${e.javaClass.simpleName})")
        false
    } catch (e: DateTimeParseException) {
        Log.e(TAG, "会话有效期格式无效 (${e.javaClass.simpleName})")
        false
    }

    internal fun sameAccount(other: AuthSession): Boolean =
        serverId == other.serverId && userUuid == other.userUuid && dataEpoch == other.dataEpoch

    companion object {
        private const val TAG = "AuthSessionStoreTester"
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43}")

        internal fun canonicalUuid(value: String): Boolean =
            value.length == UUID_TEXT_LENGTH && UUID.fromString(value).toString() == value

        internal fun validBaseUrl(value: String): Boolean {
            val url = value.toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null
        }

        private const val UUID_TEXT_LENGTH = 36
    }
}

/** 生产凭据仅保存在 KeyStore 支持的加密偏好中，无法解密时不降级到明文。 */
@Singleton
class AuthSessionStore internal constructor(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(encryptedPrefs(context))

    private val _session = MutableStateFlow(read())
    val sessionFlow: StateFlow<AuthSession?> = _session.asStateFlow()

    @Synchronized
    fun read(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val stored = AuthSession(
            userUuid = prefs.getString(KEY_USER_UUID, "").orEmpty(),
            token = token,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            syncVersion = prefs.getLong(KEY_SYNC_VERSION, 0L),
            lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L),
            serverId = prefs.getString(KEY_SERVER_ID, "").orEmpty(),
            dataEpoch = prefs.getString(KEY_DATA_EPOCH, "").orEmpty(),
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            expiresAt = prefs.getString(KEY_EXPIRES_AT, "").orEmpty(),
        )
        return stored.takeIf { it.isUsable() }.also {
            if (it == null) Log.d(TAG, "会话已过期或缺少实例绑定，需要重新登录")
        }
    }

    /** 每次云请求只取一次；地址、token、身份和数据代次属于同一不可变快照。 */
    @Synchronized
    fun snapshot(): AuthSession? = read()

    @Synchronized
    fun save(session: AuthSession) {
        require(session.isUsable()) { "会话无效或缺少实例绑定" }
        val previous = read()
        val bound = if (previous?.sameAccount(session) == true) session
        else session.copy(syncVersion = 0L, lastSyncTime = 0L)
        check(prefs.edit()
            .putString(KEY_TOKEN, bound.token)
            .putString(KEY_USER_UUID, bound.userUuid)
            .putString(KEY_USERNAME, bound.username)
            .putLong(KEY_SYNC_VERSION, bound.syncVersion)
            .putLong(KEY_LAST_SYNC_TIME, bound.lastSyncTime)
            .putString(KEY_SERVER_ID, bound.serverId)
            .putString(KEY_DATA_EPOCH, bound.dataEpoch)
            .putString(KEY_BASE_URL, bound.baseUrl)
            .putString(KEY_EXPIRES_AT, bound.expiresAt)
            .commit()) { "会话保存失败" }
        _session.value = bound
        Log.d(TAG, "已保存绑定会话")
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().clear().commit()) { "会话清除失败" }
        _session.value = null
        Log.d(TAG, "会话已清空")
    }

    companion object {
        private const val TAG = "AuthSessionStoreTester"
        private const val PREFS_NAME = "auth_session_encrypted"
        private const val LEGACY_PREFS_NAME = "auth_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_USERNAME = "username"
        private const val KEY_SYNC_VERSION = "sync_version"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_DATA_EPOCH = "data_epoch"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_EXPIRES_AT = "expires_at"

        private fun encryptedPrefs(context: Context): SharedPreferences {
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                check(legacy.edit().clear().commit()) { "旧会话清除失败" }
                Log.d(TAG, "旧明文会话已失效，保留账号资料")
            }
            return EncryptedPrefs.open(context, PREFS_NAME)
        }
    }
}
