package top.mcxiafeng.badger.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap
import top.mcxiafeng.badger.data.repository.ContactMapper.encodePlatformsMap
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactField
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactWithFields
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldDisplay
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldValue
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatform
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * [Phase 3] 联系人仓库 — 直推直删（服务端权威同步）。
 *
 * 写操作**直推** `POST/PUT/DELETE /api/user/persons`，不再走 op 队列（退役同步引擎）。
 *
 * 关键语义变化（对齐 `docs/api-handover-migration-plan.md` §C2/C3）：
 * - **uuid 幂等重放**：新建时客户端生成 uuid 携带，服务端返回既有行（超时/重试不产生克隆体）；
 * - **本地兜底**：离线直推失败 → 落 `isLocalOnly=true` 行，下次编辑/同步时 create-on-push
 *   （[ensureServerUuid] 客户端生成新 uuid 幂等重放）——这是有日志的降级，不吞根因；
 * - **commitDelete**：软删(UI 立即隐藏) → 直发 DELETE → 200/404 硬删；失败恢复软删（可重试），
 *   selfPerson 400 原样抛；
 * - **commitMerge**：直调 `POST /api/user/persons/{uuid}/merge`，merged 硬删、target 保留。
 *
 * [§14.2] Koin `singleOf(::ContactRepositoryImpl) { bind<ContactRepository>() }`。
 */
class ContactRepositoryImpl(
    private val contactCacheDao: ContactCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val serverApi: ServerApi,
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<ContactCacheEntity>> = contactCacheDao.getAllContacts()

    override fun getLetterIndex(): Flow<List<LetterCount>> = contactCacheDao.getLetterIndex()

    override suspend fun getContactById(id: Long): ContactCacheEntity? = withContext(Dispatchers.IO) {
        contactCacheDao.getContactById(id)
    }

    override fun getAllContactsWithFields(): Flow<List<ContactWithFields>> {
        return contactCacheDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                contact.toContactWithFields(emptyList())
            }
        }
    }

    override suspend fun getContactWithFieldsById(id: Long): ContactWithFields? = withContext(Dispatchers.IO) {
        val contact = contactCacheDao.getContactById(id) ?: return@withContext null
        val fieldValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(id)

        val fieldIds = fieldValues.mapNotNull { it.fieldId }.distinct()

        val fieldMap = if (fieldIds.isNotEmpty()) {
            contactFieldCacheDao.getFieldsByIds(fieldIds).filter { it.isEnabled }
                .associate { it.id to it.toContactField() }
        } else emptyMap()

        val fields = fieldValues.mapNotNull { value ->
            if (value.fieldId != null) {
                val field = fieldMap[value.fieldId] ?: return@mapNotNull null
                value.toFieldDisplay(
                    fieldName = field.fieldName,
                    fieldKey = field.fieldKey,
                    icon = field.icon,
                    sortOrder = field.sortOrder,
                )
            } else null
        }.sortedBy { it.sortOrder }

        contact.toContactWithFields(fields)
    }

    /**
     * 新建联系人：直推 `POST /api/user/persons`（客户端 uuid 幂等重放）。
     *
     * 流程：生成客户端 uuid → 直推创建（成功拿到服务端 uuid）→ 落本地行
     * `serverId=服务端uuid, isLocalOnly=false`。离线失败 → 落 `isLocalOnly=true`
     * 本地兜底行（有日志），下次编辑走 [ensureServerUuid] create-on-push。
     */
    override suspend fun insertContact(contact: ContactCacheEntity): Long = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        val clientUuid = UUID.randomUUID().toString()
        val serverUuid = try {
            serverApi.createPerson(withPinyin.name, buildProfile(withPinyin, emptyList()), clientUuid)
        } catch (e: Exception) {
            // [修复防御]: 离线直推失败 → 本地兜底(不丢用户扫描数据);isLocalOnly 标记让
            // 下次编辑 create-on-push 补推。有日志的降级,不是静默吞错。
            Log.e("Tester", "insertContact: createPerson 失败,落本地 isLocalOnly 兜底 name=${withPinyin.name}", e)
            null
        }
        val newId = contactCacheDao.insertContact(
            withPinyin.copy(
                serverId = serverUuid,
                isLocalOnly = serverUuid == null,
            )
        )
        contactCacheDao.bumpContact(newId)
        newId
    }

    /**
     * 更新联系人：本地落库 + 直推 `PUT /api/user/persons/{uuid}`。
     *
     * 改名后重算 pinyinInitial（[normalizePinyinInitial] 契约不变）。
     * 本地行为最终一致源：直推失败仅记日志（服务端权威，下次 sync 会覆盖）；name/profile 一起推。
     */
    override suspend fun updateContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        val normalized = contact.copy(
            pinyinInitial = normalizePinyinInitial(contact.name, contact.pinyinInitial)
        )
        val existing = contactCacheDao.getContactById(contact.id)
        if (existing == null) {
            // [修复防御]: 联系人已被删 / 不存在 — 本地兜底 update,不崩上层。
            contactCacheDao.updateContact(normalized)
            contactCacheDao.bumpContact(normalized.id)
            return@withContext
        }
        contactCacheDao.updateContact(normalized)
        contactCacheDao.bumpContact(normalized.id)
        val serverUuid = ensureServerUuid(normalized) ?: run {
            Log.w("Tester", "updateContact: id=${contact.id} 无 serverId 且 create-on-push 失败,本地已保存")
            return@withContext
        }
        try {
            serverApi.updatePerson(serverUuid, name = normalized.name, profile = buildProfile(normalized, null))
        } catch (e: Exception) {
            // [修复防御]: 直推失败不吞——记日志并保留本地态,待下次 sync/编辑重推。
            Log.w("Tester", "updateContact: id=${contact.id} PUT 失败(本地已保存)", e)
        }
    }

    override suspend fun updateContactBio(contactId: Long, bio: String?) = withContext(Dispatchers.IO) {
        val existing = contactCacheDao.getContactById(contactId) ?: return@withContext
        if (existing.bio == bio) return@withContext
        val updated = existing.copy(bio = bio, updateTime = System.currentTimeMillis())
        contactCacheDao.updateContact(updated)
        contactCacheDao.bumpContact(contactId)
        val serverUuid = ensureServerUuid(updated) ?: return@withContext
        try {
            serverApi.updatePerson(serverUuid, name = null, profile = buildProfile(updated, null))
        } catch (e: Exception) {
            Log.w("Tester", "updateContactBio: contactId=$contactId PUT 失败(本地已保存)", e)
        }
    }

    override suspend fun deleteContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        contactCacheDao.deleteByIds(listOf(contact.id))
    }

    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        contactCacheDao.deleteByIds(ids)
    }

    // ========== [Phase 3] commitDelete / commitMerge 直推 ==========

    /**
     * 直推删除：软删(UI 立即隐藏) → `DELETE /api/user/persons/{uuid}`。
     *
     * - 200 → 硬删本地 + 关联子表；
     * - 404 → 服务端已删，幂等成功，硬删本地；
     * - 400（selfPerson 禁删）→ 原样抛 [ApiException]（服务端守卫）；
     * - 其他失败 → **恢复软删**（UI 重新可见，可重试），有日志不吞错。
     */
    override suspend fun commitDelete(contactId: Long): CommitResult = withContext(Dispatchers.IO) {
        val current = contactCacheDao.getContactById(contactId)
        if (current == null) {
            Log.w("Tester", "commitDelete: contactId=$contactId not found, no-op")
            return@withContext CommitResult.NotFound
        }
        val serverUuid = current.serverId
        if (serverUuid.isNullOrBlank()) {
            // [修复防御]: 本地未同步(isLocalOnly) → 服务端没有对应行,无需 DELETE,直接硬删本地。
            Log.w("Tester", "commitDelete: contactId=$contactId isLocalOnly=true,skip HTTP,hardDelete")
            hardDeleteContact(contactId)
            return@withContext CommitResult.SentSuccess
        }
        val now = System.currentTimeMillis()
        // 软删(UI 立即隐藏) → 直发 DELETE
        contactCacheDao.setDeleted(contactId, deleted = true, now = now)
        contactCacheDao.bumpContact(contactId)
        return@withContext try {
            val ok = serverApi.deletePerson(serverUuid)
            if (ok) {
                hardDeleteContact(contactId)
                CommitResult.SentSuccess
            } else {
                // 不可达:deletePerson 内部已 catch 404 返 true。false 视为 5xx 兜底。
                restoreSoftDeleted(contactId)
                CommitResult.SentFailed("deletePerson returned false")
            }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w("Tester", "commitDelete: contactId=$contactId 404 → 幂等成功,hardDelete")
                hardDeleteContact(contactId)
                CommitResult.SentSuccess
            } else {
                restoreSoftDeleted(contactId)
                Log.w("Tester", "commitDelete: contactId=$contactId HTTP ${e.status} 失败,恢复软删", e)
                CommitResult.SentFailed(e.message ?: "HTTP ${e.status}")
            }
        } catch (e: Exception) {
            restoreSoftDeleted(contactId)
            Log.e("Tester", "commitDelete: contactId=$contactId 直发异常,恢复软删", e)
            CommitResult.SentFailed(e.message ?: "unknown")
        }
    }

    /**
     * 直推合并：`POST /api/user/persons/{targetUuid}/merge`（merged_ids）。
     * target 保留（字段不动），merged 行由服务端删除并级联清 personMembers；
     * 客户端硬删 merged 本地行。404 → 幂等成功。失败只返回 [CommitResult.SentFailed]，不恢复。
     */
    override suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult = withContext(Dispatchers.IO) {
        if (mergedIds.isEmpty()) {
            Log.w("Tester", "commitMerge: targetId=$targetId mergedIds is empty,no-op")
            return@withContext CommitResult.NotFound
        }
        val target = contactCacheDao.getContactById(targetId)
        if (target == null) {
            Log.w("Tester", "commitMerge: targetId=$targetId not found")
            return@withContext CommitResult.NotFound
        }
        val targetServerUuid = target.serverId
        if (targetServerUuid.isNullOrBlank()) {
            Log.w("Tester", "commitMerge: targetId=$targetId isLocalOnly,skip HTTP")
            return@withContext CommitResult.SentFailed("target isLocalOnly=true")
        }
        val mergedEntities = mergedIds.mapNotNull { contactCacheDao.getContactById(it) }
        val mergedServerIds = mergedEntities.mapNotNull { it.serverId }.filter { it.isNotBlank() }
        if (mergedServerIds.isEmpty()) {
            Log.w("Tester", "commitMerge: targetId=$targetId all merged are localOnly,只清本地")
            mergedEntities.forEach { hardDeleteContact(it.id) }
            return@withContext CommitResult.SentSuccess
        }
        return@withContext try {
            serverApi.mergePersons(targetServerUuid, mergedServerIds)
            for (mId in mergedIds) hardDeleteContact(mId)
            contactCacheDao.bumpContact(targetId)
            CommitResult.SentSuccess
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w("Tester", "commitMerge: targetId=$targetId 404 → 幂等,hardDelete merged")
                for (mId in mergedIds) hardDeleteContact(mId)
                CommitResult.SentSuccess
            } else {
                Log.w("Tester", "commitMerge: targetId=$targetId HTTP ${e.status} 失败", e)
                CommitResult.SentFailed(e.message ?: "HTTP ${e.status}")
            }
        } catch (e: Exception) {
            Log.e("Tester", "commitMerge: targetId=$targetId 直发异常", e)
            CommitResult.SentFailed(e.message ?: "unknown")
        }
    }

    /**
     * 物理删除联系人 + 关联子表（commitDelete / commitMerge 成功后用）。
     * 关联子表用 DELETE FROM 直接清，避免 Room 外键 cascade 跨表延迟。
     */
    private suspend fun hardDeleteContact(contactId: Long) {
        contactPlatformCacheDao.deleteByContact(contactId)
        contactFieldValueCacheDao.deleteByContact(contactId)
        contactTagCacheDao.clearByContact(contactId)
        contactCacheDao.deleteById(contactId)
        contactCacheDao.bumpContact(contactId)
    }

    private suspend fun restoreSoftDeleted(contactId: Long) {
        contactCacheDao.setDeleted(contactId, deleted = false, now = System.currentTimeMillis())
        contactCacheDao.bumpContact(contactId)
    }

    override fun searchContacts(query: String): Flow<List<ContactCacheEntity>> {
        return if (query.isBlank()) {
            contactCacheDao.getAllContacts()
        } else {
            contactCacheDao.searchContacts(query)
        }
    }

    override suspend fun bumpContact(contactId: Long) = withContext(Dispatchers.IO) {
        contactCacheDao.bumpContact(contactId)
    }

    // ========== 联系人社交平台操作 ==========

    /**
     * 更新/新增/删除平台条目：本地写 `contact_platforms_cache` + 直推 `profile.contactMap`。
     *
     * 空 [entry]（jumpLink 与 value 皆空）视为删除。所有平台数据以服务端
     * `Profile.contactMap`(Map<String,String>) 为载体，本地 `contact_platforms_cache`
     * 供 UI 展示（displayName/jumpLink 由 fieldKey+value 本地推导）。
     */
    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                    .firstOrNull { it.platformKey == fieldKey }
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    // 删除平台条目
                    if (existing == null) return@withContext
                    contactPlatformCacheDao.deleteByContactAndKey(contactId, fieldKey)
                    contactCacheDao.bumpContact(contactId)
                    pushPlatformUpdate(contactId)
                    return@withContext
                }
                contactPlatformCacheDao.insertPlatform(
                    ContactPlatformCacheEntity(
                        contactId = contactId,
                        platformKey = fieldKey,
                        value = entry.value,
                        displayName = entry.displayName,
                        jumpLink = entry.jumpLink,
                        originalLink = entry.originalLink,
                        avatarUrl = entry.avatarUrl,
                    )
                )
                contactCacheDao.bumpContact(contactId)
                pushPlatformUpdate(contactId)
            }
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                .firstOrNull { it.platformKey == fieldKey } ?: return@withContext
            contactPlatformCacheDao.deleteByContactAndKey(contactId, fieldKey)
            contactCacheDao.bumpContact(contactId)
            pushPlatformUpdate(contactId)
        }
    }

    override suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getAllPlatforms()
                .groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId)
        }

    // ========== [Phase 3] 直推辅助 ==========

    /**
     * 确保该联系人在服务端有 Person 行，返回服务端 uuid。
     *
     * - [ContactCacheEntity.serverId] 已有 → 直接返回；
     * - 本地 `isLocalOnly=true`（离线兜底 / 历史 v6 数据）→ 客户端生成新 uuid 幂等重放
     *   `POST /api/user/persons`，成功后回填 serverId 并清 isLocalOnly。
     *
     * @return 服务端 uuid；create-on-push 失败返回 null（本地已保存，待下次重试）。
     */
    private suspend fun ensureServerUuid(contact: ContactCacheEntity): String? {
        contact.serverId?.takeIf { it.isNotBlank() }?.let { return it }
        val clientUuid = UUID.randomUUID().toString()
        return try {
            val serverUuid = serverApi.createPerson(contact.name, buildProfile(contact, null), clientUuid)
            contactCacheDao.updateContact(contact.copy(serverId = serverUuid, isLocalOnly = false))
            Log.d("Tester", "ensureServerUuid: contactId=${contact.id} create-on-push → uuid=${serverUuid.take(8)}")
            serverUuid
        } catch (e: Exception) {
            Log.e("Tester", "ensureServerUuid: create-on-push 失败 contactId=${contact.id}", e)
            null
        }
    }

    /** 平台条目变更后直推 `PUT profile.contactMap`（value 非空条目，key→value）。 */
    private suspend fun pushPlatformUpdate(contactId: Long) {
        val contact = contactCacheDao.getContactById(contactId) ?: return
        val serverUuid = ensureServerUuid(contact) ?: run {
            Log.w("Tester", "pushPlatformUpdate: contactId=$contactId 无 serverId 且 create-on-push 失败,本地已保存,待下次编辑/同步")
            return
        }
        try {
            serverApi.updatePerson(serverUuid, name = null, profile = buildProfile(contact, null))
        } catch (e: Exception) {
            Log.w("Tester", "pushPlatformUpdate: contactId=$contactId PUT 失败(本地已保存)", e)
        }
    }

    /**
     * 由本地联系人态构建服务端 `Profile`（直推载荷）。
     *
     * 映射（对齐 `Badger-Server/docs/api-handover.md` §4.1 Profile 字段表）：
     * - `avatarUrl` → `profile.avatarURL`（camelCase）；
     * - `bio` → `profile.description`（旧 `signature` 已改名）；
     * - `contact_platforms_cache` 行 → `profile.contactMap`(Map<String,String>)（platformKey → value）；
     * - `note` / `pinyinInitial` 无服务端对应，保持本地。
     *
     * [platforms] 传 null 时从 DB 现读（调用方已持有最新值时可显式传入避免重复查询）。
     */
    private suspend fun buildProfile(
        contact: ContactCacheEntity,
        platforms: List<ContactPlatformCacheEntity>?,
    ): ProfileDto {
        val rows = platforms ?: try {
            contactPlatformCacheDao.getPlatformsByContact(contact.id)
        } catch (e: Exception) {
            Log.w("Tester", "buildProfile: 读平台失败 contactId=${contact.id}", e)
            emptyList()
        }
        val contactMap = rows
            .mapNotNull { row -> row.value?.takeIf { it.isNotBlank() }?.let { row.platformKey to it } }
            .toMap()
        return ProfileDto(
            avatarURL = contact.avatarUrl,
            description = contact.bio,
            contactMap = contactMap,
        )
    }

    // ========== 重复检测 ==========

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        var bestMatch: ContactCacheEntity? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        if (newContactName.isNotBlank()) {
            val exactMatches = contactCacheDao.getContactsByName(newContactName)
            for (contact in exactMatches) {
                if (contact.name.equals(newContactName, ignoreCase = true)) {
                    if (1.0f > bestScore) {
                        bestScore = 1.0f
                        bestMatch = contact
                        matchedFields = listOf("name")
                    }
                }
            }
            if (bestScore < 1.0f) {
                val prefixMatches = contactCacheDao.searchContactsByName(newContactName).first()
                for (contact in prefixMatches) {
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f && nameSimilarity < 1.0f) {
                        val score = nameSimilarity * 0.5f
                        if (score > bestScore) {
                            bestScore = score
                            bestMatch = contact
                            matchedFields = listOf("name")
                        }
                    }
                }
            }
        }

        if (fieldValues.isEmpty() && customFieldValues.isEmpty()) {
            return@withContext DuplicateCheckResult(
                isDuplicate = bestScore >= 1.0f,
                existingContact = bestMatch,
                similarityScore = bestScore.coerceIn(0f, 2f),
                matchFields = matchedFields
            )
        }

        val platformKeys = fieldValues.keys.filter { it in PLATFORM_FIELD_KEYS }.toSet()

        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue

            if (key in platformKeys) {
                val platformDuplicateIds = contactPlatformCacheDao.findContactIdsByPlatform(key, value, -1)
                val platformDuplicates = platformDuplicateIds.mapNotNull { contactCacheDao.getContactById(it) }
                for (contact in platformDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    score += 1.0f
                    fields.add(key)
                    val nameSimilarity = calculateNameSimilarity(newContactName, contact.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = contact
                        matchedFields = fields
                    }
                }
            } else {
                val potentialDuplicates = contactCacheDao.searchContacts(value).first()
                for (potential in potentialDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueCacheDao
                        .getFieldValuesByContactOnce(potential.id).map { it.toFieldValue() }
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldCacheDao.getFieldById(fieldId)?.toContactField()
                        if (field != null && field.fieldKey == key && existingValue.value == value) {
                            score += 1.0f
                            fields.add(field.fieldName)
                        }
                    }
                    val nameSimilarity = calculateNameSimilarity(newContactName, potential.name)
                    if (nameSimilarity > 0.7f) {
                        score += nameSimilarity * 0.5f
                        fields.add("name")
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = potential
                        matchedFields = fields
                    }
                }
            }
        }

        DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields
        )
    }

    private fun calculateNameSimilarity(name1: String, name2: String): Float {
        if (name1.equals(name2, ignoreCase = true)) return 1.0f
        val set1 = name1.lowercase().toSet()
        val set2 = name2.lowercase().toSet()
        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()
        return if (union > 0) intersection / union else 0f
    }

    // ========== QAuxv 导入 ==========

    companion object {
        private const val QQ_PLATFORM_KEY = "qq"
        /** QQ 头像源(与 QqAdapter / PlatformIdExtractor 一致)。 */
        private const val QQ_AVATAR_URL_TEMPLATE = "https://q1.qlogo.cn/g?b=qq&nk=%s&s=100"
        /** 头像下载并发上限,避免 N 个 socket 同时打开。 */
        private const val AVATAR_CONCURRENCY = 6
        /** 头像下载超时(毫秒)。 */
        private const val AVATAR_TIMEOUT_MS = 5_000L

        internal fun qqAvatarUrl(uin: Long): String = QQ_AVATAR_URL_TEMPLATE.format(uin)
        internal fun qqAvatarFileName(uin: Long): String = "contact_qq_${uin}_avatar.webp"

        /**
         * 用于测试注入的下载器。默认走 HttpUtil;测试里换成 `{ null }` 跳过实际网络。
         */
        internal var avatarDownloader: suspend (String) -> android.graphics.Bitmap? = {
            HttpUtil.downloadBitmap(it, timeoutMs = AVATAR_TIMEOUT_MS)
        }
    }

    override suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext emptyMap()
            val uinStrings = entries.map { it.uin.toString() }.distinct()
            val platforms = contactPlatformCacheDao.getPlatformsByKeyAndValues(QQ_PLATFORM_KEY, uinStrings)
            val map = LinkedHashMap<Long, Long>()
            for (p in platforms) {
                val uin = p.value?.toLongOrNull() ?: continue
                if (!map.containsKey(uin)) {
                    map[uin] = p.contactId
                }
            }
            map
        }

    override suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)?,
    ): QAuxvImportSummary {

        val toDownload = decisions.filter { it.third != QAuxvConflictAction.Skip }.map { it.first }
        val avatarPathByUin = ConcurrentHashMap<Long, String>()
        if (toDownload.isNotEmpty()) {
            onProgress?.invoke(
                QAuxvImportProgress(
                    phase = QAuxvImportProgress.Phase.AvatarDownloading,
                    current = 0, total = toDownload.size,
                )
            )
            val done = AtomicInteger(0)
            val sem = Semaphore(permits = AVATAR_CONCURRENCY)
            coroutineScope {
                for (entry in toDownload) {
                    launch {
                        sem.withPermit {
                            val uin = entry.uin
                            try {
                                val url = qqAvatarUrl(uin)
                                val bmp = avatarDownloader(url)
                                if (bmp != null) {
                                    val file = withContext(Dispatchers.IO) {
                                        Methods.saveBitmapAsAvatar(
                                            context, bmp, qqAvatarFileName(uin)
                                        )
                                    }
                                    avatarPathByUin[uin] = file.absolutePath
                                    if (!bmp.isRecycled) bmp.recycle()
                                } else {
                                    Log.w("Tester", "avatar download returned null uin=$uin")
                                }
                            } catch (e: Exception) {
                                Log.e("Tester", "avatar download/save failed uin=$uin", e)
                            }
                            val c = done.incrementAndGet()
                            onProgress?.invoke(
                                QAuxvImportProgress(
                                    phase = QAuxvImportProgress.Phase.AvatarDownloading,
                                    current = c, total = toDownload.size,
                                )
                            )
                        }
                    }
                }
            }
        }

        return contactMutex.withLock {
            withContext(Dispatchers.IO) {
                var inserted = 0
                var replaced = 0
                var skipped = 0
                onProgress?.invoke(
                    QAuxvImportProgress(
                        phase = QAuxvImportProgress.Phase.Writing,
                        current = 0, total = decisions.size,
                    )
                )
                for ((index, decision) in decisions.withIndex()) {
                    val (entry, existingId, action) = decision
                    val localAvatar = avatarPathByUin[entry.uin]
                    try {
                        when (action) {
                            QAuxvConflictAction.Skip -> {
                                skipped++
                            }
                            QAuxvConflictAction.Replace -> {
                                val targetId = existingId?.takeIf { it > 0L }
                                if (targetId == null) {
                                    Log.w("Tester", "importQAuxvFriends[$index]: Replace w/o existingId, fallback to insert uin=${entry.uin}")
                                    insertOne(entry, localAvatar)
                                    inserted++
                                } else {
                                    replaceOne(targetId, entry, localAvatar)
                                    replaced++
                                }
                            }
                            QAuxvConflictAction.InsertAnyway -> {
                                insertOne(entry, localAvatar)
                                inserted++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Tester", "importQAuxvFriends[$index]: failed uin=${entry.uin}", e)
                        skipped++
                    }
                    onProgress?.invoke(
                        QAuxvImportProgress(
                            phase = QAuxvImportProgress.Phase.Writing,
                            current = index + 1, total = decisions.size,
                        )
                    )
                }
                QAuxvImportSummary(inserted = inserted, replaced = replaced, skipped = skipped)
            }
        }
    }

    /** 新增一条 Contact + 一条 QQ Platform 条目。 */
    private suspend fun insertOne(entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val now = System.currentTimeMillis()
        val newContactId = contactCacheDao.insertContact(
            ContactCacheEntity(
                id = 0L,
                name = entry.displayName,
                avatarUrl = qqAvatarUrl(entry.uin),
                avatarPath = localAvatarPath,
                pinyinInitial = PinyinUtils.getContactPinyinInitial(entry.displayName),
                createTime = now,
                updateTime = now,
            )
        )
        contactPlatformCacheDao.insertPlatform(buildQqPlatform(newContactId, entry))
        contactCacheDao.bumpContact(newContactId)
    }

    /** 替换:更新已有 Contact 的 name + 头像 + QQ Platform 条目。 */
    private suspend fun replaceOne(contactId: Long, entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val latest = contactCacheDao.getContactById(contactId)
        if (latest != null) {
            if (!localAvatarPath.isNullOrBlank() && !latest.avatarPath.isNullOrBlank()
                && latest.avatarPath != localAvatarPath
            ) {
                Methods.deleteAvatarFile(latest.avatarPath)
            }
            val newAvatarPath = localAvatarPath ?: latest.avatarPath
            val newPinyinInitial = if (latest.name == entry.displayName) {
                latest.pinyinInitial
            } else {
                PinyinUtils.getContactPinyinInitial(entry.displayName)
            }
            contactCacheDao.updateContact(
                latest.copy(
                    name = entry.displayName,
                    avatarUrl = qqAvatarUrl(entry.uin),
                    avatarPath = newAvatarPath,
                    pinyinInitial = newPinyinInitial,
                )
            )
            contactCacheDao.bumpContact(contactId)
        }
        contactPlatformCacheDao.insertPlatform(buildQqPlatform(contactId, entry))
    }

    /**
     * 规范化 pinyinInitial:只要 name 变化或当前 pinyinInitial 与按 name 重算的结果不一致,
     * 就用重算结果覆盖。空 name 退化为 '#'。
     *
     * 为什么放在 Repository 而不是 ViewModel:排序字段是 DB 一致性问题,不该依赖每个 caller
     * 记得去算。Repository 是写入最后一道关口,在此收敛契约。
     */
    private fun normalizePinyinInitial(name: String, currentPinyinInitial: String): String {
        if (name.isBlank()) return currentPinyinInitial.ifBlank { "#" }
        val expected = PinyinUtils.getContactPinyinInitial(name)
        return if (currentPinyinInitial == expected) currentPinyinInitial else expected
    }

    private fun buildQqPlatform(contactId: Long, entry: QAuxvFriendEntry): ContactPlatformCacheEntity {
        val uin = entry.uin.toString()
        val jumpLink = buildPlatformLink(QQ_PLATFORM_KEY, uin)
        return ContactPlatformCacheEntity(
            contactId = contactId,
            platformKey = QQ_PLATFORM_KEY,
            value = uin,
            displayName = entry.displayName,
            jumpLink = jumpLink,
            originalLink = null,
            avatarUrl = qqAvatarUrl(entry.uin),
        )
    }
}
