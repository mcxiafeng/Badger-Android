package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxOp
import top.mcxiafeng.badger.sync.OutboxOpType
import top.mcxiafeng.badger.sync.OutboxStore
import top.mcxiafeng.badger.utils.BadgerLog

abstract class ServerApiBase(
    protected val core: ApiCore,
    private val outboxStore: OutboxStore,
    private val kickScheduler: () -> Unit,
) : ServerApi {

    private val auth = AuthApi(core)
    private val ai = AiApi(core)
    private val resolver = ResolverApi(core)
    private val shortLink = ShortLinkApi(core)
    private val geo = GeoApi(core)
    private val notifications = NotificationApi(core)
    private val devices = DeviceApi(core)
    private val stats = StatsApi(core)
    private val v2 = V2DomainApi(core)
    private val person = PersonApi(core)
    private val sync = SyncApi(core)
    private val settings = SettingsApi(core)
    private val serverShortLink = ServerShortLinkApi(core)

    
    override fun setBaseUrl(newUrl: String) {
        if (newUrl == core.baseUrl) return
        BadgerLog.d(TAG, "setBaseUrl: ${core.baseUrl} -> $newUrl")
        core.baseUrl = newUrl
    }

    override fun listPersons(): List<PersonDto> = person.listPersons()
    override fun getPerson(uuid: String): PersonDto = person.getPerson(uuid)

    

    override fun enqueueCreatePerson(localId: Long, name: String, profile: ProfileDto?, clientUuid: String) {
        enqueueAndKick(
            EntityKind.PERSON, localId, clientUuid, OutboxOpType.CREATE,
            personPatchPayload(name, profile), "createPerson",
        )
    }

    
    override fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String =
        person.createPerson(name, profile, clientUuid)

    

    override fun updatePerson(localId: Long, uuid: String, name: String?, profile: ProfileDto?) {
        enqueueAndKick(
            EntityKind.PERSON, localId, uuid, OutboxOpType.PATCH,
            personPatchPayload(name, profile), "updatePerson",
        )
    }

    override fun deletePerson(uuid: String): Boolean = person.deletePerson(uuid)
    override fun mergePersons(targetUuid: String, mergedIds: List<String>): String = person.mergePersons(targetUuid, mergedIds)

    override fun syncSince(since: Long, limit: Int): SyncPage = sync.syncSince(since, limit)

    override fun register(
        username: String,
        email: String,
        password: String,
        passwordAgain: String,
        captchaId: String?,
        captchaCode: String?,
        emailCaptchaId: String?,
        emailCode: String?,
    ) = auth.register(username, email, password, passwordAgain, captchaId, captchaCode, emailCaptchaId, emailCode)

    override fun login(username: String, password: String, deviceId: String?, deviceName: String?): AuthResponse =
        auth.login(username, password, deviceId, deviceName)
    override fun refresh(): AuthResponse = auth.refresh()
    override fun logout() = auth.logout()
    override fun me(): JsonObject? = auth.me()
    override fun registerPolicy(): RegisterPolicy = auth.registerPolicy()
    override fun getCaptcha(): CaptchaResult = auth.getCaptcha()
    override fun sendVerificationCode(email: String, purpose: String): VerificationCodeResult = auth.sendVerificationCode(email, purpose)
    override fun forgotPassword(email: String, captchaId: String, captchaCode: String, newPassword: String, newPasswordAgain: String) =
        auth.forgotPassword(email, captchaId, captchaCode, newPassword, newPasswordAgain)
    override fun changePassword(oldPassword: String, newPassword: String, newPasswordAgain: String) =
        auth.changePassword(oldPassword, newPassword, newPasswordAgain)

    override fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> =
        ai.tagGenerate(bio, existingTagNames)
    override fun contactOcr(imageB64: String?, text: String?): ExtractedContact =
        ai.contactOcr(imageB64, text)

    override fun resolveIdentify(input: String): JsonObject? = resolver.resolveIdentify(input)
    override fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> = resolver.resolveIdentifyBatch(inputs)
    override fun platforms(): List<JsonObject> = resolver.platforms()

    override fun shortioList(): JsonObject = shortLink.shortioList()
    override fun shortioUpdate(linkId: String, newUrl: String): JsonObject = shortLink.shortioUpdate(linkId, newUrl)
    override fun shortioDomains(): JsonObject = shortLink.shortioDomains()
    override fun shortioCreate(originalUrl: String, domainId: Long?): JsonObject = shortLink.shortioCreate(originalUrl, domainId)

    override fun amapDistrict(adcode: String?): AmapDistrictPage = geo.district(adcode)

    override fun getUnreadNotificationCount(): Int = notifications.getUnreadCount()
    override fun listNotifications(): List<UserNotification> = notifications.listNotifications()
    override fun markNotificationRead(uuid: String) = notifications.markAsRead(uuid)
    override fun deleteNotification(uuid: String): Boolean = notifications.delete(uuid)

    override fun listDevices(): List<UserDevice> = devices.listDevices()
    override fun renameDevice(uuid: String, name: String) = devices.renameDevice(uuid, name)
    override fun deleteDevice(uuid: String): Boolean = devices.deleteDevice(uuid)

    override fun getStats(): UserStats? = stats.getStats()

    override fun patchProfile(name: String?, profile: ProfileDto?) = v2.patchProfile(name, profile)
    override fun getProfile(): UserProfileResponse = v2.getProfile()

    override fun uploadImage(fileBytes: ByteArray, fileName: String): String {
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
            
            val mb = fileBytes.size / 1048576.0
            val mbRounded = kotlin.math.round(mb * 10) / 10
            val mbText = if (mbRounded % 1.0 == 0.0) "${mbRounded.toInt()}.0" else "$mbRounded"
            throw ApiException(413, "图片大小 ${mbText}MB 超过 5MB 限制", "upload")
        }
        BadgerLog.d(TAG, "[$tag] uploadImage: name=$fileName bytes=${fileBytes.size} mime=$mime")
        return core.execute(core.multipartRequest("/api/user/upload", fileBytes, fileName, mime))
            .unwrapApiResult("upload", tag) { data ->
                val url = if (data is JsonObject) stringOrNull(data, "url").orEmpty() else ""
                if (url.isBlank()) throw ApiException(0, "upload missing url", "upload")
                url
            }
    }

    override fun listTags(): List<TagDto> = v2.listTags()

    
    override fun enqueueCreateTag(localId: Long, name: String, colorHash: String?, clientUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, clientUuid, OutboxOpType.CREATE,
            patchPayload {
                put("name", name)
                colorHash?.takeIf { it.isNotBlank() }?.let { put("colorHash", it) }
            },
            "createTag",
        )
    }

    
    override fun createTag(name: String, colorHash: String?, personMembers: List<String>?, uuid: String?): String =
        v2.createTag(name, colorHash, personMembers, uuid)

    
    override fun patchTag(localId: Long, uuid: String, name: String?, colorHash: String?) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.PATCH,
            patchPayload {
                name?.let { put("name", it) }
                colorHash?.let { put("colorHash", it) }
            },
            "patchTag",
        )
    }

    
    override fun deleteTag(localId: Long, uuid: String) {
        enqueueAndKick(EntityKind.TAG, localId, uuid, OutboxOpType.DELETE, JsonObject(emptyMap()), "deleteTag")
    }

    override fun addTagMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.MEMBER_ADD,
            memberPayload(personUuid), "addTagMember",
        )
    }

    override fun removeTagMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.MEMBER_REMOVE,
            memberPayload(personUuid), "removeTagMember",
        )
    }

    override fun listCollections(): List<CollectionDto> = v2.listCollections()

    
    override fun enqueueCreateCollection(
        localId: Long,
        name: String,
        description: String?,
        backgroundURL: String?,
        clientUuid: String,
    ) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, clientUuid, OutboxOpType.CREATE,
            patchPayload {
                put("name", name)
                description?.let { put("description", it) }
                backgroundURL?.let { put("backgroundURL", it) }
            },
            "createCollection",
        )
    }

    

    override fun enqueueDeletePerson(localId: Long, clientUuid: String) {
        enqueueAndKick(
            EntityKind.PERSON, localId, clientUuid, OutboxOpType.DELETE,
            JsonObject(emptyMap()), "deletePerson",
        )
    }

    
    override fun createCollection(
        name: String,
        description: String?,
        backgroundURL: String?,
        personMembers: List<String>?,
        uuid: String?,
    ): String = v2.createCollection(name, description, backgroundURL, personMembers, uuid)

    
    override fun patchCollection(localId: Long, uuid: String, name: String?, description: String?, backgroundURL: String?) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.PATCH,
            patchPayload {
                name?.let { put("name", it) }
                description?.let { put("description", it) }
                backgroundURL?.let { put("backgroundURL", it) }
            },
            "patchCollection",
        )
    }

    
    override fun deleteCollection(localId: Long, uuid: String) {
        enqueueAndKick(EntityKind.COLLECTION, localId, uuid, OutboxOpType.DELETE, JsonObject(emptyMap()), "deleteCollection")
    }

    override fun addCollectionMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.MEMBER_ADD,
            memberPayload(personUuid), "addCollectionMember",
        )
    }

    override fun removeCollectionMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.MEMBER_REMOVE,
            memberPayload(personUuid), "removeCollectionMember",
        )
    }

    override fun getUserSettings(): UserSettings = settings.getUserSettings()
    override fun updateUserSettings(
        language: String?,
        theme: String?,
        notifyEmail: Boolean?,
        shortLinkProvider: String?,
        shortioApiKey: String?,
        clearShortioApiKey: Boolean?,
    ) = settings.updateUserSettings(language, theme, notifyEmail, shortLinkProvider, shortioApiKey, clearShortioApiKey)

    override fun getShortLinkConfig(): ShortLinkConfig = serverShortLink.getConfig()
    override fun listServerShortLinks(): List<ServerShortLink> = serverShortLink.listLinks()
    override fun createServerShortLink(originalURL: String, code: String?): String = serverShortLink.createLink(originalURL, code)
    override fun updateServerShortLink(uuid: String, originalURL: String?, code: String?) =
        serverShortLink.updateLink(uuid, originalURL, code)
    override fun deleteServerShortLink(uuid: String): Boolean = serverShortLink.deleteLink(uuid)

    

    

    override fun replayOutboxOp(op: OutboxOp) {
        val remoteId = op.remoteId
            ?: throw ApiException(0, "outbox op missing remoteId id=${op.id}", "outbox.replay")
        when (op.entityKind) {
            EntityKind.PERSON -> when (op.op) {
                OutboxOpType.PATCH -> person.updatePerson(
                    remoteId,
                    name = op.payload.stringField("name"),
                    profile = op.payload.objectField("profile")?.let { ProfileDto.from(it) },
                )
                OutboxOpType.DELETE -> {
                    if (!person.deletePerson(remoteId)) {
                        throw ApiException(0, "deletePerson returned false id=${op.id}", "outbox.replay")
                    }
                }
                else -> throw ApiException(0, "unsupported person op ${op.op} id=${op.id}", "outbox.replay")
            }
            EntityKind.TAG -> when (op.op) {
                OutboxOpType.PATCH -> v2.patchTag(
                    remoteId,
                    name = op.payload.stringField("name"),
                    colorHash = op.payload.stringField("colorHash"),
                )
                OutboxOpType.DELETE -> v2.deleteTag(remoteId)
                OutboxOpType.MEMBER_ADD -> v2.addTagMember(remoteId, requirePersonUuid(op))
                OutboxOpType.MEMBER_REMOVE -> v2.removeTagMember(remoteId, requirePersonUuid(op))
                else -> throw ApiException(0, "unsupported tag op ${op.op} id=${op.id}", "outbox.replay")
            }
            EntityKind.COLLECTION -> when (op.op) {
                OutboxOpType.PATCH -> v2.patchCollection(
                    remoteId,
                    name = op.payload.stringField("name"),
                    description = op.payload.stringField("description"),
                    backgroundURL = op.payload.stringField("backgroundURL"),
                )
                OutboxOpType.DELETE -> v2.deleteCollection(remoteId)
                OutboxOpType.MEMBER_ADD -> v2.addCollectionMember(remoteId, requirePersonUuid(op))
                OutboxOpType.MEMBER_REMOVE -> v2.removeCollectionMember(remoteId, requirePersonUuid(op))
                else -> throw ApiException(0, "unsupported collection op ${op.op} id=${op.id}", "outbox.replay")
            }
        }
    }

    

    private fun enqueueAndKick(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: JsonObject,
        what: String,
    ) {
        
        
        
        val result = kotlinx.coroutines.runBlocking {
            outboxStore.enqueue(entityKind, localId, remoteId, op, payload)
        }
        kickScheduler()
        BadgerLog.d(TAG, "[$what] enqueued kind=${entityKind.name} localId=$localId remote=${remoteId?.take(8)} result=$result")
    }

    private fun personPatchPayload(name: String?, profile: ProfileDto?): JsonObject = patchPayload {
        name?.let { put("name", it) }
        profile?.let { put("profile", it.toJsonObject()) }
    }

    private inline fun patchPayload(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(build)

    private fun memberPayload(personUuid: String): JsonObject = buildJsonObject {
        put("personUuid", personUuid)
    }

    private fun JsonObject.stringField(key: String): String? = stringOrNull(this, key)

    private fun JsonObject.objectField(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun requirePersonUuid(op: OutboxOp): String {
        val personUuid = op.payload.stringField("personUuid")
        if (personUuid.isNullOrBlank()) {
            throw ApiException(0, "outbox member op missing personUuid id=${op.id}", "outbox.replay")
        }
        return personUuid
    }

    protected companion object {
        const val TAG = ApiCore.TAG
    }
}
