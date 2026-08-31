package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ShortLinkPrefs

data class ShortIoLink(val idString: String, val path: String, val shortURL: String, val originalURL: String)
data class ShortIoDomain(val hostname: String, val id: Long)

/** Short-link coordinator. Server-owned short.io credentials never enter local preferences. */
class ShortLinkService(
    private val serverApi: ServerApi,
) {
    private companion object {
        const val TAG = "ShortLinkService"

        /** Compatibility entry point for UI code that only has a Context. */
        @JvmStatic
        fun isConfigured(ctx: Context): Boolean = isConfiguredLocal(ctx)
    }

    fun isEnabled(ctx: Context): Boolean = ShortLinkPrefs.isEnabled(ctx)
    fun setEnabled(ctx: Context, value: Boolean) = ShortLinkPrefs.setEnabled(ctx, value)
    fun getDomain(ctx: Context): String = ShortLinkPrefs.getDomain(ctx)
    fun getLinkId(ctx: Context): String = ShortLinkPrefs.getLinkId(ctx)
    fun getDomainId(ctx: Context): Long = ShortLinkPrefs.getDomainId(ctx)
    fun getShortUrl(ctx: Context): String? = ShortLinkPrefs.getShortUrl(ctx)
    fun isCustomEnabled(ctx: Context): Boolean = ShortLinkPrefs.isCustomEnabled(ctx)
    fun getApiUrl(ctx: Context): String = ShortLinkPrefs.getApiUrl(ctx)
    fun getUpdatePath(ctx: Context): String = ShortLinkPrefs.getUpdatePath(ctx)
    fun getApiMethod(ctx: Context): String = ShortLinkPrefs.getApiMethod(ctx)
    fun getAuthHeader(ctx: Context): String = ShortLinkPrefs.getAuthHeader(ctx)
    fun getAuthPrefix(ctx: Context): String = ShortLinkPrefs.getAuthPrefix(ctx)
    fun getUpdateBody(ctx: Context): String = ShortLinkPrefs.getUpdateBody(ctx)

    fun saveAdvancedSettings(
        ctx: Context,
        enabled: Boolean,
        apiUrl: String,
        updatePath: String,
        method: String,
        authHeader: String,
        authPrefix: String,
        updateBody: String,
    ) = ShortLinkPrefs.saveAdvanced(
        ctx,
        enabled,
        apiUrl,
        updatePath,
        method,
        authHeader,
        authPrefix,
        updateBody,
    )

    fun isConfigured(ctx: Context): Boolean = isConfiguredLocal(ctx)

    suspend fun updateLinkDestination(context: Context, newUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = ShortLinkPrefs.getLinkId(context)
                require(id.isNotBlank()) { "no link selected" }
                val shortUrl = serverApi.shortioUpdate(id, newUrl).get("shortURL")?.asString.orEmpty()
                ShortLinkPrefs.saveShortUrl(context, shortUrl)
                shortUrl
            }
        }

    suspend fun fetchLinkDetails(context: Context): Result<ShortIoLink> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = ShortLinkPrefs.getLinkId(context)
                require(id.isNotBlank()) { "no link selected" }
                val arr = serverApi.shortioList().getAsJsonArray("links") ?: error("no links")
                val obj = arr.firstOrNull { it.asJsonObject.get("idString")?.asString == id }?.asJsonObject
                    ?: error("link not found")
                ShortIoLink(
                    obj.get("idString")?.asString.orEmpty(),
                    obj.get("path")?.asString.orEmpty(),
                    obj.get("shortURL")?.asString.orEmpty(),
                    obj.get("originalURL")?.asString.orEmpty(),
                ).also {
                    val domain = getDomain(context)
                    val fallbackUrl = if (domain.isBlank()) it.path else "https://$domain/${it.path}"
                    ShortLinkPrefs.saveShortUrl(context, it.shortURL.ifBlank { fallbackUrl })
                }
            }
        }

    suspend fun fetchDomains(): Result<List<ShortIoDomain>> = withContext(Dispatchers.IO) {
        runCatching {
            val arr: JsonArray? = serverApi.shortioDomains().getAsJsonArray("domains")
            arr?.mapNotNull { el ->
                val obj = el.asJsonObject
                val hostname = obj.get("hostname")?.asString ?: return@mapNotNull null
                ShortIoDomain(hostname, obj.get("id")?.asLong ?: 0L)
            } ?: emptyList()
        }
    }

    suspend fun fetchLinks(domainId: Long): Result<List<ShortIoLink>> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = serverApi.shortioList().getAsJsonArray("links") ?: return@runCatching emptyList()
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val returnedDomainId = obj.get("domainId")?.asLong ?: 0L
                if (domainId > 0 && returnedDomainId != domainId) return@mapNotNull null
                ShortIoLink(
                    obj.get("idString")?.asString ?: return@mapNotNull null,
                    obj.get("path")?.asString.orEmpty(),
                    obj.get("shortURL")?.asString.orEmpty(),
                    obj.get("originalURL")?.asString.orEmpty(),
                )
            }
        }
    }

    suspend fun createShortIoLink(originalUrl: String): Result<ShortIoLink> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = serverApi.shortioCreate(originalUrl = originalUrl)
                ShortIoLink(
                    resp.get("idString")?.asString.orEmpty(),
                    resp.get("path")?.asString.orEmpty(),
                    resp.get("shortURL")?.asString.orEmpty(),
                    resp.get("originalURL")?.asString ?: originalUrl,
                )
            }.onFailure { Log.w(TAG, "create short.io link failed", it) }
        }

    fun saveDomainSelection(ctx: Context, domain: ShortIoDomain) {
        ShortLinkPrefs.saveDomain(ctx, domain.hostname)
        ShortLinkPrefs.saveDomainId(ctx, domain.id)
        ShortLinkPrefs.saveLinkId(ctx, "")
        ShortLinkPrefs.saveShortUrl(ctx, null)
    }

    fun saveLinkSelection(ctx: Context, link: ShortIoLink) {
        ShortLinkPrefs.saveLinkId(ctx, link.idString)
        ShortLinkPrefs.saveShortUrl(ctx, link.shortURL.takeIf { it.isNotBlank() })
        Log.d(TAG, "selected short link ${link.idString}")
    }

    private fun isConfiguredLocal(ctx: Context): Boolean =
        ShortLinkPrefs.getLinkId(ctx).isNotBlank() || ShortLinkPrefs.isCustomEnabled(ctx)
}
