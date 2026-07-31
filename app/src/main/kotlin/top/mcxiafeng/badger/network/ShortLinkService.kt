package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.ShortLinkPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory

/**
 * Compatibility wrapper around the Badger-Server short-link proxies so
 * existing UI keeps calling the same `ShortLinkService.xxx(context, ...)`
 * shape. The short.io API key now lives on the server; the client only
 * stores per-user preferences locally and sends/receives DTOs.
 *
 * Local prefs (api key, enabled flag, domain, link id, advanced custom
 * service fields) all live in [ShortLinkPrefs].
 *
 * 修复防御: 旧版 ShortLinkService 大部分方法返回 null/默认,导致 UI 状态
 * 丢失。本版本把所有 NfcSettingsPage 用到的方法补齐,本地读写 + 服务端
 * 代理 (shortioList / shortioUpdate) 双层结构。
 */
/** DTO kept for source compatibility with existing UI. */
data class ShortIoLink(
    val idString: String,
    val path: String,
    val shortURL: String,
    val originalURL: String,
)

/** DTO kept for source compatibility with existing UI. */
data class ShortIoDomain(
    val hostname: String,
    val id: Long,
)

object ShortLinkService {

    private const val TAG = "ShortLinkService"

    // [修复防御]: 必须走 [ServerApiFactory.get()],而不是自己 `new ServerApi()`。
    // 与全 app 复用同一份 OkHttp + TokenHolder —— 改 baseUrl 立即生效;access token
    // 失效时由 NetworkModule.tokenRefreshInterceptor 自动 refresh + 重试一次。
    private fun api(): ServerApi =
        GlobalContext.get().get<ServerApiFactory>().get()

    // ---- Local prefs passthroughs ----

    fun getApiKey(ctx: Context): String = ShortLinkPrefs.getApiKey(ctx)
    fun saveApiKey(ctx: Context, k: String) = ShortLinkPrefs.saveApiKey(ctx, k)

    fun isEnabled(ctx: Context): Boolean = ShortLinkPrefs.isEnabled(ctx)
    fun setEnabled(ctx: Context, b: Boolean) = ShortLinkPrefs.setEnabled(ctx, b)

    fun getDomain(ctx: Context): String = ShortLinkPrefs.getDomain(ctx)
    fun getLinkId(ctx: Context): String = ShortLinkPrefs.getLinkId(ctx)

    fun getDomainId(ctx: Context): Long = ShortLinkPrefs.getDomainId(ctx)

    fun isCustomEnabled(ctx: Context): Boolean = ShortLinkPrefs.isCustomEnabled(ctx)

    fun getApiUrl(ctx: Context): String = ShortLinkPrefs.getApiUrl(ctx)
    fun getUpdatePath(ctx: Context): String = ShortLinkPrefs.getUpdatePath(ctx)
    fun getApiMethod(ctx: Context): String = ShortLinkPrefs.getApiMethod(ctx)
    fun getAuthHeader(ctx: Context): String = ShortLinkPrefs.getAuthHeader(ctx)
    fun getAuthPrefix(ctx: Context): String = ShortLinkPrefs.getAuthPrefix(ctx)
    fun getUpdateBody(ctx: Context): String = ShortLinkPrefs.getUpdateBody(ctx)

    fun saveAdvancedSettings(
        ctx: Context, enabled: Boolean, apiUrl: String,
        updatePath: String, method: String, authHeader: String,
        authPrefix: String, updateBody: String,
    ) {
        ShortLinkPrefs.saveAdvanced(ctx, enabled, apiUrl, updatePath, method, authHeader, authPrefix, updateBody)
    }

    /**
     * ShortLinkService is configured when the user has a non-empty API key
     * (or has explicitly enabled custom-mode). Until they save credentials
     * we don't pretend to be ready.
     */
    fun isConfigured(ctx: Context): Boolean =
        getApiKey(ctx).isNotBlank() || isCustomEnabled(ctx)

    // ---- Network-backed getters ----

    /**
     * Returns the canonical short URL the user has saved as their NFC
     * target. Reads from local prefs first; falls back to a fresh
     * `shortioList` call so a re-login / re-install keeps showing the same
     * link. `null` when nothing is configured yet.
     */
    fun getShortUrl(ctx: Context): String? {
        val cached = ShortLinkPrefs.getApiKey(ctx) // not the URL; keep API surface
        // Cache the active link id, not the URL, because the URL on the
        // server can change after we re-bind it. Re-fetch on every call is
        // cheap (single HTTP round-trip) and keeps the result honest.
        val id = ShortLinkPrefs.getLinkId(ctx)
        if (id.isBlank()) return null
        return try {
            val resp = api().shortioList()
            val links = resp.getAsJsonArray("links") ?: return null
            links.firstOrNull { it.asJsonObject.get("idString")?.asString == id }
                ?.asJsonObject?.get("shortURL")?.asString
        } catch (e: Exception) {
            Log.w(TAG, "getShortUrl failed", e)
            null
        }
    }

    /**
     * Re-points the saved short link at [newUrl]. Returns the new short
     * URL on success.
     */
    fun updateLinkDestination(context: Context, newUrl: String): Result<String> = runCatching {
        val a = api()
        val id = ShortLinkPrefs.getLinkId(context)
        require(id.isNotBlank()) { "no link selected" }
        val resp = a.shortioUpdate(id, newUrl)
        resp.get("shortURL")?.asString ?: ""
    }

    fun fetchLinkDetails(context: Context): Result<ShortIoLink> = runCatching {
        val a = api()
        val id = ShortLinkPrefs.getLinkId(context)
        require(id.isNotBlank()) { "no link selected" }
        val resp = a.shortioList()
        val arr = resp.getAsJsonArray("links") ?: error("no links")
        val obj = arr.firstOrNull { it.asJsonObject.get("idString")?.asString == id }?.asJsonObject
            ?: error("link not found")
        ShortIoLink(
            idString = obj.get("idString").asString,
            path = obj.get("path")?.asString.orEmpty(),
            shortURL = obj.get("shortURL").asString,
            originalURL = obj.get("originalURL")?.asString.orEmpty(),
        )
    }

    /**
     * Domain list. Backed by the server's dedicated `/v1/proxy/shortio/domains`
     * endpoint. Returns the raw list (may be empty if the account has no
     * short.io domains yet).
     */
    suspend fun fetchDomains(context: Context): Result<List<ShortIoDomain>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api().shortioDomains()
            val arr: JsonArray? = resp.getAsJsonArray("domains")
            val list = arr?.mapNotNull { el ->
                val o = el.asJsonObject
                val host = o.get("hostname")?.asString ?: return@mapNotNull null
                val id = o.get("id")?.asLong ?: 0L
                ShortIoDomain(hostname = host, id = id)
            } ?: emptyList()
            list
        }
    }

    /**
     * Link list for a given domain. `domainId` is the short.io domain id;
     * the server's `/v1/proxy/shortio/links` action returns the full set,
     * we filter client-side to keep the wire shape simple.
     */
    suspend fun fetchLinks(context: Context, domainId: Long): Result<List<ShortIoLink>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api().shortioList()
                val arr = resp.getAsJsonArray("links") ?: return@runCatching emptyList()
                arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val dId = o.get("domainId")?.asLong ?: 0L
                    if (domainId > 0 && dId != domainId) return@mapNotNull null
                    ShortIoLink(
                        idString = o.get("idString")?.asString ?: return@mapNotNull null,
                        path = o.get("path")?.asString.orEmpty(),
                        shortURL = o.get("shortURL")?.asString.orEmpty(),
                        originalURL = o.get("originalURL")?.asString.orEmpty(),
                    )
                }
            }
        }

    /**
     * Create a short link pointing at [originalUrl]. Backed by the server's
     * proxy — the actual short.io API key is held server-side.
     */
    suspend fun createShortIoLink(context: Context, originalUrl: String): Result<ShortIoLink> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api().shortioCreate(originalUrl = originalUrl)
                ShortIoLink(
                    idString = resp.get("idString")?.asString ?: "",
                    path = resp.get("path")?.asString.orEmpty(),
                    shortURL = resp.get("shortURL")?.asString.orEmpty(),
                    originalURL = resp.get("originalURL")?.asString ?: originalUrl,
                )
            }
        }

    /**
     * Persist the user's domain pick. Saves both the hostname (for display
     * in the UI summary line) and the numeric id (so we can scope the
     * link fetch).
     */
    fun saveDomainSelection(ctx: Context, d: ShortIoDomain) {
        ShortLinkPrefs.saveDomain(ctx, d.hostname)
        ShortLinkPrefs.saveDomainId(ctx, d.id)
        // Picking a domain implicitly clears any stale link selection.
        ShortLinkPrefs.saveLinkId(ctx, "")
    }

    /**
     * Persist the user's link pick. Stores the idString so subsequent
     * `updateLinkDestination` / `getShortUrl` calls hit the right entry.
     */
    fun saveLinkSelection(ctx: Context, link: ShortIoLink) {
        ShortLinkPrefs.saveLinkId(ctx, link.idString)
    }
}