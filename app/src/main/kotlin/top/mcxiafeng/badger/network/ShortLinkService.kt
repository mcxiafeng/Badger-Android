package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import top.mcxiafeng.badger.utils.HttpUtil
import androidx.core.content.edit

/**
 * short.io 域名信息
 */
data class ShortIoDomain(
    val id: Long,
    val hostname: String
)

/**
 * short.io 链接信息
 */
data class ShortIoLink(
    val idString: String,
    val path: String,
    val shortURL: String,
    val originalURL: String
)

/**
 * NFC 标签短链接管理服务
 *
 * 负责与短链接平台（默认 short.io）交互：
 * - 选择已有短链接（用户在 short.io 后台创建的）
 * - 更新短链接目标地址（切换平台时）
 *
 * 设置流程：API Key → 自动拉取域名 → 选择域名 → 自动拉取链接 → 选择链接
 * 高级设置支持自定义 API 端点（用于其他短链接平台）
 */
object ShortLinkService {

    private const val TAG = "ShortLinkService"
    private const val PREFS_NAME = "short_link_settings"
    private const val SHORT_IO_BASE = "https://api.shortio.cn"

    // --- SharedPreferences Keys ---

    // 基础设置
    private const val KEY_API_KEY = "api_key"
    private const val KEY_DOMAIN = "domain"
    private const val KEY_DOMAIN_ID = "domain_id"
    private const val KEY_LINK_ID = "link_id"
    private const val KEY_SHORT_PATH = "short_path"

    // 高级设置
    private const val KEY_CUSTOM_ENABLED = "custom_enabled"
    private const val KEY_API_URL = "api_url"
    private const val KEY_UPDATE_PATH = "update_path"
    private const val KEY_API_METHOD = "api_method"
    private const val KEY_AUTH_HEADER = "auth_header"
    private const val KEY_AUTH_PREFIX = "auth_prefix"
    private const val KEY_UPDATE_BODY = "update_body"

    // --- 设置读写 ---

    fun getApiKey(ctx: Context) = prefs(ctx).getString(KEY_API_KEY, "") ?: ""
    fun getDomain(ctx: Context) = prefs(ctx).getString(KEY_DOMAIN, "") ?: ""
    fun getDomainId(ctx: Context) = prefs(ctx).getLong(KEY_DOMAIN_ID, 0L)
    fun getLinkId(ctx: Context) = prefs(ctx).getString(KEY_LINK_ID, "") ?: ""
    fun getShortPath(ctx: Context) = prefs(ctx).getString(KEY_SHORT_PATH, "") ?: ""

    fun isCustomEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_CUSTOM_ENABLED, false)
    fun getApiUrl(ctx: Context) = prefs(ctx).getString(KEY_API_URL, "") ?: ""
    fun getUpdatePath(ctx: Context) = prefs(ctx).getString(KEY_UPDATE_PATH, "/links/{linkId}") ?: ""
    fun getApiMethod(ctx: Context) = prefs(ctx).getString(KEY_API_METHOD, "POST") ?: "POST"
    fun getAuthHeader(ctx: Context) = prefs(ctx).getString(KEY_AUTH_HEADER, "Authorization") ?: "Authorization"
    fun getAuthPrefix(ctx: Context) = prefs(ctx).getString(KEY_AUTH_PREFIX, "Bearer ") ?: "Bearer "
    fun getUpdateBody(ctx: Context) = prefs(ctx).getString(KEY_UPDATE_BODY, """{"originalURL":"{url}"}""") ?: ""

    fun saveDomainSelection(ctx: Context, domain: ShortIoDomain) {
        prefs(ctx).edit {
            putString(KEY_DOMAIN, domain.hostname)
                .putLong(KEY_DOMAIN_ID, domain.id)
        }
    }

    fun saveLinkSelection(ctx: Context, link: ShortIoLink) {
        prefs(ctx).edit {
            putString(KEY_LINK_ID, link.idString)
                .putString(KEY_SHORT_PATH, link.path)
        }
    }

    fun saveApiKey(ctx: Context, apiKey: String) {
        prefs(ctx).edit { putString(KEY_API_KEY, apiKey) }
    }

    fun saveAdvancedSettings(
        ctx: Context, enabled: Boolean, apiUrl: String,
        updatePath: String, method: String, authHeader: String, authPrefix: String,
        updateBody: String
    ) {
        prefs(ctx).edit {
            putBoolean(KEY_CUSTOM_ENABLED, enabled)
                .putString(KEY_API_URL, apiUrl)
                .putString(KEY_UPDATE_PATH, updatePath)
                .putString(KEY_API_METHOD, method)
                .putString(KEY_AUTH_HEADER, authHeader)
                .putString(KEY_AUTH_PREFIX, authPrefix)
                .putString(KEY_UPDATE_BODY, updateBody)
        }
    }

    /** 是否已完成配置（有 API Key、域名和链接） */
    fun isConfigured(ctx: Context): Boolean {
        return getApiKey(ctx).isNotBlank()
                && getDomain(ctx).isNotBlank()
                && getLinkId(ctx).isNotBlank()
    }

    /** 获取完整短链接 URL */
    fun getShortUrl(ctx: Context): String? {
        val domain = getDomain(ctx)
        val path = getShortPath(ctx)
        if (domain.isBlank() || path.isBlank()) return null
        val d = if (domain.startsWith("http")) domain else "https://$domain"
        return "$d/$path"
    }

    // --- API 查询 ---

    /**
     * 从 short.io API 获取用户已添加的域名列表。
     */
    suspend fun fetchDomains(ctx: Context): Result<List<ShortIoDomain>> {
        val apiKey = getApiKey(ctx)
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("请先设置 API Key"))
        return try {
            val response = HttpUtil.get(
                "$SHORT_IO_BASE/api/domains",
                headers = mapOf("Authorization" to apiKey)
            )
            if (response != null) {
                val array = JsonParser.parseString(response).asJsonArray
                val domains = array.mapNotNull { elem ->
                    val obj = elem.asJsonObject ?: return@mapNotNull null
                    val id = obj.get("id")?.asLong ?: return@mapNotNull null
                    val hostname = obj.get("hostname")?.asString ?: return@mapNotNull null
                    ShortIoDomain(id, hostname)
                }
                Result.success(domains)
            } else {
                Result.failure(IllegalStateException("获取域名列表失败，请检查 API Key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从 short.io API 获取指定域名下的短链接列表。
     * limit 必须 < 150。
     */
    suspend fun fetchLinks(ctx: Context, domainId: Long): Result<List<ShortIoLink>> {
        val apiKey = getApiKey(ctx)
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("请先设置 API Key"))
        return try {
            val response = HttpUtil.get(
                "$SHORT_IO_BASE/api/links?domain_id=$domainId&limit=50",
                headers = mapOf("Authorization" to apiKey)
            )
            if (response != null) {
                val json = JsonParser.parseString(response).asJsonObject
                val linksArray = json.getAsJsonArray("links")
                val links = linksArray.mapNotNull { elem ->
                    val obj = elem.asJsonObject ?: return@mapNotNull null
                    val idString = obj.get("idString")?.asString ?: obj.get("id")?.asString ?: return@mapNotNull null
                    val path = obj.get("path")?.asString ?: return@mapNotNull null
                    val shortURL = obj.get("secureShortURL")?.asString
                        ?: obj.get("shortURL")?.asString ?: ""
                    val originalURL = obj.get("originalURL")?.asString ?: ""
                    ShortIoLink(idString, path, shortURL, originalURL)
                }
                Result.success(links)
            } else {
                Result.failure(IllegalStateException("获取链接列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- API 操作 ---

    /**
     * 更新已有短链接的目标地址，返回短链接 URL。
     */
    suspend fun updateLinkDestination(ctx: Context, newUrl: String): Result<String> {
        return if (isCustomEnabled(ctx)) updateCustomLink(ctx, newUrl)
        else updateShortIoLink(ctx, newUrl)
    }

    /**
     * 获取指定短链接的详情（包括 originalURL）。
     * 使用 GET /links/{linkId} 端点。
     */
    suspend fun fetchLinkDetails(ctx: Context): Result<ShortIoLink> {
        val linkId = getLinkId(ctx)
        if (linkId.isBlank()) return Result.failure(IllegalStateException("未选择短链接"))
        return if (isCustomEnabled(ctx)) fetchCustomLinkDetails()
        else fetchShortIoLinkDetails(ctx, linkId)
    }

    private suspend fun fetchShortIoLinkDetails(ctx: Context, linkId: String): Result<ShortIoLink> {
        val apiKey = getApiKey(ctx)
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("请先设置 API Key"))
        return try {
            val response = HttpUtil.get(
                "$SHORT_IO_BASE/links/$linkId",
                headers = mapOf("Authorization" to apiKey)
            )
            if (response != null) {
                val json = JsonParser.parseString(response).asJsonObject
                val idString = json.get("idString")?.asString ?: json.get("id")?.asString ?: ""
                val path = json.get("path")?.asString ?: ""
                val shortURL = json.get("secureShortURL")?.asString ?: json.get("shortURL")?.asString ?: ""
                val originalURL = json.get("originalURL")?.asString ?: ""
                Result.success(ShortIoLink(idString, path, shortURL, originalURL))
            } else {
                Result.failure(IllegalStateException("获取链接详情失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchCustomLinkDetails(): Result<ShortIoLink> {
        // 自定义 API 通常没有获取详情端点，返回失败
        return Result.failure(IllegalStateException("自定义 API 不支持获取链接详情"))
    }

    // --- short.io 实现 ---

    /**
     * 创建新短链接，返回 ShortIoLink 或失败。
     */
    suspend fun createShortIoLink(ctx: Context, originalUrl: String): Result<ShortIoLink> {
        val domain = getDomain(ctx)
        if (domain.isBlank()) return Result.failure(IllegalStateException("请先选择域名"))
        Log.d(TAG, "创建链接: domain=$domain, url=$originalUrl")
        val body = """{"originalURL":"$originalUrl","domain":"$domain"}"""
        val response = HttpUtil.post(
            "$SHORT_IO_BASE/links", body,
            headers = mapOf("Authorization" to getApiKey(ctx))
        )
        return if (response != null) {
            try {
                val json = JsonParser.parseString(response).asJsonObject
                val idString = json.get("idString")?.asString ?: json.get("id")?.asString ?: ""
                val path = json.get("path")?.asString ?: json.get("shortURL")?.asString ?: ""
                val shortURL = json.get("secureShortURL")?.asString ?: json.get("shortURL")?.asString ?: ""
                ShortIoLink(idString, path, shortURL, originalUrl).let { Result.success(it) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException("创建短链接失败，请检查网络"))
        }
    }

    private suspend fun updateShortIoLink(ctx: Context, newUrl: String): Result<String> {
        val linkId = getLinkId(ctx)
        val body = """{"originalURL":"$newUrl"}"""
        Log.d(TAG, "更新链接: id=$linkId, newUrl=$newUrl")
        val response = HttpUtil.post(
            "$SHORT_IO_BASE/links/$linkId", body,
            headers = mapOf("Authorization" to getApiKey(ctx))
        )
        return if (response != null) {
            Result.success(getShortUrl(ctx) ?: "")
        } else {
            Result.failure(IllegalStateException("更新短链接失败，请检查网络连接"))
        }
    }

    // --- 自定义 API 实现 ---

    private suspend fun updateCustomLink(ctx: Context, newUrl: String): Result<String> {
        val linkId = getLinkId(ctx)
        val path = getUpdatePath(ctx).replace("{linkId}", linkId)
        val url = buildCustomUrl(ctx, path)
        val body = getUpdateBody(ctx)
            .replace("{url}", newUrl)
            .replace("{linkId}", linkId)
        val headers = buildAuthHeaders(ctx)

        val response = when (getApiMethod(ctx).uppercase()) {
            "PUT" -> HttpUtil.put(url, body, headers = headers)
            else -> HttpUtil.patch(url, body, headers = headers)
        }
        return if (response != null) {
            Result.success(getShortUrl(ctx) ?: "")
        } else {
            Result.failure(IllegalStateException("更新短链接失败"))
        }
    }

    // --- 工具方法 ---

    private fun buildCustomUrl(ctx: Context, path: String): String {
        val base = getApiUrl(ctx).trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base$p"
    }

    private fun buildAuthHeaders(ctx: Context): Map<String, String> {
        val key = getApiKey(ctx)
        return if (key.isNotBlank()) {
            mapOf(getAuthHeader(ctx) to "${getAuthPrefix(ctx)}$key")
        } else emptyMap()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
