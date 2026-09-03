package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxOp
import top.mcxiafeng.badger.sync.OutboxOpType
import top.mcxiafeng.badger.sync.OutboxScheduler
import top.mcxiafeng.badger.sync.OutboxStore

/** Facade over the typed clients that implement the canonical `/api` surface. */
class ServerApi(
    baseUrl: String,
    private val http: okhttp3.OkHttpClient,
    private val tokenProvider: () -> String?,
    private val outboxStore: OutboxStore,
    private val outboxScheduler: OutboxScheduler,
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

    /**
     * [T14] CREATE 写意图 → Outbox 入队 + kick。实际 POST 由 SyncEngine.createOnPush 在重放时
     * 执行（幂等键复用 / 400 降级 / Synced 免 POST 都在那边裁决），本方法只负责意图落盘。
     *
     * @param localId 本地 `contacts_cache.id`（outbox 合并键）
     * @param clientUuid 客户端幂等键（首次创建生成并已落盘到 `serverId`）
     */
    fun enqueueCreatePerson(localId: Long, name: String, profile: ProfileDto?, clientUuid: String) {
        enqueueAndKick(
            EntityKind.PERSON, localId, clientUuid, OutboxOpType.CREATE,
            personPatchPayload(name, profile), "createPerson",
        )
    }

    /** 原始 HTTP：`POST /api/user/persons`（SyncEngine.createOnPush 专用，Repository 禁止直调）。 */
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String =
        person.createPerson(name, profile, clientUuid)

    /**
     * PUT /api/user/persons/{uuid} 的写意图 → Outbox 入队 + kick（T12b 起不再直推）。
     *
     * [A6 注记] 终态 seam 应命名在 Repository 层（「commit」）；本方法变 enqueue 是过渡
     * 形态，Phase 3 后随 A2 一并评估收缩。payload 只含非 null 字段（缺省 = 服务端「不更新」），
     * 同 `(PERSON, localId, PATCH)` 的半载 PUT 在 OutboxStore 内做字段级 merge（F4）。
     *
     * @param localId 本地 `contacts_cache.id`（outbox 合并键），由 Repository 传入。
     */
    fun updatePerson(localId: Long, uuid: String, name: String?, profile: ProfileDto?) {
        enqueueAndKick(
            EntityKind.PERSON, localId, uuid, OutboxOpType.PATCH,
            personPatchPayload(name, profile), "updatePerson",
        )
    }

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

    /** [T14] CREATE 写意图 → Outbox 入队 + kick（语义同 [enqueueCreatePerson]）。 */
    fun enqueueCreateTag(localId: Long, name: String, colorHash: String?, clientUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, clientUuid, OutboxOpType.CREATE,
            patchPayload {
                addProperty("name", name)
                colorHash?.takeIf { it.isNotBlank() }?.let { addProperty("colorHash", it) }
            },
            "createTag",
        )
    }

    /** 原始 HTTP：`POST /api/user/tags`（SyncEngine.createOnPush 专用；uuid=null 为 400 降级重试）。 */
    fun createTag(name: String, colorHash: String?, personMembers: List<String>?, uuid: String?): String =
        v2.createTag(name, colorHash, personMembers, uuid)

    /** PUT /api/user/tags/{uuid} → Outbox 入队 + kick（不再直推）。 */
    fun patchTag(localId: Long, uuid: String, name: String?, colorHash: String?) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.PATCH,
            patchPayload {
                name?.let { addProperty("name", it) }
                colorHash?.let { addProperty("colorHash", it) }
            },
            "patchTag",
        )
    }

    /** DELETE /api/user/tags/{uuid} → Outbox 入队 + kick（404 幂等成功由重放侧处理）。 */
    fun deleteTag(localId: Long, uuid: String) {
        enqueueAndKick(EntityKind.TAG, localId, uuid, OutboxOpType.DELETE, JsonObject(), "deleteTag")
    }

    fun addTagMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.MEMBER_ADD,
            memberPayload(personUuid), "addTagMember",
        )
    }

    fun removeTagMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.TAG, localId, uuid, OutboxOpType.MEMBER_REMOVE,
            memberPayload(personUuid), "removeTagMember",
        )
    }

    fun listCollections(): List<CollectionDto> = v2.listCollections()

    /** [T14] CREATE 写意图 → Outbox 入队 + kick（语义同 [enqueueCreatePerson]）。 */
    fun enqueueCreateCollection(
        localId: Long,
        name: String,
        description: String?,
        backgroundURL: String?,
        clientUuid: String,
    ) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, clientUuid, OutboxOpType.CREATE,
            patchPayload {
                addProperty("name", name)
                description?.let { addProperty("description", it) }
                backgroundURL?.let { addProperty("backgroundURL", it) }
            },
            "createCollection",
        )
    }

    /**
     * [T14] DELETE 写意图 → Outbox 入队 + kick。仅用于「本地新建未确认上云」的联系人删除：
     * 先 cancelEntity 取消未发 CREATE/PATCH 防复活，再入队 DELETE 兜底未知结局
     * （服务端可能已创建；404 = 从未创建，幂等成功）。已 Synced 联系人的删除仍走 commitDelete 直推。
     */
    fun enqueueDeletePerson(localId: Long, clientUuid: String) {
        enqueueAndKick(
            EntityKind.PERSON, localId, clientUuid, OutboxOpType.DELETE,
            JsonObject(), "deletePerson",
        )
    }

    /** 原始 HTTP：`POST /api/user/collections`（SyncEngine.createOnPush 专用；uuid=null 为 400 降级重试）。 */
    fun createCollection(
        name: String,
        description: String?,
        backgroundURL: String?,
        personMembers: List<String>?,
        uuid: String?,
    ): String = v2.createCollection(name, description, backgroundURL, personMembers, uuid)

    /** PUT /api/user/collections/{uuid} → Outbox 入队 + kick（不再直推）。 */
    fun patchCollection(localId: Long, uuid: String, name: String?, description: String?, backgroundURL: String?) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.PATCH,
            patchPayload {
                name?.let { addProperty("name", it) }
                description?.let { addProperty("description", it) }
                backgroundURL?.let { addProperty("backgroundURL", it) }
            },
            "patchCollection",
        )
    }

    /** DELETE /api/user/collections/{uuid} → Outbox 入队 + kick（404 幂等成功由重放侧处理）。 */
    fun deleteCollection(localId: Long, uuid: String) {
        enqueueAndKick(EntityKind.COLLECTION, localId, uuid, OutboxOpType.DELETE, JsonObject(), "deleteCollection")
    }

    fun addCollectionMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.MEMBER_ADD,
            memberPayload(personUuid), "addCollectionMember",
        )
    }

    fun removeCollectionMember(localId: Long, uuid: String, personUuid: String) {
        enqueueAndKick(
            EntityKind.COLLECTION, localId, uuid, OutboxOpType.MEMBER_REMOVE,
            memberPayload(personUuid), "removeCollectionMember",
        )
    }

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

    // ============ Outbox 重放（OutboxWorker 的分发端点） ============

    /**
     * 按 EntityKind / op 把一条 outbox 行重放为真实 HTTP 调用。
     *
     * 幂等性：PUT/PATCH 天然可安全重试；DELETE 404 视为幂等成功；MEMBER 子接口独立幂等。
     * CREATE（create-on-push）由 `SyncEngine.createOnPush` 接管（需要 DB identity 解析与
     * uuid 兑现回填），不经本方法——出现即编程错误，抛错走 recordFailure 暴露问题。
     */
    fun replayOutboxOp(op: OutboxOp) {
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

    // ============ Outbox 辅助 ============

    private fun enqueueAndKick(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: JsonObject,
        what: String,
    ) {
        val result = outboxStore.enqueue(entityKind, localId, remoteId, op, payload)
        outboxScheduler.kick()
        Log.d(TAG, "[$what] enqueued kind=${entityKind.name} localId=$localId remote=${remoteId?.take(8)} result=$result")
    }

    private fun personPatchPayload(name: String?, profile: ProfileDto?): JsonObject = patchPayload {
        name?.let { addProperty("name", it) }
        profile?.let { add("profile", it.toJsonObject()) }
    }

    private inline fun patchPayload(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)

    private fun memberPayload(personUuid: String): JsonObject = JsonObject().apply {
        addProperty("personUuid", personUuid)
    }

    private fun JsonObject.stringField(key: String): String? = stringOrNull(this, key)

    private fun JsonObject.objectField(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun requirePersonUuid(op: OutboxOp): String {
        val personUuid = op.payload.stringField("personUuid")
        if (personUuid.isNullOrBlank()) {
            throw ApiException(0, "outbox member op missing personUuid id=${op.id}", "outbox.replay")
        }
        return personUuid
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
