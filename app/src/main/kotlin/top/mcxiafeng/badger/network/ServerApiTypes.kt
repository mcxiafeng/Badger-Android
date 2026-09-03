package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Shared API models and small JSON helpers for the `/api` network surface. */

// [K04] Gson → kotlinx.serialization：DTO 全部 @Serializable（Outbox payload 编解码 + 双实现对照用）；
// 网络响应解析保留手写 from(JsonObject)（stringOrNull 等防御语义逐条平移，勿改行为）。

/** Auth endpoints return `data: { token, user? }`. */
@Serializable
data class AuthResponse(val token: String = "", val user: AuthUser? = null) {
    companion object {
        fun ofToken(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = null,
        )

        fun ofLogin(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = jsonObjectOrNull(o, "user")?.let { AuthUser.from(it) },
        )
    }
}

@Serializable
data class AuthUser(
    val uuid: String = "",
    val name: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
    val profile: JsonObject? = null,
    val lastLogin: String? = null,
    val createTime: String? = null,
) {
    companion object {
        fun from(o: JsonObject): AuthUser = AuthUser(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            displayName = stringOrNull(o, "displayName"),
            email = stringOrNull(o, "email"),
            isAdmin = boolOr(o["isAdmin"], false),
            profile = jsonObjectOrNull(o, "profile"),
            lastLogin = stringOrNull(o, "lastLogin"),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

@Serializable
data class RegisterPolicy(
    val allowRegister: Boolean = true,
    val requireCaptcha: Boolean = false,
    val requireEmailCode: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): RegisterPolicy = RegisterPolicy(
            allowRegister = boolOr(o["allowRegister"], true),
            requireCaptcha = boolOr(o["requireCaptcha"], false),
            requireEmailCode = boolOr(o["requireEmailCode"], false),
        )
    }
}

@Serializable
data class CaptchaResult(val captchaId: String = "", val code: String? = null) {
    companion object {
        fun from(o: JsonObject): CaptchaResult = CaptchaResult(
            captchaId = stringOrNull(o, "captchaId").orEmpty(),
            code = stringOrNull(o, "code"),
        )
    }
}

@Serializable
data class VerificationCodeResult(
    val captchaId: String = "",
    val code: String? = null,
    val emailSent: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): VerificationCodeResult = VerificationCodeResult(
            captchaId = stringOrNull(o, "captchaId").orEmpty(),
            code = stringOrNull(o, "code"),
            emailSent = boolOr(o["emailSent"], false),
        )
    }
}

@Serializable
data class TagCandidate(val name: String = "", val confidence: Float = 0.5f)

@Serializable
data class ExtractedContact(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val qq: String? = null,
    val wechat: String? = null,
    val bilibili: String? = null,
    val weibo: String? = null,
    val douyin: String? = null,
    val github: String? = null,
    val telegram: String? = null,
    val xiaohongshu: String? = null,
    val facebook: String? = null,
    val x: String? = null,
    val website: String? = null,
    val other: List<String> = emptyList(),
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
            other = jsonArrayOrNull(o, "other")?.mapNotNull { it.takeIfString() } ?: emptyList(),
        )
    }
}

class ApiException(val status: Int, val bodyText: String?, val what: String) :
    java.io.IOException("$what failed: HTTP $status  ${bodyText ?: ""}")

@Serializable
data class UserNotification(
    val uuid: String = "",
    val senderName: String = "",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createTime: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
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
                    read = boolOr(o["read"], false),
                    createTime = jsonTimeOrNull(o, "createTime"),
                    entityType = stringOrNull(o, "entityType"),
                    entityId = stringOrNull(o, "entityId"),
                )
            } catch (e: Exception) {
                Log.w("ServerApi", "notification parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class UserDevice(
    val uuid: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val ip: String? = null,
    val online: Boolean = false,
    val loginTime: String? = null,
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
                    online = boolOr(o["online"], false),
                    loginTime = jsonTimeOrNull(o, "loginTime"),
                )
            } catch (e: Exception) {
                Log.w("ServerApi", "device parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class UserStats(
    val persons: Int = 0,
    val personsDelta: Int = 0,
    val tags: Int = 0,
    val tagsDelta: Int = 0,
    val collections: Int = 0,
    val collectionsDelta: Int = 0,
    val storageBytes: Long = 0L,
    val recentPersons: List<RecentPerson> = emptyList(),
    val recentCollections: List<RecentCollection> = emptyList(),
) {
    companion object {
        fun parse(o: JsonObject): UserStats? {
            return try {
                UserStats(
                    persons = intOr(o["persons"], 0),
                    personsDelta = intOr(o["personsDelta"], 0),
                    tags = intOr(o["tags"], 0),
                    tagsDelta = intOr(o["tagsDelta"], 0),
                    collections = intOr(o["collections"], 0),
                    collectionsDelta = intOr(o["collectionsDelta"], 0),
                    storageBytes = longOr(o["storageBytes"], 0L),
                    recentPersons = jsonArrayOrNull(o, "recentPersons")?.mapNotNull { el ->
                        (el as? JsonObject)?.let { RecentPerson.parse(it) }
                    } ?: emptyList(),
                    recentCollections = jsonArrayOrNull(o, "recentCollections")?.mapNotNull { el ->
                        (el as? JsonObject)?.let { RecentCollection.parse(it) }
                    } ?: emptyList(),
                )
            } catch (e: Exception) {
                Log.w("ServerApi", "stats parse failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class RecentPerson(
    val uuid: String = "",
    val name: String = "",
    val avatarURL: String? = null,
    val description: String? = null,
    val createTime: String? = null,
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
                Log.w("ServerApi", "recentPerson parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class RecentCollection(
    val uuid: String = "",
    val name: String = "",
    val description: String? = null,
    val backgroundURL: String? = null,
    val memberCount: Int = 0,
    val createTime: String? = null,
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
                    memberCount = intOr(o["memberCount"], 0),
                    createTime = jsonTimeOrNull(o, "createTime"),
                )
            } catch (e: Exception) {
                Log.w("ServerApi", "recentCollection parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class UserSettings(
    val language: String? = null,
    val theme: String? = null,
    val notifyEmail: Boolean = false,
    val shortLinkProvider: String? = null,
    val shortioApiKeySet: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): UserSettings = try {
            UserSettings(
                language = stringOrNull(o, "language"),
                theme = stringOrNull(o, "theme"),
                notifyEmail = boolOr(o["notifyEmail"], false),
                shortLinkProvider = stringOrNull(o, "shortLinkProvider"),
                shortioApiKeySet = boolOr(o["shortioApiKeySet"], false),
            )
        } catch (e: Exception) {
            Log.w("ServerApi", "UserSettings parse skip: ${e.javaClass.simpleName}: ${e.message}")
            UserSettings(null, null, false, null, false)
        }
    }
}

@Serializable
data class ShortLinkConfig(
    val enabled: Boolean = false,
    val shortioEnabled: Boolean = false,
    val serverEnabled: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): ShortLinkConfig = try {
            ShortLinkConfig(
                enabled = boolOr(o["enabled"], false),
                shortioEnabled = boolOr(o["shortioEnabled"], false),
                serverEnabled = boolOr(o["serverEnabled"], false),
            )
        } catch (e: Exception) {
            Log.w("ServerApi", "ShortLinkConfig parse skip: ${e.javaClass.simpleName}: ${e.message}")
            ShortLinkConfig(false, false, false)
        }
    }
}

@Serializable
data class ServerShortLink(
    val uuid: String = "",
    val originalURL: String = "",
    val code: String? = null,
    val shortURL: String? = null,
    val createTime: String? = null,
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
                Log.w("ServerApi", "ServerShortLink parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class UserProfileResponse(
    val name: String? = null,
    val displayName: String? = null,
    val profile: ProfileDto? = null,
) {
    companion object {
        fun from(o: JsonObject): UserProfileResponse = try {
            UserProfileResponse(
                name = stringOrNull(o, "name"),
                displayName = stringOrNull(o, "displayName"),
                profile = jsonObjectOrNull(o, "profile")?.let { ProfileDto.from(it) },
            )
        } catch (e: Exception) {
            Log.w("ServerApi", "UserProfileResponse parse skip: ${e.javaClass.simpleName}: ${e.message}")
            UserProfileResponse(null, null, null)
        }
    }
}
