package top.mcxiafeng.badger.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException

/**
 * Shared data classes + top-level helpers extracted from the old monolithic
 * [ServerApi] during the [§15 #19] split. Kept at the top level of the
 * `network` package so cross-domain references (e.g. UI listing
 * backup rows from [BackupSummary]) remain unambiguous and call sites don't
 * have to qualify with `ServerApi.X`.
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

data class BackupSummary(val id: String, val name: String, val size: Long, val createdAt: String)
data class BackupUpload(val id: String, val name: String, val size: Long, val createdAt: String)

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
                )
            } catch (e: Exception) {
                android.util.Log.w("ServerApi", "notification parse skip: ${e.javaClass.simpleName}: ${e.message}")
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

/** [Phase 3] 提升为 internal：见 [stringOrNull]。 */
internal fun JsonElement.takeIfString(): String? =
    if (this.isJsonNull) null else this.asString?.takeIf { it.isNotBlank() }
