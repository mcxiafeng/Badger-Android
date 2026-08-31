package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.ShortLinkPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory

/**
 * Compatibility wrapper around the Badger-Server short-link proxies.
 * The short.io API key is server-owned; local preferences only keep UI
 * selection state and custom-provider configuration.
 */
data class ShortIoLink(
    val idString: String,
    val path: String,
    val shortURL: String,
    val originalURL: String,
)

data class ShortIoDomain(
    val hostname: String,
    val id: Long,
)

object ShortLinkService {

    private const val TAG = "ShortLinkService"

    private fun api(): ServerApi =
        GlobalContext.get().get<ServerApiFactory>().get()

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
    ) = ShortLinkPrefs.saveAdvanced(ctx, enabled, apiUrl, updatePath, method, authHeader, authPrefix, updateBody)

    /** True when there is a selected server link or a custom short-link provider. */
    fun isConfigured(ctx: Context): Boolean =
        ShortLinkPrefs.getLinkId(ctx).isNotBlank() || ShortLinkPrefs.isCustomEnabled(ctx)

    /**
     * Local-only lookup used by Compose state initialization. Never performs
     * network I/O; server refresh is handled explicitly by suspend callers.
     */
    fun getShortUrl(ctx: Context): String? = ShortLinkPrefs.getShortUrl(ctx)

    fun updateLinkDestination(context: Context, newUrl: String): Result<String> = runCatching {
        val id = ShortLinkPrefs.getLinkId(context)
        require(id.isNotBlank()) { "no link selected" }
        val shortUrl = api().shortioUpdate(id, newUrl).get("shortURL")?.asString.orEmpty()
        ShortLinkPrefs.saveShortUrl(context, shortUrl)
        shortUrl
    }

    fun fetchLinkDetails(context: Context): Result<ShortIoLink> = runCatching {
        val id = ShortLinkPrefs.getLinkId(context)
        require(id.isNotBlank()) { "no link selected" }
        val arr = api().shortioList().getAsJsonArray("links") ?: error("no links")
        val obj = arr.firstOrNull { it.asJsonObject.get("idString")?.asString == id }?.asJsonObject
            ?: error("link not found")
        ShortIoLink(
            idString = obj.get("idString")?.asString.orEmpty(),
            path = obj.get("path")?.asString.orEmpty(),
            shortURL = obj.get("shortURL")?.asString.orEmpty(),
            originalURL = obj.get("originalURL")?.asString.orEmpty(),
        ).also { ShortLinkPrefs.saveShortUrl(context, it.shortURL.ifBlank { "https://${getDomain(context)}/${it.path}" }) }
    }

    suspend fun fetchDomains(context: Context): Result<List<ShortIoDomain>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api().shortioDomains()
            val arr: JsonArray? = resp.getAsJsonArray("domains")
            arr?.mapNotNull { el ->
                val o = el.asJsonObject
                val host = o.get("hostname")?.asString ?: return@mapNotNull null
                ShortIoDomain(hostname = host, id = o.get("id")?.asLong ?: 0L)
            } ?: emptyList()
        }
    }

    suspend fun fetchLinks(context: Context, domainId: Long): Result<List<ShortIoLink>> = withContext(Dispatchers.IO) {
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

    suspend fun createShortIoLink(context: Context, originalUrl: String): Result<ShortIoLink> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api().shortioCreate(originalUrl = originalUrl)
            ShortIoLink(
                idString = resp.get("idString")?.asString.orEmpty(),
                path = resp.get("path")?.asString.orEmpty(),
                shortURL = resp.get("shortURL")?.asString.orEmpty(),
                originalURL = resp.get("originalURL")?.asString ?: originalUrl,
            )
        }
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
}
