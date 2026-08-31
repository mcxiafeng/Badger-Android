package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject

/** Facade over the typed clients that implement the canonical `/api` surface. */
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
    private val notifications = NotificationApi(core)
    private val devices = DeviceApi(core)
    private val stats = StatsApi(core)
    private val v2 = V2DomainApi(core)
    private val person = PersonApi(core)
    private val sync = SyncApi(core)
    private val settings = SettingsApi(core)
    private val serverShortLink = ServerShortLinkApi(core)

    /** Update the base URL used by every subsequent request. */
    fun setBaseUrl(newUrl: String) {
        if (newUrl == baseUrl) return
        Log.d(TAG, "setBaseUrl: $baseUrl -> $newUrl")
        baseUrl = newUrl
        core.baseUrl = newUrl
    }

    fun listPersons(): List<PersonDto> = person.listPersons()
    fun getPerson(uuid: String): PersonDto = person.getPerson(uuid)
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String =
        person.createPerson(name, profile, clientUuid)
    fun updatePerson(uuid: String, name: String?, profile: ProfileDto?) = person.updatePerson(uuid, name, profile)
    fun deletePerson(uuid: String): Boolean = person.deletePerson(uuid)
    fun mergePersons(targetUuid: String, mergedIds: List<String>): String = person.mergePersons(targetUuid, mergedIds)

    fun syncSince(since: Long, limit: Int = 500): SyncPage = sync.syncSince(since, limit)

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
    fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult = auth.sendVerificationCode(email, purpose)
    fun forgotPassword(email: String, captchaId: String, captchaCode: String, newPassword: String, newPasswordAgain: String) =
        auth.forgotPassword(email, captchaId, captchaCode, newPassword, newPasswordAgain)
    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) =
        auth.changePassword(oldPassword, newPassword, newPasswordAgain)

    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> =
        ai.tagGenerate(bio, existingTagNames)
    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact =
        ai.contactOcr(imageB64, text)

    fun resolveIdentify(input: String): JsonObject? = resolver.resolveIdentify(input)
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> = resolver.resolveIdentifyBatch(inputs)
    fun platforms(): List<JsonObject> = resolver.platforms()

    fun shortioList(): JsonObject = shortLink.shortioList()
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject = shortLink.shortioUpdate(linkId, newUrl)
    fun shortioDomains(): JsonObject = shortLink.shortioDomains()
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject = shortLink.shortioCreate(originalUrl, domainId)

    fun getUnreadNotificationCount(): Int = notifications.getUnreadCount()
    fun listNotifications(): List<UserNotification> = notifications.listNotifications()
    fun markNotificationRead(uuid: String) = notifications.markAsRead(uuid)
    fun deleteNotification(uuid: String): Boolean = notifications.delete(uuid)

    fun listDevices(): List<UserDevice> = devices.listDevices()
    fun renameDevice(uuid: String, name: String) = devices.renameDevice(uuid, name)
    fun deleteDevice(uuid: String): Boolean = devices.deleteDevice(uuid)

    fun getStats(): UserStats? = stats.getStats()

    fun patchProfile(name: String?, profile: ProfileDto?) = v2.patchProfile(name, profile)
    fun getProfile(): UserProfileResponse = v2.getProfile()

    fun uploadImage(fileBytes: ByteArray, fileName: String): String {
        val tag = core.nextCallTag()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> throw ApiException(400, "不支持的图片格式: .$ext", "upload")
        }
        if (fileBytes.size > 5 * 1024 * 1024) {
            throw ApiException(413, "图片大小 ${"%.1f".format(fileBytes.size / 1048576.0)}MB 超过 5MB 限制", "upload")
        }
        Log.d(TAG, "[$tag] uploadImage: name=$fileName bytes=${fileBytes.size} mime=$mime")
        return core.execute(core.buildMultipartRequest("/api/user/upload", fileBytes, fileName, mime).build())
            .unwrapApiResult("upload", tag) { data ->
                val url = if (data.isJsonObject) stringOrNull(data.asJsonObject, "url").orEmpty() else ""
                if (url.isBlank()) throw ApiException(0, "upload missing url", "upload")
                url
            }
    }

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

    fun getUserSettings(): UserSettings = settings.getUserSettings()
    fun updateUserSettings(
        language: String? = null,
        theme: String? = null,
        notifyEmail: Boolean? = null,
        shortLinkProvider: String? = null,
        shortioApiKey: String? = null,
        clearShortioApiKey: Boolean? = null,
    ) = settings.updateUserSettings(language, theme, notifyEmail, shortLinkProvider, shortioApiKey, clearShortioApiKey)

    fun getShortLinkConfig(): ShortLinkConfig = serverShortLink.getConfig()
    fun listServerShortLinks(): List<ServerShortLink> = serverShortLink.listLinks()
    fun createServerShortLink(originalURL: String, code: String? = null): String = serverShortLink.createLink(originalURL, code)
    fun updateServerShortLink(uuid: String, originalURL: String? = null, code: String? = null) =
        serverShortLink.updateLink(uuid, originalURL, code)
    fun deleteServerShortLink(uuid: String): Boolean = serverShortLink.deleteLink(uuid)

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
