package top.mcxiafeng.badger.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException

/**
 * Shared data classes + top-level helpers extracted from the old monolithic
 * [ServerApi] during the [§15 #19] split. Kept at the top level of the
 * `network` package so cross-domain references remain unambiguous and call
 * sites don't have to qualify with `ServerApi.X`.
 */

/**
 * Auth 端点统一响应外壳（新 Java `/api` 契约）。
 *
 * 新契约 login 返回 `data: { token, user: {uuid,name,displayName,email,isAdmin,profile,lastLogin,createTime} }`；
 * refresh 只返回 `data: { token }`（[user] 为 null）。旧的 `expiresIn/role/username` 顶层字段已退役，
 * 权限位由 [AuthUser.isAdmin] 承担。
 */
data class AuthResponse(val token: String, val user: AuthUser?) {
    companion object {
        /** 仅解析 `data.token`（refresh 端点）。 */
        fun ofToken(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = null,
        )

        /** 解析 `data.token` + `data.user`（login 端点）。 */
        fun ofLogin(o: JsonObject): AuthResponse = AuthResponse(
            token = stringOrNull(o, "token").orEmpty(),
            user = o.getAsJsonObject("user")?.let { AuthUser.from(it) },
        )
    }
}

/** 登录/me 返回的当前用户脱敏信息。字段名一律 camelCase（服务端 [Profile] 嵌套对象原样透传）。 */
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

/** `GET /api/auth/registerPolicy` — 注册策略公开查询（注册页据此渲染/隐藏验证码）。 */
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

/** `GET /api/auth/getCaptcha` — 图形验证码；dev 下发明文 [code] 供前端渲染。 */
data class CaptchaResult(val captchaId: String, val code: String?) {
    companion object {
        fun from(o: JsonObject): CaptchaResult = CaptchaResult(
            captchaId = stringOrNull(o, "captchaId").orEmpty(),
            code = stringOrNull(o, "code"),
        )
    }
}

/** `POST /api/auth/sendVerificationCode` — SMTP 启用时 [emailSent]=true 且 [code]=null；dev 回退时明文下发。 */
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

/** Single tag candidate from the server `/v1/proxy/ai/tasks/tag_generate`. */
data class TagCandidate(val name: String, val confidence: Float)

/**
 * Mirror of the server's contact-OCR schema. All contact fields are nullable
 * because the upstream LLM may legitimately return null. [other] is a list
 * of leftover strings that didn't fit any typed column.
 */
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

/** Thrown when the server returns a non-2xx response. */
class ApiException(val status: Int, val bodyText: String?, val what: String) :
    IOException("$what failed: HTTP $status  ${bodyText ?: ""}")

/**
 * [B1] 站内通知行（`GET /api/user/notifications` 单条）。
 *
 * 字段名 camelCase，与服务端 `UserModule.notificationRow` 对齐。
 * 不叫 `Notification`，避免与 `android.app.Notification` 撞名。
 */
data class UserNotification(
    val uuid: String,
    val senderName: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createTime: String?,
    /** 关联实体类型：`"person"` / `"tag"` / `"collection"` / `null`（服务端未提供或未知类型）。 */
    val entityType: String?,
    /** 关联实体 UUID（person/tag/collection 的 uuid）；仅当 [entityType] 有效时有意义。 */
    val entityId: String?,
) {
    companion object {
        /**
         * 解析单条；缺 uuid → null。
         * [修复防御]: 整条 try/catch —— 字段类型异常时跳过该行，不炸整批（有日志，不吞根因）。
         */
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

/**
 * [B3] 设备行（`GET /api/user/devices` 单条）。
 *
 * 字段名 camelCase，与服务端 `UserModule.deviceRow` 对齐。
 * 不叫 `Device`，避免与 `android.hardware.Device` 撞名。
 */
data class UserDevice(
    val uuid: String,
    val deviceId: String,
    val deviceName: String,
    val ip: String?,
    val online: Boolean,
    val loginTime: String?,
) {
    companion object {
        /**
         * 解析单条；缺 uuid → null。
         * [修复防御]: 整条 try/catch —— 字段类型异常时跳过该行，不炸整批（有日志，不吞根因）。
         */
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

/**
 * [C1] Dashboard 统计概览（`GET /api/user/stats` data 对象）。
 *
 * 字段名 camelCase，与服务端对齐。
 * [recentPersons] 为最近添加的联系人摘要（最多 N 条），用于 Dashboard 横向滚动列表。
 */
/**
 * [C1] Dashboard 统计概览（`GET /api/user/stats` data 对象）。
 *
 * 字段名与服务端 `UserModule.statsRow` 对齐：
 * - `persons/tags/collections` 为当前计数，`*Delta` 为近期增减量；
 * - `storageBytes` 为用户已用存储字节数；
 * - `recentPersons/recentCollections` 为最近添加摘要（最多 N 条）。
 */
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

/**
 * 最近添加的联系人摘要（Dashboard 横向滚动列表项）。
 */
/**
 * 最近添加的联系人摘要（Dashboard 横向滚动列表项）。
 * 字段名与服务端对齐：`avatarURL`（大写 URL）。
 */
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

/**
 * 最近添加的名片夹摘要（Dashboard 横向滚动列表项）。
 * 服务端 `GET /api/user/stats` 的 `recentCollections` 字段。
 */
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

/** createTime 可能是 ISO 字符串或 epoch millis（fastjson Date）。 */
internal fun jsonTimeOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    if (!v.isJsonPrimitive) return null
    val p = v.asJsonPrimitive
    return when {
        p.isString -> p.asString.takeIf { it.isNotBlank() }
        p.isNumber -> p.asString
        else -> null
    }
}

/** [Phase 3] 提升为 internal：PersonApi/V2DomainApi/SyncApi 等新契约 API 共用此解析器。 */
internal fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    return v.takeIfString()
}

/** [C4] 解析 JSON 数字字段为 Long；非数字 / null → null。 */
internal fun longOrNull(o: JsonObject, key: String): Long? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    if (!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) return null
    return runCatching { v.asLong }.getOrNull()
}

/** [Phase 3] 提升为 internal：见 [stringOrNull]。 */
internal fun JsonElement.takeIfString(): String? =
    if (this.isJsonNull) null else this.asString?.takeIf { it.isNotBlank() }

/**
 * 用户个人设置（`GET /api/user/getSettings`）。
 *
 * 字段名 camelCase，与服务端 `UserModule.getSettings` 对齐。
 * `shortioApiKeySet` 为布尔（密钥绝不明文回传）。
 */
data class UserSettings(
    val language: String?,
    val theme: String?,
    val notifyEmail: Boolean,
    val shortLinkProvider: String?,
    val shortioApiKeySet: Boolean,
) {
    companion object {
        /**
         * [修复防御]: 整条 try/catch —— 字段类型异常时返回默认值，不炸整批（有日志，不吞根因）。
         */
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

/**
 * 短链配置快照（`GET /api/shortlinks/config`）。
 *
 * 功能开关 + 用户选择 + key 是否已设。
 */
data class ShortLinkConfig(
    val enabled: Boolean,
    val shortioEnabled: Boolean,
    val serverEnabled: Boolean,
) {
    companion object {
        /**
         * [修复防御]: 整条 try/catch —— 字段类型异常时返回默认值，不炸整批（有日志，不吞根因）。
         */
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

/**
 * 自建短链行（`GET /api/shortlinks/` 单条）。
 *
 * 字段名 camelCase，与服务端 `ShortLinkModule.shortLinkRow` 对齐。
 */
data class ServerShortLink(
    val uuid: String,
    val originalURL: String,
    val code: String?,
    val shortURL: String?,
    val createTime: String?,
) {
    companion object {
        /**
         * [修复防御]: 整条 try/catch —— 字段类型异常时跳过该行，不炸整批（有日志，不吞根因）。
         */
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

/**
 * selfPerson 资料（`GET /api/user/profile`）。
 *
 * 字段名 camelCase，与服务端 `UserModule.getProfile` 对齐。
 * 与 [PersonDto] 同构但不含 uuid/createTime/updateTime/self。
 */
data class UserProfileResponse(
    val name: String?,
    val displayName: String?,
    val profile: ProfileDto?,
) {
    companion object {
        /**
         * [修复防御]: 整条 try/catch —— 字段类型异常时返回空壳，不炸整批（有日志，不吞根因）。
         */
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
