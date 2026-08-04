package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject

/**
 * Thin REST client for the Badger-Server HTTP surface.
 *
 * [§15 #19] This class used to be a 700+ line god service spanning contact,
 * auth, AI proxy, resolver, short link, and backup domains. It is now a
 * facade: each domain lives in its own [ContactApi] / [AuthApi] / [AiApi] /
 * [ResolverApi] / [ShortLinkApi] / [BackupApi] class. They all share one
 * [ApiCore] for HTTP plumbing.
 *
 * Public API is preserved 1:1 so call sites (33 across the app) don't need
 * to change — they keep using [createContact], [login], etc.
 */
class ServerApi(
    baseUrl: String,
    private val http: okhttp3.OkHttpClient,
    private val tokenProvider: () -> String?,
) {
    @Volatile private var baseUrl: String = baseUrl

    private val core = ApiCore(baseUrl, http, tokenProvider)
    private val contact = ContactApi(core)
    private val auth = AuthApi(core)
    private val ai = AiApi(core)
    private val resolver = ResolverApi(core)
    private val shortLink = ShortLinkApi(core)
    private val backup = BackupApi(core)
    private val v2 = V2DomainApi(core)

    /**
     * Update the base URL used for every subsequent request. Must only be
     * called from [top.mcxiafeng.badger.data.repository.ServerApiFactory];
     * call sites should go through `ServerApiFactory.updateBaseUrl()` which
     * also persists to prefs.
     *
     * No-op when [newUrl] equals the current value.
     */
    fun setBaseUrl(newUrl: String) {
        if (newUrl == baseUrl) return
        Log.d(TAG, "setBaseUrl: $baseUrl -> $newUrl")
        baseUrl = newUrl
        core.baseUrl = newUrl
    }

    // ============ Contact domain ============

    fun createContact(payload: JsonObject, ifMatch: Long? = null): ContactResponse =
        contact.createContact(payload, ifMatch)

    fun getContact(serverId: String): ContactResponse =
        contact.getContact(serverId)

    fun patchContact(serverId: String, payload: JsonObject, ifMatch: Long?): ContactResponse =
        contact.patchContact(serverId, payload, ifMatch)

    fun deleteContact(serverId: String, ifMatch: Long?): Boolean =
        contact.deleteContact(serverId, ifMatch)

    fun mergeContact(targetServerId: String, mergedIds: List<String>, ifMatch: Long?): ContactResponse =
        contact.mergeContact(targetServerId, mergedIds, ifMatch)

    fun listContacts(since: Long? = null, limit: Int = 50): ContactPage =
        contact.listContacts(since, limit)

    // ============ Auth domain ============

    fun register(username: String, password: String, email: String?, displayName: String?): AuthResponse =
        auth.register(username, password, email, displayName)

    fun login(username: String, password: String): AuthResponse =
        auth.login(username, password)

    fun refresh(): AuthResponse = auth.refresh()
    fun logout() = auth.logout()
    fun me(): JsonObject = auth.me()

    // ============ AI domain ============

    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> =
        ai.tagGenerate(bio, existingTagNames)

    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact =
        ai.contactOcr(imageB64, text)

    // ============ Resolver domain ============

    fun resolveIdentify(input: String): JsonObject? = resolver.resolveIdentify(input)

    // ============ ShortLink domain ============

    fun shortioList(): JsonObject = shortLink.shortioList()
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject = shortLink.shortioUpdate(linkId, newUrl)
    fun shortioDomains(): JsonObject = shortLink.shortioDomains()
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject =
        shortLink.shortioCreate(originalUrl, domainId)

    // ============ Backup domain ============

    fun listBackups(): List<BackupSummary> = backup.listBackups()
    fun uploadBackup(envelopeJson: String): BackupUpload = backup.uploadBackup(envelopeJson)
    fun downloadBackup(id: String): ByteArray = backup.downloadBackup(id)
    fun deleteBackup(id: String): Boolean = backup.deleteBackup(id)

    // ============ V2 Profile / Tag / Collection domain ============
    // [V2-P12] PendingUploadExecutor 消费 USER_PROFILE_UPSERT / TAG_UPSERT/DELETE /
    // COLLECTION_UPSERT/DELETE 时调这里;UI 层通常不直接调用。

    fun patchMe(
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        platformsJson: String? = null,
    ): com.google.gson.JsonObject = v2.patchMe(displayName, bio, avatarUrl, platformsJson)

    fun createTag(name: String, color: String, pinyinInitial: String): com.google.gson.JsonObject =
        v2.createTag(name, color, pinyinInitial)

    fun patchTag(id: Long, name: String? = null, color: String? = null): com.google.gson.JsonObject =
        v2.patchTag(id, name, color)

    fun deleteTag(id: Long): Boolean = v2.deleteTag(id)

    fun createCollection(
        name: String,
        color: String? = null,
        backgroundImagePath: String? = null,
    ): com.google.gson.JsonObject = v2.createCollection(name, color, backgroundImagePath)

    fun patchCollection(
        id: Long,
        name: String? = null,
        color: String? = null,
        backgroundImagePath: String? = null,
    ): com.google.gson.JsonObject = v2.patchCollection(id, name, color, backgroundImagePath)

    fun deleteCollection(id: Long): Boolean = v2.deleteCollection(id)

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
