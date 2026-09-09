package top.mcxiafeng.badger.data.prefs

/**
 * Lightweight prefs for the on-server auth system. Kept separate from
 * OnboardingPrefs so an OAuth refresh-token revocation doesn't wipe the
 * onboarding flag (and vice versa).
 *
 * [KMP K05] Storage: DataStore Preferences（经 PrefsStore 内存缓存，读同步语义不变）。
 * The refresh token is short-lived (≤ 7d by server default) and the access
 * token never touches disk — it lives in memory inside the ServerApi's
 * `TokenHolder` and is rewritten on every refresh.
 */
/** 默认开发服务器地址（Android 模拟器 10.0.2.2 语义指向宿主机）。全工程唯一来源。 */
const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

object AuthPrefs {
    // [Phase 2] 旧契约只有 access token（存 refresh_token 键）；
    // 新 Java /api 契约登录返回 user{uuid,name,displayName,email,isAdmin}，这里补本地缓存。
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_EMAIL = "email"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SELF_PERSON_ID = "self_person_id"

    fun readRefreshToken(): String? =
        PrefsStore.readString(KEY_REFRESH)

    fun writeRefreshToken(token: String) {
        PrefsStore.writeString(KEY_REFRESH, token)
    }

    fun readUserId(): String? =
        PrefsStore.readString(KEY_USER_ID)

    fun writeUserId(id: String) {
        PrefsStore.writeString(KEY_USER_ID, id)
    }

    fun readUsername(): String? =
        PrefsStore.readString(KEY_USERNAME)

    fun writeUsername(name: String) {
        PrefsStore.writeString(KEY_USERNAME, name)
    }

    // ---- [Phase 2] 新契约 user 字段缓存 ----

    fun readDisplayName(): String? =
        PrefsStore.readString(KEY_DISPLAY_NAME)

    fun writeDisplayName(name: String) {
        PrefsStore.writeString(KEY_DISPLAY_NAME, name)
    }

    fun readEmail(): String? =
        PrefsStore.readString(KEY_EMAIL)

    fun writeEmail(email: String) {
        PrefsStore.writeString(KEY_EMAIL, email)
    }

    fun readIsAdmin(): Boolean =
        PrefsStore.readBoolean(KEY_IS_ADMIN, false)

    fun writeIsAdmin(isAdmin: Boolean) {
        PrefsStore.writeBoolean(KEY_IS_ADMIN, isAdmin)
    }

    /**
     * Returns the base URL the client should talk to. Defaults to a dev
     * emulator-style address (`http://10.0.2.2:8080` reaches the host machine
     * from an Android emulator).
     */
    fun readServerUrl(): String =
        PrefsStore.readString(KEY_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun writeServerUrl(url: String) {
        PrefsStore.writeString(KEY_SERVER_URL, url)
    }

    /**
     * 自己的 Person uuid（serverId）。来源：login//me/GET /profile 响应、sync ADD 快照
     * 的 self=true 标记。SyncEngine 据此把 selfPerson 事件路由到"我的名片"，
     * 而不是当普通联系人写进 contacts_cache（历史上"自己"混进联系人列表的根因）。
     */
    fun readSelfPersonId(): String? =
        PrefsStore.readString(KEY_SELF_PERSON_ID)?.takeIf { it.isNotBlank() }

    fun writeSelfPersonId(uuid: String?) {
        if (uuid.isNullOrBlank()) {
            PrefsStore.remove(KEY_SELF_PERSON_ID)
        } else {
            PrefsStore.writeString(KEY_SELF_PERSON_ID, uuid)
        }
    }

    fun clearAuth() {
        listOf(
            KEY_REFRESH, KEY_USER_ID, KEY_USERNAME,
            KEY_DISPLAY_NAME, KEY_EMAIL, KEY_IS_ADMIN, KEY_SELF_PERSON_ID,
        ).forEach { PrefsStore.remove(it) }
    }
}
