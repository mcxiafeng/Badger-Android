package top.mcxiafeng.badger.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException

/** Shared API models and small JSON helpers for the `/api` network surface. */

/** Auth endpoints return `data: { token, user? }`. */
data class AuthResponse(val token: String, val user: AuthUser?) {
    companion object {
        fun ofToken(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = null,
        )

        fun ofLogin(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = o.getAsJsonObject("user")?.let { AuthUser.from(it) },
        )
    }
}

data class AuthUser(
    val uuid: String,
    val name: String,
    val displayName: String?,
    val email: String?,
    val isAdmin: Boolean,
    val profile: JsonObject?,
    val lastLogin: String?,
    val createTime: String?,
) {
    companion object {
        fun from(o: JsonObject): AuthUser = AuthUser(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            displayName = stringOrNull(o, "displayName"),
            email = stringOrNull(o, "email"),
            isAdmin = o.get("isAdmin")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            profile = o.getAsJsonObject("profile"),
            lastLogin = stringOrNull(o, "lastLogin"),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

data class RegisterPolicy(
    val allowRegister: Boolean,
    val requireCaptcha: Boolean,
    val requireEmailCode: Boolean,
) {
    companion object {
        fun from(o: JsonObject): RegisterPolicy = RegisterPolicy(
            allowRegister = o.get("allowRegister")?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
            requireCaptcha = o.get("requireCaptcha")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            requireEmailCode = o.get("requireEmailCode")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
        )
    }
}

data class CaptchaResult(val captchaId: String, val code: String?) {
    companion object {
        fun from(o: JsonObject): CaptchaResult = CaptchaResult(
            captchaId = stringOrNull(o, "captchaId").orEmpty(),
            code = stringOrNull(o, "code"),
        )
    }
}

data class VerificationCodeResult(
    val captchaId: String,
    val code: String?,
    val emailSent: Boolean,
) {
    companion object {
        fun from(o: JsonObject): VerificationCodeResult = VerificationCodeResult(
            captchaId = stringOrNull(o, "captchaId").orEmpty(),
            code = stringOrNull(o, "code"),
            emailSent = o.get("emailSent")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
        )
    }
}

data class TagCandidate(val name: String, val confidence: Float)

data class ExtractedContact(
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String? = null,
    val qq: String?,
    val wechat: String?,
    val bilibili: String?,
    val weibo: String?,
    val douyin: String?,
    val github: String?,
    val telegram: String?,
    val xiaohongshu: String?,
    val facebook: String?,
    val x: String?,
    val website: String?,
    val other: List<String>,
) {
    companion object {
        fun from(o: JsonObject): ExtractedContact = ExtractedContact(
            name = stringOrNull(o, "name"),
            phone = stringOrNull(o, "phone"),
            email = stringOrNull(o, "email"),
            avatarUrl = stringOrNull(o, "avatar_url"),
            qq = stringOrNull(o, "qq"),
            wechat = stringOrNull(o, "wechat"),
            bilibili = stringOrNull(o, "bilibili"),
            weibo = stringOrNull(o, "weibo"),
            douyin = stringOrNull(o, "douyin"),
            github = stringOrNull(o, "github"),
            telegram = stringOrNull(o, "telegram"),
            xiaohongshu = stringOrNull(o, "xiaohongshu"),
            facebook = stringOrNull(o, "facebook"),
            x = stringOrNull(o, "x"),
            website = stringOrNull(o, "website"),
            other = o.getAsJsonArray("other")?.mapNotNull { it.takeIfString() } ?: emptyList(),
        )
    }
}

class ApiException(val status: Int, val bodyText: String?, val what: String) :
    IOException("$what failed: HTTP $status  ${bodyText ?: ""}")

data class UserNotification(
    val uuid: String,
    val senderName: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createTime: String?,
    val entityType: String?,
    val entityId: String?,
) {
    companion object {
        fun parse(o: JsonObject): UserNotification? {
            return try {
                val uuid = stringOrNull(o, "uuid") ?: return null
                UserNotification(
                    uuid = uuid,
                    senderName = stringOrNull(o, "senderName").orEmpty(),
                    title = stringOrNull(o, "title").orEmpty(),
                    body = stringOrNull(o, "body").orEmpty(),
                    read = o.get("read")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    createTime = jsonTimeOrNull(o, "createTime"),
                    entityType = stringOrNull(o, "entityType"),
                    entityId = stringOrNull(o, "entityId"),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "notification parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

data class UserDevice(
    val uuid: String,
    val deviceId: String,
    val deviceName: String,
    val ip: String?,
    val online: Boolean,
    val loginTime: String?,
) {
    companion object {
        fun parse(o: JsonObject): UserDevice? {
            return try {
                val uuid = stringOrNull(o, "uuid") ?: return null
                UserDevice(
                    uuid = uuid,
                    deviceId = stringOrNull(o, "deviceId").orEmpty(),
                    deviceName = stringOrNull(o, "deviceName").orEmpty(),
                    ip = stringOrNull(o, "ip"),
                    online = o.get("online")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    loginTime = jsonTimeOrNull(o, "loginTime"),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "device parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

data class UserStats(
    val persons: Int,
    val personsDelta: Int,
    val tags: Int,
    val tagsDelta: Int,
    val collections: Int,
    val collectionsDelta: Int,
    val storageBytes: Long,
    val recentPersons: List<RecentPerson>,
    val recentCollections: List<RecentCollection>,
) {
    companion object {
        fun parse(o: JsonObject): UserStats? {
            return try {
                UserStats(
                    persons = o.get("persons")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    personsDelta = o.get("personsDelta")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    tags = o.get("tags")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    tagsDelta = o.get("tagsDelta")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    collections = o.get("collections")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    collectionsDelta = o.get("collectionsDelta")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    storageBytes = o.get("storageBytes")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    recentPersons = o.getAsJsonArray("recentPersons")?.mapNotNull { el ->
                        val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        RecentPerson.parse(obj)
                    } ?: emptyList(),
                    recentCollections = o.getAsJsonArray("recentCollections")?.mapNotNull { el ->
                        val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        RecentCollection.parse(obj)
                    } ?: emptyList(),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "stats parse failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

data class RecentPerson(
    val uuid: String,
    val name: String,
    val avatarURL: String?,
    val description: String?,
    val createTime: String?,
) {
    companion object {
        fun parse(o: JsonObject): RecentPerson? {
            return try {
                val uuid = stringOrNull(o, "uuid") ?: return null
                RecentPerson(
                    uuid = uuid,
                    name = stringOrNull(o, "name").orEmpty(),
                    avatarURL = stringOrNull(o, "avatarURL"),
                    description = stringOrNull(o, "description"),
                    createTime = jsonTimeOrNull(o, "createTime"),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "recentPerson parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

data class RecentCollection(
    val uuid: String,
    val name: String,
    val description: String?,
    val backgroundURL: String?,
    val memberCount: Int,
    val createTime: String?,
) {
    companion object {
        fun parse(o: JsonObject): RecentCollection? {
            return try {
                val uuid = stringOrNull(o, "uuid") ?: return null
                RecentCollection(
                    uuid = uuid,
                    name = stringOrNull(o, "name").orEmpty(),
                    description = stringOrNull(o, "description"),
                    backgroundURL = stringOrNull(o, "backgroundURL"),
                    memberCount = o.get("memberCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    createTime = jsonTimeOrNull(o, "createTime"),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "recentCollection parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

internal fun jsonTimeOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull || !v.isJsonPrimitive) return null
    val p = v.asJsonPrimitive
    return when {
        p.isString -> p.asString.takeIf { it.isNotBlank() }
        p.isNumber -> p.asString
        else -> null
    }
}

internal fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    return v.takeIfString()
}

internal fun JsonElement.takeIfString(): String? {
    if (isJsonNull || !isJsonPrimitive) return null
    return asString?.takeIf { it.isNotBlank() }
}

data class UserSettings(
    val language: String?,
    val theme: String?,
    val notifyEmail: Boolean,
    val shortLinkProvider: String?,
    val shortioApiKeySet: Boolean,
) {
    companion object {
        fun from(o: JsonObject): UserSettings = try {
            UserSettings(
                language = stringOrNull(o, "language"),
                theme = stringOrNull(o, "theme"),
                notifyEmail = o.get("notifyEmail")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                shortLinkProvider = stringOrNull(o, "shortLinkProvider"),
                shortioApiKeySet = o.get("shortioApiKeySet")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            android.util.Log.w("ServerApi", "UserSettings parse skip: ${e.javaClass.simpleName}: ${e.message}")
            UserSettings(null, null, false, null, false)
        }
    }
}

data class ShortLinkConfig(
    val enabled: Boolean,
    val shortioEnabled: Boolean,
    val serverEnabled: Boolean,
) {
    companion object {
        fun from(o: JsonObject): ShortLinkConfig = try {
            ShortLinkConfig(
                enabled = o.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                shortioEnabled = o.get("shortioEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                serverEnabled = o.get("serverEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            android.util.Log.w("ServerApi", "ShortLinkConfig parse skip: ${e.javaClass.simpleName}: ${e.message}")
            ShortLinkConfig(false, false, false)
        }
    }
}

data class ServerShortLink(
    val uuid: String,
    val originalURL: String,
    val code: String?,
    val shortURL: String?,
    val createTime: String?,
) {
    companion object {
        fun from(o: JsonObject): ServerShortLink? {
            return try {
                val uuid = stringOrNull(o, "uuid") ?: return null
                ServerShortLink(
                    uuid = uuid,
                    originalURL = stringOrNull(o, "originalURL").orEmpty(),
                    code = stringOrNull(o, "code"),
                    shortURL = stringOrNull(o, "shortURL"),
                    createTime = stringOrNull(o, "createTime"),
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "ServerShortLink parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

data class UserProfileResponse(
    val name: String?,
    val displayName: String?,
    val profile: ProfileDto?,
) {
    companion object {
        fun from(o: JsonObject): UserProfileResponse = try {
            UserProfileResponse(
                name = stringOrNull(o, "name"),
                displayName = stringOrNull(o, "displayName"),
                profile = o.getAsJsonObject("profile")?.let { ProfileDto.from(it) },
            )
        } catch (e: Exception) {
            android.util.Log.w("ServerApi", "UserProfileResponse parse skip: ${e.javaClass.simpleName}: ${e.message}")
            UserProfileResponse(null, null, null)
        }
    }
}
