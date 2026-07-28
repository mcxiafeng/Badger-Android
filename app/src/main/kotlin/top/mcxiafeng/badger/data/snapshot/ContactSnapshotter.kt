package top.mcxiafeng.badger.data.snapshot

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity

/**
 * [V2-P3] 联系人完整快照序列化器。
 *
 * 单一职责:把 `ContactCacheEntity` + 关联子表(平台 / 字段值 / 标签)打包成
 * 可入 [OperationHistoryEntity.snapshotBeforeJson] / [OperationHistoryEntity.snapshotAfterJson]
 * 的 JSON 字符串,以及反向还原。
 *
 * 与 [top.mcxiafeng.badger.data.repository.ContactMapper] 的区别:
 * - `ContactMapper` 只搬 V2 cache entity ↔ UI 包装类,体积小、字段缺省值少。
 * - `ContactSnapshotter` 装**完整状态**(包括关联子表),用于撤销 / 重发的"快照回滚"。
 *   体积较大但有版本号,且仅在写 history 时使用,不入常态 Flow。
 *
 * 设计要点(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5.1 第 12 行 + §6.4):
 * 1. **不存头像二进制**:只存 `avatarUrl`(远端 URL)与可能的 `avatarPath`(本地磁盘路径)。
 *    history 表可能膨胀,但不会因 base64 失控。
 * 2. **version 字段**:envelope 写 `version: 1`,未来字段扩展兼容旧 history。
 * 3. **容错**:
 *    - [toJson] / [fromJson] 任何异常都会被捕获并降级(空快照),避免 P5/P8 阶段
 *      写 history 时因 Gson 崩导致无法入队 = 丢消息。
 *    - 空联系人 / 缺关联子表都按"最小可序列化"输出。
 * 4. **可观测**:每个入口都打 `Log.d("Snapshotter", ...)` 标注 toJson / fromJson 的输入规模。
 *
 * [修复防御]:[fromJson] 还原时仅返回 snapshot 数据本身,**不**写 DB。调用方
 * (`HistoryRepository.undo`)负责协调 `contactCacheDao.upsert(before)` 等落库动作;
 * 本类不直接持有 DAO 的写权限,降低一处崩盘牵连多表的风险。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::ContactSnapshotter)`。
 * `@ApplicationContext` 在 Koin module 里用 `get()` + 类型映射保证。
 */
class ContactSnapshotter(
    private val context: Context,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val tagCacheDao: TagCacheDao,
) {

    private val gson: Gson = Gson()

    // ============ toJson: 现场 → JSON ============

    /**
     * 从 contactCacheDao 现读联系人 + 关联子表,序列化为 JSON。
     *
     * 用于:
     * - P5 [ContactRepositoryImpl.optimisticUpdate] 入 history 前 dump 当前状态。
     * - P6 [commitDelete] 入 history 的 snapshotBeforeJson。
     *
     * @return JSON 字符串;若联系人不存在或序列化失败,返回空对象的 JSON(保证 history
     *   的 snapshotBeforeJson 列非空,NPE 兜底)。
     */
    suspend fun toJsonFromCache(contactId: Long, capturedAt: Long = System.currentTimeMillis()): String {
        val contact = contactCacheDao.getContactById(contactId)
        if (contact == null) {
            Log.w("Tester", "ContactSnapshotter.toJsonFromCache: contact $contactId not found, fallback to empty snapshot")
            return gson.toJson(ContactSnapshot.empty(contactId, capturedAt))
        }
        val snapshot = buildSnapshot(contact, capturedAt)
        return try {
            val json = gson.toJson(snapshot)
                        json
        } catch (e: Exception) {
            Log.e("Tester", "ContactSnapshotter.toJsonFromCache: serialize failed for $contactId", e)
            gson.toJson(ContactSnapshot.empty(contactId, capturedAt))
        }
    }

    /**
     * 把已持有的 [ContactCacheEntity] + 关联子表(由调用方组装)序列化为 JSON。
     *
     * 用于"事务性"场景:P5 入队 PendingUpload 前,我们刚 bumpContact 但又想 capture
     * 一个固定一致的 snapshot(不想中间被并发的二次 update 污染)。
     */
    suspend fun toJson(
        contact: ContactCacheEntity,
        platforms: List<ContactPlatformCacheEntity>? = null,
        fieldValues: List<ContactFieldValueCacheEntity>? = null,
        capturedAt: Long = System.currentTimeMillis(),
    ): String {
        // [修复防御]:即使调用方传入 null,我们依然从 cache 现场补一次,保证 snapshot 完整。
        val resolvedPlatforms = platforms ?: contactPlatformCacheDao.getPlatformsByContact(contact.id)
        val resolvedFields = fieldValues ?: contactFieldValueCacheDao.getFieldValuesByContactOnce(contact.id)
        return try {
            val snapshot = buildSnapshot(
                contact = contact,
                capturedAt = capturedAt,
                platforms = resolvedPlatforms,
                fieldValues = resolvedFields,
            )
            val json = gson.toJson(snapshot)
                        json
        } catch (e: Exception) {
            Log.e("Tester", "ContactSnapshotter.toJson: serialize failed for ${contact.id}", e)
            gson.toJson(ContactSnapshot.empty(contact.id, capturedAt))
        }
    }

    // ============ fromJson: JSON → 实体 ============

    /**
     * [V2-P8] 把服务端 ContactResponse.serverContact(JsonObject 直接 dump)适配成 [RestoredContact]。
     *
     * 与 [fromJson] 的区别:
     * - [fromJson] 解析 ContactSnapshot envelope 格式(`{version:1, captured_at, contact_id, ...}`)
     *   — 由 [toJsonFromCache] / [toJson] 产生,来自客户端 history snapshot。
     * - [fromServerContact] 解析服务端 409 Conflict 响应里的 `server_contact` 字段
     *   (JsonObject,格式是 `{id, name, bio, avatar_url, pinyin_initial, server_version, platforms: {...}}`),
     *   无 envelope 包装。本函数负责把它包装成 ContactSnapshot 再走 [fromJson] 复用现有逻辑。
     *
     * 缺失字段兜底:
     * - `id` / `server_id` 缺失 → 用 caller 传入的 contactId
     * - `platforms` 缺失 → 空 map
     * - `field_values` / `tags` → 兜底空(CONFLICT 响应通常不带)
     *
     * [修复防御]:解析失败(空 / 损坏 JSON)走 notFound 兜底,与 [fromJson] 同样不抛异常。
     */
    suspend fun fromServerContact(serverContactJson: String?, contactId: Long): RestoredContact {
        if (serverContactJson.isNullOrBlank() || serverContactJson == "null") {
            Log.w("Tester", "ContactSnapshotter.fromServerContact: empty/null json for contactId=$contactId")
            return RestoredContact.notFound(contactId)
        }
        return try {
            val obj = JsonParser.parseString(serverContactJson).asJsonObject
            val now = System.currentTimeMillis()

            // [修复防御]:服务端 platforms 字段可能用 snake_case 键,也可能直接是平台 map。
            // 我们按 platforms Map 解析;缺失 → 空 map。
            val platformMap = mutableMapOf<String, SnapshotPlatformEntry>()
            obj.getAsJsonObject("platforms")?.entrySet()?.forEach { (key, value) ->
                if (value.isJsonObject) {
                    val p = value.asJsonObject
                    platformMap[key] = SnapshotPlatformEntry(
                        value = p.get("value")?.takeIf { !it.isJsonNull }?.asString,
                        displayName = p.get("display_name")?.takeIf { !it.isJsonNull }?.asString,
                        jumpLink = p.get("jump_link")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        originalLink = p.get("original_link")?.takeIf { !it.isJsonNull }?.asString,
                        avatarUrl = p.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString,
                    )
                }
            }

            val wrapped = ContactSnapshot(
                version = ContactSnapshot.SNAPSHOT_VERSION,
                capturedAt = now,
                contactId = contactId,
                serverId = obj.get("server_id")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                note = obj.get("note")?.takeIf { !it.isJsonNull }?.asString,
                bio = obj.get("bio")?.takeIf { !it.isJsonNull }?.asString,
                avatarUrl = obj.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString,
                pinyinInitial = obj.get("pinyin_initial")?.takeIf { !it.isJsonNull }?.asString ?: "",
                serverVersion = obj.get("server_version")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                lastSyncedAt = now,
                isLocalOnly = false,
                isDeleted = obj.get("is_deleted")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                platforms = platformMap,
                fieldValues = emptyList(),
                tags = emptyList(),
            )
            // 走 fromJson 复用现有"envelope → RestoredContact"逻辑
            val wrappedJson = gson.toJson(wrapped)
            val result = fromJson(wrappedJson, contactId)
                        result
        } catch (e: JsonSyntaxException) {
            Log.e("Tester", "ContactSnapshotter.fromServerContact: JsonSyntaxException for contactId=$contactId", e)
            RestoredContact.notFound(contactId)
        } catch (e: Exception) {
            Log.e("Tester", "ContactSnapshotter.fromServerContact: unexpected error for contactId=$contactId", e)
            RestoredContact.notFound(contactId)
        }
    }

    /**
     * 把 history 中存的 snapshotBeforeJson 还原成 [RestoredContact]。
     *
     * 返回值包含:
     * - `contact: ContactCacheEntity` — 撤销时 upsert 即可恢复主表;
     * - `platforms: List<ContactPlatformCacheEntity>` — 由 caller 决定如何替换
     *   (`deleteByContactAndKey` 循环 upsert,或整体 REPLACE);
     * - `fieldValues: List<ContactFieldValueCacheEntity>` — 同上;
     * - `tags: List<SnapshotTagRef>` — 同上(撤销时按 contactId 重建 contact_tag_cache 行)。
     *
     * [修复防御]:JSON 异常 / 空字符串 / 缺字段都不能让 P8 undo 失败;这里统一降级为
     * `RestoredContact.notFound(...)`,由 caller 决定如何 fallback。
     *
     * [修复防御]:返回 [RestoredContact],内部 [SnapshotTagRef] 字段在文档中标注是
     * snapshot 纯数据载体,允许 caller 读取但不应修改其结构。
     */
    suspend fun fromJson(json: String?, contactId: Long): RestoredContact {
        if (json.isNullOrBlank() || json == "null") {
            Log.w("Tester", "ContactSnapshotter.fromJson: empty/null json for contactId=$contactId")
            return RestoredContact.notFound(contactId)
        }
        return try {
            val parsed = gson.fromJson(json, ContactSnapshot::class.java)
            if (parsed == null) {
                Log.w("Tester", "ContactSnapshotter.fromJson: parsed null for contactId=$contactId")
                return RestoredContact.notFound(contactId)
            }
            val contact = parsed.toCacheEntity()
            val platforms = parsed.platforms.entries.map { (platformKey, entry) ->
                entry.toCacheEntity(contactId = contact.id, platformKey = platformKey)
            }
            val fieldValues = parsed.fieldValues.map { it.toCacheEntity(contactId = contact.id) }
                        RestoredContact(
                contact = contact,
                platforms = platforms,
                fieldValues = fieldValues,
                tags = parsed.tags,
            )
        } catch (e: JsonSyntaxException) {
            Log.e("Tester", "ContactSnapshotter.fromJson: JsonSyntaxException for contactId=$contactId", e)
            RestoredContact.notFound(contactId)
        } catch (e: Exception) {
            Log.e("Tester", "ContactSnapshotter.fromJson: unexpected error for contactId=$contactId", e)
            RestoredContact.notFound(contactId)
        }
    }

    // ============ 内部构建 ============

    private suspend fun buildSnapshot(
        contact: ContactCacheEntity,
        capturedAt: Long,
        platforms: List<ContactPlatformCacheEntity>? = null,
        fieldValues: List<ContactFieldValueCacheEntity>? = null,
    ): ContactSnapshot {
        val resolvedPlatforms = platforms ?: contactPlatformCacheDao.getPlatformsByContact(contact.id)
        val resolvedFields = fieldValues ?: contactFieldValueCacheDao.getFieldValuesByContactOnce(contact.id)
        val resolvedCrossRefs = contactTagCacheDao.getTagIdsByContact(contact.id)
        val tagMap: Map<Long, String> = if (resolvedCrossRefs.isNotEmpty()) {
            tagCacheDao.searchTagsByIds(resolvedCrossRefs).associate { it.id to it.name }
        } else emptyMap()

        val platformMap = resolvedPlatforms.associate { it.platformKey to it.toSnapshotEntry() }
        val fieldValueList = resolvedFields.map { it.toSnapshotEntry() }
        val tagList = resolvedCrossRefs.map { tagId ->
            // [修复防御]:tag 已被人工删除时,crossRef 行还在但 tagCacheDao 返回空。
            // 用 "<deleted:tagId=$tagId>" 占位,UI 渲染时不会显示空字符串。
            SnapshotTagRef(
                tagId = tagId,
                name = tagMap[tagId] ?: "<deleted:tagId=$tagId>",
            )
        }

        return ContactSnapshot(
            capturedAt = capturedAt,
            contactId = contact.id,
            serverId = contact.serverId,
            name = contact.name,
            note = contact.note,
            bio = contact.bio,
            avatarUrl = contact.avatarUrl,
            pinyinInitial = contact.pinyinInitial,
            serverVersion = contact.serverVersion,
            lastSyncedAt = contact.lastSyncedAt,
            isLocalOnly = contact.isLocalOnly,
            isDeleted = contact.isDeleted,
            platforms = platformMap,
            fieldValues = fieldValueList,
            tags = tagList,
        )
    }
}

/**
 * [ContactSnapshotter.fromJson] 公开返回类型。
 *
 * 包含还原 [ContactCacheEntity] 主体 + 关联子表(platforms / fieldValues / tags)。
 * Caller 拿到此对象后,直接走 `contactCacheDao.upsert(...)` + `contactPlatformCacheDao.insertPlatforms(...)`
 * + `contactFieldValueCacheDao.insertOrUpdateFieldValues(...)` 即可整体回滚。
 *
 * 内部快照字段 [SnapshotTagRef] 是 internal [data class],public `RestoredContact.tags`
 * 字段暴露它会触发 "public function exposes internal" 编译错误 — 这里通过在文件
 * 顶层用 [internal] `tags` 不可行(数据类字段不能降级),所以**整体保持 public,允许
 * caller 看到 `List<SnapshotTagRef>`**。`SnapshotTagRef` 是纯数据载体,字段语义稳定,
 * 暴露成 public 不会引入隐式契约(Caller 只能读,不能通过它操作 snapshot 序列化)。
 */
data class RestoredContact(
    val contact: ContactCacheEntity,
    val platforms: List<ContactPlatformCacheEntity>,
    val fieldValues: List<ContactFieldValueCacheEntity>,
    val tags: List<SnapshotTagRef>,
) {
    companion object {
        /**
         * 还原失败的兜底:caller 拿到 notFound 后应**跳过撤销**而不是抛错,否则
         * history 上的撤销按钮会让 App 崩。
         */
        fun notFound(contactId: Long): RestoredContact = RestoredContact(
            contact = ContactCacheEntity(
                id = contactId,
                name = "",
                createTime = 0L,
                updateTime = 0L,
            ),
            platforms = emptyList(),
            fieldValues = emptyList(),
            tags = emptyList(),
        )
    }
}

// ============ 实体 ↔ 快照互转 扩展函数 ============

private fun ContactSnapshot.toCacheEntity(): ContactCacheEntity = ContactCacheEntity(
    id = contactId,
    serverId = serverId,
    name = name,
    avatarUrl = avatarUrl,
    avatarPath = null, // [修复防御]:avatarPath 是本地磁盘路径,snapshot 不存储(防 history 膨胀)
    note = note,
    bio = bio,
    pinyinInitial = pinyinInitial,
    platformsJson = "", // [修复防御]:由 caller 走 ContactMapper.encodePlatformsMap 重新序列化
    createTime = capturedAt,
    updateTime = capturedAt,
    serverVersion = serverVersion,
    lastSyncedAt = lastSyncedAt,
    isLocalOnly = isLocalOnly,
    isDeleted = isDeleted,
)

private fun SnapshotPlatformEntry.toCacheEntity(contactId: Long): ContactPlatformCacheEntity =
    ContactPlatformCacheEntity(
        contactId = contactId,
        platformKey = "", // [修复防御]:由 caller 在循环里补 platformKey(本类实体已丢失)
        value = value,
        displayName = displayName,
        jumpLink = jumpLink,
        originalLink = originalLink,
        avatarUrl = avatarUrl,
        serverVersion = 0L,
        isLocalOnly = true,
    )

private fun ContactPlatformCacheEntity.toSnapshotEntry(): SnapshotPlatformEntry =
    SnapshotPlatformEntry(
        value = value,
        displayName = displayName,
        jumpLink = jumpLink,
        originalLink = originalLink,
        avatarUrl = avatarUrl,
    )

private fun ContactFieldValueCacheEntity.toSnapshotEntry(): SnapshotFieldValue =
    SnapshotFieldValue(
        id = id,
        fieldId = fieldId,
        customFieldId = customFieldId,
        value = value,
        displayOrder = displayOrder,
    )

private fun SnapshotFieldValue.toCacheEntity(contactId: Long): ContactFieldValueCacheEntity =
    ContactFieldValueCacheEntity(
        id = 0L, // [修复防御]:id 必须重新生成,否则会撞旧 id
        contactId = contactId,
        fieldId = fieldId,
        customFieldId = customFieldId,
        value = value,
        displayOrder = displayOrder,
        createTime = System.currentTimeMillis(),
        updateTime = System.currentTimeMillis(),
        serverVersion = 0L,
        isLocalOnly = true,
    )

// ============ PlatformEntry ↔ SnapshotPlatformEntry 互转 ============

/**
 * 为 caller 提供一个把 snapshot 里的 platformKey 重新注入的工具(见 [SnapshotPlatformEntry.toCacheEntity]
 * 的 [修复防御])。
 */
internal fun SnapshotPlatformEntry.toCacheEntity(contactId: Long, platformKey: String): ContactPlatformCacheEntity =
    ContactPlatformCacheEntity(
        contactId = contactId,
        platformKey = platformKey,
        value = value,
        displayName = displayName,
        jumpLink = jumpLink,
        originalLink = originalLink,
        avatarUrl = avatarUrl,
        serverVersion = 0L,
        isLocalOnly = true,
    )

/**
 * 用 ContactCacheEntity 里存的 [top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity.platformsJson]
 * (V2 折叠 Map<String, PlatformEntry>)直接拆出 platformsMap,
 * 配合 [ContactSnapshotter] 写 snapshotBeforeJson 时不会丢这部分数据。
 */
internal fun Collection<ContactPlatformCacheEntity>.toPlatformsMap(): Map<String, PlatformEntry> =
    associate { it.platformKey to it.toPlatformEntry() }

private fun ContactPlatformCacheEntity.toPlatformEntry(): PlatformEntry = PlatformEntry(
    displayName = displayName,
    jumpLink = jumpLink,
    originalLink = originalLink,
    value = value,
    avatarUrl = avatarUrl,
)