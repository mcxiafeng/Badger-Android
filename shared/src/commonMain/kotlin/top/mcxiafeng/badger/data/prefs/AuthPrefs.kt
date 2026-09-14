package top.mcxiafeng.badger.data.prefs

const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

object AuthPrefs {
    
    
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_EMAIL = "email"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SELF_PERSON_ID = "self_person_id"

    

    private const val KEY_LAST_USER_ID = "last_user_id"

    fun readLastUserId(): String? =
        PrefsStore.readString(KEY_LAST_USER_ID)

    fun writeLastUserId(id: String) {
        PrefsStore.writeString(KEY_LAST_USER_ID, id)
    }

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

    

    fun readServerUrl(): String =
        PrefsStore.readString(KEY_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun writeServerUrl(url: String) {
        PrefsStore.writeString(KEY_SERVER_URL, url)
    }

    

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
