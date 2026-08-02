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
 *
 * [ConflictException] is also defined here so [PendingUploadExecutor] and
 * other call sites that catch the exception directly don't need to import
 * an internal type.
 */

/** 联系人 CRUD 响应外壳：服务端 S1 约定 `version` 是资源当前版本号，客户端下次 PATCH 用 `If-Match: <version>`。[serverId] 在创建时由服务端分配。 */
data class ContactResponse(
    val id: String,
    val serverId: String?,
    val version: Long,
    val contact: JsonObject,
) {
    companion object {
        fun from(o: JsonObject): ContactResponse = ContactResponse(
            id = o.get("id")?.asString ?: "",
            serverId = o.get("server_id")?.takeIf { !it.isJsonNull }?.asString,
            version = o.get("version")?.asLong ?: 0L,
            contact = o.getAsJsonObject("contact") ?: JsonObject(),
        )
    }
}

/** 409 Conflict 响应体（S3 协议）：服务端返回当前权威版本，客户端选择"采用本地"或"采用服务端"。 */
data class ConflictResponse(val serverVersion: Long, val serverContact: JsonObject?) {
    companion object {
        fun from(o: JsonObject): ConflictResponse = ConflictResponse(
            serverVersion = o.get("server_version")?.asLong ?: 0L,
            serverContact = o.getAsJsonObject("contact"),
        )
    }
}

/**
 * 捕获 409 Conflict 响应：与 [ApiException] 同源但携带结构化 [ConflictResponse]。
 * 由 [PendingUploadExecutor] 在捕获后做"采用本地 / 采用服务端"决策。
 */
class ConflictException(val conflict: ConflictResponse, what: String) :
    IOException("$what failed: HTTP 409  ${conflict.serverContact?.toString() ?: ""}")

/**
 * GET /v1/contacts?since=<cursor>&limit=<n> 增量拉取结果。
 * [items] 是服务端权威版本，客户端按 serverId → 本地 id 映射替换 cache。
 */
data class ContactPage(val items: List<ContactResponse>, val nextSince: Long)

/** Auth 端点统一响应外壳 — token / expiresIn / role / username。 */
data class AuthResponse(val token: String, val expiresIn: Int, val role: String?, val username: String?)

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

private fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    return v.takeIfString()
}

private fun JsonElement.takeIfString(): String? =
    if (this.isJsonNull) null else this.asString?.takeIf { it.isNotBlank() }
