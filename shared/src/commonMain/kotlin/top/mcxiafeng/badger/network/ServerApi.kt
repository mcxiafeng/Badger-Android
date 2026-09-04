package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxOp

/**
 * [KMP K08-B] `/api` 网络面的契约接口（commonMain）。
 *
 * 实现类 `OkHttpServerApi`（app network 包，OkHttp 传输，Q2 裁决 OkHttp 无 iOS 变体）。
 * repository/sync 依赖本接口——为业务层迁 commonMain 提供编译可见的类型边界；
 * Ktor suspend 化（KtorHttpCore 接线）在实现内部进行，接口签名届时一并 suspend。
 *
 * 语义文档（幂等/Outbox 入队等）见实现类各方法注释。
 */
interface ServerApi {
    fun setBaseUrl(newUrl: String)

    // ============ Person ============
    fun listPersons(): List<PersonDto>
    fun getPerson(uuid: String): PersonDto
    fun enqueueCreatePerson(localId: Long, name: String, profile: ProfileDto?, clientUuid: String)
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String
    fun updatePerson(localId: Long, uuid: String, name: String?, profile: ProfileDto?)
    fun deletePerson(uuid: String): Boolean
    fun mergePersons(targetUuid: String, mergedIds: List<String>): String
    fun enqueueDeletePerson(localId: Long, clientUuid: String)

    // ============ Sync ============
    fun syncSince(since: Long, limit: Int = 500): SyncPage
    fun replayOutboxOp(op: OutboxOp)

    // ============ Auth ============
    fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    )
    fun login(username: String, password: String, deviceId: String? = null, deviceName: String? = null): AuthResponse
    fun refresh(): AuthResponse
    fun logout()
    fun me(): JsonObject?
    fun registerPolicy(): RegisterPolicy
    fun getCaptcha(): CaptchaResult
    fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult
    fun forgotPassword(email: String, captchaId: String, captchaCode: String, newPassword: String, newPasswordAgain: String)
    fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String)

    // ============ AI ============
    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate>
    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact

    // ============ Resolver ============
    fun resolveIdentify(input: String): JsonObject?
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?>
    fun platforms(): List<JsonObject>

    // ============ short.io ============
    fun shortioList(): JsonObject
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject
    fun shortioDomains(): JsonObject
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject

    // ============ Notifications ============
    fun getUnreadNotificationCount(): Int
    fun listNotifications(): List<UserNotification>
    fun markNotificationRead(uuid: String)
    fun deleteNotification(uuid: String): Boolean

    // ============ Devices ============
    fun listDevices(): List<UserDevice>
    fun renameDevice(uuid: String, name: String)
    fun deleteDevice(uuid: String): Boolean

    // ============ Stats ============
    fun getStats(): UserStats?

    // ============ V2 Profile / Tags / Collections ============
    fun patchProfile(name: String?, profile: ProfileDto?)
    fun getProfile(): UserProfileResponse
    fun listTags(): List<TagDto>
    fun enqueueCreateTag(localId: Long, name: String, colorHash: String?, clientUuid: String)
    fun createTag(name: String, colorHash: String?, personMembers: List<String>?, uuid: String?): String
    fun patchTag(localId: Long, uuid: String, name: String?, colorHash: String?)
    fun deleteTag(localId: Long, uuid: String)
    fun addTagMember(localId: Long, uuid: String, personUuid: String)
    fun removeTagMember(localId: Long, uuid: String, personUuid: String)
    fun listCollections(): List<CollectionDto>
    fun enqueueCreateCollection(
        localId: Long,
        name: String,
        description: String?,
        backgroundURL: String?,
        clientUuid: String,
    )
    fun createCollection(
        name: String,
        description: String?,
        backgroundURL: String?,
        personMembers: List<String>?,
        uuid: String?,
    ): String
    fun patchCollection(localId: Long, uuid: String, name: String?, description: String?, backgroundURL: String?)
    fun deleteCollection(localId: Long, uuid: String)
    fun addCollectionMember(localId: Long, uuid: String, personUuid: String)
    fun removeCollectionMember(localId: Long, uuid: String, personUuid: String)

    // ============ Settings / ServerShortLink ============
    fun getUserSettings(): UserSettings
    fun updateUserSettings(
        language: String? = null,
        theme: String? = null,
        notifyEmail: Boolean? = null,
        shortLinkProvider: String? = null,
        shortioApiKey: String? = null,
        clearShortioApiKey: Boolean? = null,
    )
    fun getShortLinkConfig(): ShortLinkConfig
    fun listServerShortLinks(): List<ServerShortLink>
    fun createServerShortLink(originalURL: String, code: String? = null): String
    fun updateServerShortLink(uuid: String, originalURL: String? = null, code: String? = null)
    fun deleteServerShortLink(uuid: String): Boolean

    // ============ Upload ============
    fun uploadImage(fileBytes: ByteArray, fileName: String): String
}
