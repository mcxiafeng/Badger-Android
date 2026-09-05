package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.prefs.ShortLinkPrefs
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

data class ShortIoLink(val idString: String, val path: String, val shortURL: String, val originalURL: String)
data class ShortIoDomain(val hostname: String, val id: Long)

/** Short-link coordinator. Server-owned short.io credentials never enter local preferences. */
class ShortLinkService(
    private val serverApi: ServerApi,
) {

    fun isEnabled(): Boolean = ShortLinkPrefs.isEnabled()
    fun setEnabled(value: Boolean) = ShortLinkPrefs.setEnabled(value)
    fun getDomain(): String = ShortLinkPrefs.getDomain()
    fun getLinkId(): String = ShortLinkPrefs.getLinkId()
    fun getDomainId(): Long = ShortLinkPrefs.getDomainId()
    fun getShortUrl(): String? = ShortLinkPrefs.getShortUrl()
    fun isCustomEnabled(): Boolean = ShortLinkPrefs.isCustomEnabled()
    fun getApiUrl(): String = ShortLinkPrefs.getApiUrl()
    fun getUpdatePath(): String = ShortLinkPrefs.getUpdatePath()
    fun getApiMethod(): String = ShortLinkPrefs.getApiMethod()
    fun getAuthHeader(): String = ShortLinkPrefs.getAuthHeader()
    fun getAuthPrefix(): String = ShortLinkPrefs.getAuthPrefix()
    fun getUpdateBody(): String = ShortLinkPrefs.getUpdateBody()

    fun saveAdvancedSettings(
        enabled: Boolean,
        apiUrl: String,
        updatePath: String,
        method: String,
        authHeader: String,
        authPrefix: String,
        updateBody: String,
    ) = ShortLinkPrefs.saveAdvanced(enabled,
        apiUrl,
        updatePath,
        method,
        authHeader,
        authPrefix,
        updateBody,
    )

    fun isConfigured(): Boolean = isConfiguredLocal()

    suspend fun updateLinkDestination(newUrl: String): Result<String> =
        withContext(BadgerDispatchers.io) {
            runCatching {
                val id = ShortLinkPrefs.getLinkId()
                require(id.isNotBlank()) { "no link selected" }
                val shortUrl = (serverApi.shortioUpdate(id, newUrl)["shortURL"] as? JsonPrimitive)?.content.orEmpty()
                ShortLinkPrefs.saveShortUrl(shortUrl)
                shortUrl
            }
        }

    suspend fun fetchLinkDetails(): Result<ShortIoLink> =
        withContext(BadgerDispatchers.io) {
            runCatching {
                val id = ShortLinkPrefs.getLinkId()
                require(id.isNotBlank()) { "no link selected" }
                val arr = serverApi.shortioList()["links"] as? JsonArray ?: error("no links")
                val obj = arr.firstOrNull { o -> (o as? JsonObject)?.let { (it["idString"] as? JsonPrimitive)?.content } == id } as? JsonObject
                    ?: error("link not found")
                ShortIoLink(
                    (obj["idString"] as? JsonPrimitive)?.content.orEmpty(),
                    (obj["path"] as? JsonPrimitive)?.content.orEmpty(),
                    (obj["shortURL"] as? JsonPrimitive)?.content.orEmpty(),
                    (obj["originalURL"] as? JsonPrimitive)?.content.orEmpty(),
                ).also {
                    val domain = getDomain()
                    val fallbackUrl = if (domain.isBlank()) it.path else "https://$domain/${it.path}"
                    ShortLinkPrefs.saveShortUrl(it.shortURL.ifBlank { fallbackUrl })
                }
            }
        }

    suspend fun fetchDomains(): Result<List<ShortIoDomain>> = withContext(BadgerDispatchers.io) {
        runCatching {
            val arr: JsonArray? = serverApi.shortioDomains()["domains"] as? JsonArray
            arr?.mapNotNull { el ->
                val obj = el as JsonObject
                val hostname = (obj["hostname"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                ShortIoDomain(hostname, longOr(obj["id"], 0L))
            } ?: emptyList()
        }
    }

    suspend fun fetchLinks(domainId: Long): Result<List<ShortIoLink>> = withContext(BadgerDispatchers.io) {
        runCatching {
            val arr = serverApi.shortioList()["links"] as? JsonArray ?: return@runCatching emptyList()
            arr.mapNotNull { el ->
                val obj = el as JsonObject
                val returnedDomainId = longOr(obj["domainId"], 0L)
                if (domainId > 0 && returnedDomainId != domainId) return@mapNotNull null
                ShortIoLink(
                    (obj["idString"] as? JsonPrimitive)?.content ?: return@mapNotNull null,
                    (obj["path"] as? JsonPrimitive)?.content.orEmpty(),
                    (obj["shortURL"] as? JsonPrimitive)?.content.orEmpty(),
                    (obj["originalURL"] as? JsonPrimitive)?.content.orEmpty(),
                )
            }
        }
    }

    suspend fun createShortIoLink(originalUrl: String): Result<ShortIoLink> =
        withContext(BadgerDispatchers.io) {
            runCatching {
                val resp = serverApi.shortioCreate(originalUrl = originalUrl)
                ShortIoLink(
                    (resp["idString"] as? JsonPrimitive)?.content.orEmpty(),
                    (resp["path"] as? JsonPrimitive)?.content.orEmpty(),
                    (resp["shortURL"] as? JsonPrimitive)?.content.orEmpty(),
                    (resp["originalURL"] as? JsonPrimitive)?.content ?: originalUrl,
                )
            }.onFailure { BadgerLog.w(TAG, "create short.io link failed", it) }
        }

    fun saveDomainSelection(domain: ShortIoDomain) {
        ShortLinkPrefs.saveDomain(domain.hostname)
        ShortLinkPrefs.saveDomainId(domain.id)
        ShortLinkPrefs.saveLinkId("")
        ShortLinkPrefs.saveShortUrl(null)
    }

    fun saveLinkSelection(link: ShortIoLink) {
        ShortLinkPrefs.saveLinkId(link.idString)
        ShortLinkPrefs.saveShortUrl(link.shortURL.takeIf { it.isNotBlank() })
        BadgerLog.d(TAG, "selected short link ${link.idString}")
    }

    private fun isConfiguredLocal(): Boolean =
        ShortLinkPrefs.getLinkId().isNotBlank() || ShortLinkPrefs.isCustomEnabled()

    // [兼容] 静态门面:dev 的 UI 层(NfcSettingsPage / Social / Setup 等)仍以
    // `ShortLinkService.xxx()` 的 companion 形式调用。实现统一委托到
    // Koin 单例实例(构造器注入 ServerApi);UI 迁移到 koinInject 后整体移除。
    // 忽略 context 的网络方法,其凭证由服务端托管(serverApi)。
    public companion object {
        const val TAG = "ShortLinkService"

        private val instance: ShortLinkService
            get() = top.mcxiafeng.badger.di.KoinComponentBy.get()

        fun getApiKey(): String = ShortLinkPrefs.getApiKey()
        fun saveApiKey(k: String) = ShortLinkPrefs.saveApiKey(k)
        fun isEnabled(): Boolean = instance.isEnabled()
        fun setEnabled(value: Boolean) = instance.setEnabled(value)
        fun getDomain(): String = instance.getDomain()
        fun getDomainId(): Long = instance.getDomainId()
        fun getLinkId(): String = instance.getLinkId()
        fun getShortUrl(): String? = instance.getShortUrl()
        fun isCustomEnabled(): Boolean = instance.isCustomEnabled()
        fun getApiUrl(): String = instance.getApiUrl()
        fun getUpdatePath(): String = instance.getUpdatePath()
        fun getApiMethod(): String = instance.getApiMethod()
        fun getAuthHeader(): String = instance.getAuthHeader()
        fun getAuthPrefix(): String = instance.getAuthPrefix()
        fun getUpdateBody(): String = instance.getUpdateBody()
        fun isConfigured(): Boolean = instance.isConfigured()
        fun saveAdvancedSettings(
            enabled: Boolean,
            apiUrl: String,
            updatePath: String,
            method: String,
            authHeader: String,
            authPrefix: String,
            updateBody: String,
        ) = instance.saveAdvancedSettings(
            enabled, apiUrl, updatePath, method, authHeader, authPrefix, updateBody,
        )
        fun saveDomainSelection(domain: ShortIoDomain) =
            instance.saveDomainSelection(domain)
        fun saveLinkSelection(link: ShortIoLink) =
            instance.saveLinkSelection(link)
        suspend fun fetchDomains(): Result<List<ShortIoDomain>> =
            instance.fetchDomains()
        suspend fun fetchLinkDetails(): Result<ShortIoLink> =
            instance.fetchLinkDetails()
        suspend fun fetchLinks(domainId: Long): Result<List<ShortIoLink>> =
            instance.fetchLinks(domainId)
        suspend fun createShortIoLink(
            originalUrl: String,
        ): Result<ShortIoLink> = instance.createShortIoLink(originalUrl)
    }
}
