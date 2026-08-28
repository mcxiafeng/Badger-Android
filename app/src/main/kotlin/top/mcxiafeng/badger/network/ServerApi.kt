package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject

/**
 * Thin REST client for the Badger-Server HTTP surface.
 *
 * [§15 #19] This class used to be a 700+ line god service spanning contact,
 * auth, AI proxy, resolver, short link, and backup domains. It is now a
 * facade: each domain lives in its own [AuthApi] / [AiApi] /
 * [ResolverApi] / [ShortLinkApi] / [BackupApi] / [NotificationApi] class. They all share one
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
    private val auth = AuthApi(core)
    private val ai = AiApi(core)
    private val resolver = ResolverApi(core)
    private val shortLink = ShortLinkApi(core)
    private val backup = BackupApi(core)
    private val notifications = NotificationApi(core)
    private val v2 = V2DomainApi(core)
    // [Phase 3] Person / Sync 新契约
    private val person = PersonApi(core)
    private val sync = SyncApi(core)

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

    // ============ Person domain（新 Java /api 契约，Phase 3） ============
    // 旧 Go 契约的 createContact/getContact/patchContact/deleteContact/mergeContact/listContacts
    // 已退役（ContactRepositoryImpl 重写后移除），见 [PersonApi]。

    fun listPersons(): List<PersonDto> = person.listPersons()

    fun getPerson(uuid: String): PersonDto = person.getPerson(uuid)

    /** 创建 Person；[clientUuid] 为客户端幂等重放键（重试重放返回既有行）。 */
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String =
        person.createPerson(name, profile, clientUuid)

    fun updatePerson(uuid: String, name: String?, profile: ProfileDto?) =
        person.updatePerson(uuid, name, profile)

    /** DELETE person；404 幂等成功，selfPerson 400 原样抛 [ApiException]。 */
    fun deletePerson(uuid: String): Boolean = person.deletePerson(uuid)

    fun mergePersons(targetUuid: String, mergedIds: List<String>): String =
        person.mergePersons(targetUuid, mergedIds)

    // ============ Sync domain（Phase 3） ============

    /** GET /api/user/sync?since= — 增量拉取；[SyncPage.hasMore] 需续拉。 */
    fun syncSince(since: Long, limit: Int = 500): SyncPage = sync.syncSince(since, limit)

    // ============ Auth domain ============
    // [Phase 2] 全部走新 Java /api 契约（ApiResult 壳）；注册成功不返回 token，需再 login。

    fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    ) = auth.register(username, email, password, passwordAgain, captchaId, captchaCode, emailCaptchaId, emailCode)

    fun login(username: String, password: String, deviceId: String? = null, deviceName: String? = null): AuthResponse =
        auth.login(username, password, deviceId, deviceName)

    fun refresh(): AuthResponse = auth.refresh()
    fun logout() = auth.logout()
    fun me(): JsonObject? = auth.me()

    fun registerPolicy(): RegisterPolicy = auth.registerPolicy()
    fun getCaptcha(): CaptchaResult = auth.getCaptcha()
    fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult =
        auth.sendVerificationCode(email, purpose)

    /** POST /api/auth/forgotPassword — 重置密码（需先 sendVerificationCode purpose="forgotPassword" 拿 captchaId+captchaCode）。 */
    fun forgotPassword(email: String, captchaId: String, captchaCode: String, newPassword: String, newPasswordAgain: String) =
        auth.forgotPassword(email, captchaId, captchaCode, newPassword, newPasswordAgain)

    // ============ AI domain ============

    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> =
        ai.tagGenerate(bio, existingTagNames)

    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact =
        ai.contactOcr(imageB64, text)

    // ============ Resolver domain ============

    fun resolveIdentify(input: String): JsonObject? = resolver.resolveIdentify(input)

    /**
     * Batch variant of [resolveIdentify]: sends a single POST `/api/resolve/`
     * with `items` array, returns one JSON per input URL in order (null on
     * failure). Prefer this when the caller knows ≥ 2 URLs at once
     * (e.g. multi-QR scanner ResultDialog) — saves N-1 RTTs.
     */
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> =
        resolver.resolveIdentifyBatch(inputs)

    /** GET /api/resolve/platforms — 服务端可解析平台清单（含自定义，过滤禁用）。 */
    fun platforms(): List<JsonObject> = resolver.platforms()

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

    // ============ Notification domain（B1） ============

    /** GET /api/user/notifications/unread-count — `data.unread`。 */
    fun getUnreadNotificationCount(): Int = notifications.getUnreadCount()

    /** GET /api/user/notifications — 全量列表（未读在前）。 */
    fun listNotifications(): List<UserNotification> = notifications.listNotifications()

    /** PUT /api/user/notifications/{uuid}/read — 已读幂等。 */
    fun markNotificationRead(uuid: String) = notifications.markAsRead(uuid)

    /** DELETE /api/user/notifications/{uuid}；404 幂等成功。 */
    fun deleteNotification(uuid: String): Boolean = notifications.delete(uuid)

    // ============ V2 Profile / Tag / Collection domain ============
    // [Phase 3] 新 Java /api 契约：PUT /api/user/profile + /api/user/tags|collections
    // （uuid/colorHash/personMembers + 成员子接口）。

    fun patchProfile(name: String?, profile: ProfileDto?) = v2.patchProfile(name, profile)

    fun listTags(): List<TagDto> = v2.listTags()
    fun createTag(name: String, colorHash: String?, personMembers: List<String>?): String =
        v2.createTag(name, colorHash, personMembers)
    fun patchTag(uuid: String, name: String?, colorHash: String?) = v2.patchTag(uuid, name, colorHash)
    fun deleteTag(uuid: String): Boolean = v2.deleteTag(uuid)
    fun addTagMember(uuid: String, personUuid: String) = v2.addTagMember(uuid, personUuid)
    fun removeTagMember(uuid: String, personUuid: String) = v2.removeTagMember(uuid, personUuid)

    fun listCollections(): List<CollectionDto> = v2.listCollections()
    fun createCollection(name: String, description: String?, backgroundURL: String?, personMembers: List<String>?): String =
        v2.createCollection(name, description, backgroundURL, personMembers)
    fun patchCollection(uuid: String, name: String?, description: String?, backgroundURL: String?) =
        v2.patchCollection(uuid, name, description, backgroundURL)
    fun deleteCollection(uuid: String): Boolean = v2.deleteCollection(uuid)
    fun addCollectionMember(uuid: String, personUuid: String) = v2.addCollectionMember(uuid, personUuid)
    fun removeCollectionMember(uuid: String, personUuid: String) = v2.removeCollectionMember(uuid, personUuid)

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
