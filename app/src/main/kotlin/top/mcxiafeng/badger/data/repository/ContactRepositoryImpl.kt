package top.mcxiafeng.badger.data.repository

import android.content.Context
import top.mcxiafeng.badger.utils.BadgerLog
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
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.DuplicateCheckResult
import top.mcxiafeng.badger.data.model.LetterCount
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.model.QAuxvConflictAction
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.model.QAuxvImportProgress
import top.mcxiafeng.badger.data.model.QAuxvImportSummary
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
import top.mcxiafeng.badger.data.repository.ContactMapper.toPersonWithFields
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldDisplay
import top.mcxiafeng.badger.data.repository.ContactMapper.toFieldValue
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxStore
import top.mcxiafeng.badger.sync.RemoteIdentity
import top.mcxiafeng.badger.sync.identity
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import top.mcxiafeng.badger.shared.util.randomUuid
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 联系人仓库：直推直删，写操作走 POST/PUT/DELETE。uuid 幂等重放。 */
class ContactRepositoryImpl(
    private val contactCacheDao: ContactCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val serverApi: ServerApi,
    private val outboxStore: OutboxStore,
) : ContactRepository {

    private val contactMutex = Mutex()

    // ========== 联系人基本操作 ==========

    override fun getAllContacts(): Flow<List<ContactCacheEntity>> = contactCacheDao.getAllContacts()

    override fun getLetterIndex(): Flow<List<LetterCount>> = contactCacheDao.getLetterIndex()

    override suspend fun getContactById(id: Long): ContactCacheEntity? = withContext(Dispatchers.IO) {
        contactCacheDao.getContactById(id)
    }

    /** 按服务端 UUID 查找（Deep Link 用）。 */
    override suspend fun getContactByServerId(serverId: String): ContactCacheEntity? = withContext(Dispatchers.IO) {
        contactCacheDao.getContactByServerId(serverId)
    }

    override fun getAllContactsWithFields(): Flow<List<PersonWithFields>> {
        return contactCacheDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                contact.toPersonWithFields(emptyList())
            }
        }
    }

    override suspend fun getPersonWithFieldsById(id: Long): PersonWithFields? = withContext(Dispatchers.IO) {
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

        contact.toPersonWithFields(fields)
    }

    override suspend fun insertContact(contact: ContactCacheEntity): Long = withContext(Dispatchers.IO) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        val clientUuid = randomUuid()
        val newId = contactCacheDao.insertContact(
            withPinyin.copy(
                // clientUuid 是持久化的幂等键：CREATE 重放/重试必须复用，避免响应丢失造成重复人物
                serverId = clientUuid,
                isLocalOnly = true,
            )
        )
        contactCacheDao.bumpContact(newId)
        try {
            serverApi.enqueueCreatePerson(newId, withPinyin.name, buildProfile(withPinyin, emptyList()), clientUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "insertContact: CREATE 入队失败(本地已保存,待 syncOnce 回填) id=$newId", e)
        }
        newId
    }

    override suspend fun updateContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        val normalized = contact.copy(
            pinyinInitial = normalizePinyinInitial(contact.name, contact.pinyinInitial)
        )
        val existing = contactCacheDao.getContactById(contact.id)
        if (existing == null) {
            contactCacheDao.updateContact(normalized)
            contactCacheDao.bumpContact(normalized.id)
            return@withContext
        }
        contactCacheDao.updateContact(normalized)
        contactCacheDao.bumpContact(normalized.id)
        val remoteId = ensureCreateEnqueued(normalized)
        try {
            serverApi.updatePerson(contact.id, remoteId, name = normalized.name, profile = buildProfile(normalized, null))
        } catch (e: Exception) {
            BadgerLog.w(TAG, "updateContact: id=${contact.id} 入队失败(本地已保存)", e)
        }
    }

    override suspend fun updateContactBio(contactId: Long, bio: String?) = withContext(Dispatchers.IO) {
        val existing = contactCacheDao.getContactById(contactId) ?: return@withContext
        if (existing.bio == bio) return@withContext
        val updated = existing.copy(bio = bio, updateTime = System.currentTimeMillis())
        contactCacheDao.updateContact(updated)
        contactCacheDao.bumpContact(contactId)
        val remoteId = ensureCreateEnqueued(updated)
        try {
            serverApi.updatePerson(contactId, remoteId, name = null, profile = buildProfile(updated, null))
        } catch (e: Exception) {
            BadgerLog.w(TAG, "updateContactBio: contactId=$contactId 入队失败(本地已保存)", e)
        }
    }

    override suspend fun deleteContact(contact: ContactCacheEntity) = withContext(Dispatchers.IO) {
        contactCacheDao.deleteByIds(listOf(contact.id))
    }

    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        // 批量删除同样回收头像文件：先读行取 avatarPath，再删行删文件
        val avatarPaths = ids.mapNotNull { id ->
            contactCacheDao.getContactById(id)?.avatarPath?.takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()
        contactCacheDao.deleteByIds(ids)
        avatarPaths.forEach { (id, path) -> deleteAvatarFileQuietly(id, path) }
    }

    // ========== [Phase 3] commitDelete / commitMerge 直推 ==========

    override suspend fun commitDelete(contactId: Long): CommitResult = withContext(Dispatchers.IO) {
        val current = contactCacheDao.getContactById(contactId)
        if (current == null) {
            BadgerLog.w(TAG, "commitDelete: contactId=$contactId not found, no-op")
            return@withContext CommitResult.NotFound
        }
        val serverUuid = current.serverId
        if (serverUuid.isNullOrBlank()) {
            BadgerLog.w(TAG, "commitDelete: contactId=$contactId isLocalOnly=true,skip HTTP,hardDelete")
            hardDeleteContact(contactId)
            return@withContext CommitResult.SentSuccess
        }
        // 本地 PendingCreate：取消未发的 CREATE/PATCH，入队 DELETE 兜底，本地硬删
        if (current.identity() is RemoteIdentity.PendingCreate) {
            outboxStore.cancelEntity(EntityKind.PERSON, contactId)
            try {
                serverApi.enqueueDeletePerson(contactId, serverUuid)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "commitDelete: DELETE 入队失败 contactId=$contactId(本地已硬删)", e)
            }
            hardDeleteContact(contactId)
            return@withContext CommitResult.SentSuccess
        }
        val now = System.currentTimeMillis()
        contactCacheDao.setDeleted(contactId, deleted = true, now = now)
        contactCacheDao.bumpContact(contactId)
        return@withContext try {
            val ok = serverApi.deletePerson(serverUuid)
            if (ok) {
                hardDeleteContact(contactId)
                CommitResult.SentSuccess
            } else {
                restoreSoftDeleted(contactId)
                CommitResult.SentFailed("deletePerson returned false")
            }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.w(TAG, "commitDelete: contactId=$contactId 404 → 幂等成功,hardDelete")
                hardDeleteContact(contactId)
                CommitResult.SentSuccess
            } else {
                restoreSoftDeleted(contactId)
                BadgerLog.w(TAG, "commitDelete: contactId=$contactId HTTP ${e.status} 失败,恢复软删", e)
                CommitResult.SentFailed(e.message ?: "HTTP ${e.status}")
            }
        } catch (e: Exception) {
            restoreSoftDeleted(contactId)
            BadgerLog.w(TAG, "commitDelete: contactId=$contactId 直发异常,恢复软删", e)
            CommitResult.SentFailed(e.message ?: "unknown")
        }
    }

    /** 合并人物：localOnly 的先拷字段到 target 再硬删，synced 的走 HTTP merge。 */
    override suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult = withContext(Dispatchers.IO) {
        if (mergedIds.isEmpty()) {
            BadgerLog.w(TAG, "commitMerge: targetId=$targetId mergedIds is empty,no-op")
            return@withContext CommitResult.NotFound
        }
        val target = contactCacheDao.getContactById(targetId)
        if (target == null) {
            BadgerLog.w(TAG, "commitMerge: targetId=$targetId not found")
            return@withContext CommitResult.NotFound
        }
        val targetServerUuid = target.serverId
        if (targetServerUuid.isNullOrBlank()) {
            BadgerLog.w(TAG, "commitMerge: targetId=$targetId isLocalOnly,skip HTTP")
            return@withContext CommitResult.SentFailed("target isLocalOnly=true")
        }
        val mergedEntities = mergedIds.mapNotNull { contactCacheDao.getContactById(it) }
        // 分离 localOnly 与 synced 实体
        val localOnlyMerged = mergedEntities.filter { it.serverId.isNullOrBlank() }
        val syncedMerged = mergedEntities.filter { !it.serverId.isNullOrBlank() }
        // 先把 localOnly 实体的字段/平台拷到 target
        for (entity in localOnlyMerged) {
            copyFieldsAndPlatformsToTarget(entity.id, targetId)
            BadgerLog.d(TAG, "commitMerge: copied localOnly entity ${entity.id} → target $targetId")
        }
        val mergedServerIds = syncedMerged.mapNotNull { it.serverId }.filter { it.isNotBlank() }
        if (mergedServerIds.isEmpty()) {
            BadgerLog.d(TAG, "commitMerge: targetId=$targetId all merged are localOnly,只清本地")
            mergedEntities.forEach { hardDeleteContact(it.id) }
            return@withContext CommitResult.SentSuccess
        }
        return@withContext try {
            serverApi.mergePersons(targetServerUuid, mergedServerIds)
            for (mId in mergedIds) hardDeleteContact(mId)
            contactCacheDao.bumpContact(targetId)
            CommitResult.SentSuccess
        } catch (e: ApiException) {
            BadgerLog.w(TAG, "commitMerge: targetId=$targetId HTTP ${e.status} 失败,保留本地数据", e)
            CommitResult.SentFailed(e.message ?: "HTTP ${e.status}")
        } catch (e: Exception) {
            BadgerLog.w(TAG, "commitMerge: targetId=$targetId 直发异常", e)
            CommitResult.SentFailed(e.message ?: "unknown")
        }
    }

    /** 把 localOnly 行的字段和平台拷到 target，跳过已有条目。 */
    private suspend fun copyFieldsAndPlatformsToTarget(sourceId: Long, targetId: Long) {
        // 拷字段值
        val sourceFields = contactFieldValueCacheDao.getFieldValuesByContactOnce(sourceId)
        val existingFields = contactFieldValueCacheDao.getFieldValuesByContactOnce(targetId)
        val existingFieldIds = existingFields.mapNotNull { it.fieldId }.toSet()
        for (fv in sourceFields) {
            if (fv.fieldId != null && fv.fieldId in existingFieldIds) continue
            contactFieldValueCacheDao.insertFieldValue(
                fv.copy(id = 0, contactId = targetId)
            )
        }
        // 拷平台条目
        val sourcePlatforms = contactPlatformCacheDao.getPlatformsByContact(sourceId)
        val existingPlatforms = contactPlatformCacheDao.getPlatformsByContact(targetId)
        val existingKeys = existingPlatforms.map { it.platformKey }.toSet()
        for (p in sourcePlatforms) {
            if (p.platformKey in existingKeys) continue
            contactPlatformCacheDao.insertPlatform(
                p.copy(id = 0, contactId = targetId)
            )
        }
    }

    /** 物理删除联系人 + 关联子表，回收头像文件。 */
    private suspend fun hardDeleteContact(contactId: Long) {
        val avatarPath = contactCacheDao.getContactById(contactId)?.avatarPath
        contactPlatformCacheDao.deleteByContact(contactId)
        contactFieldValueCacheDao.deleteByContact(contactId)
        contactTagCacheDao.clearContactTags(contactId)
        contactCacheDao.deleteById(contactId)
        contactCacheDao.bumpContact(contactId)
        deleteAvatarFileQuietly(contactId, avatarPath)
    }

    /** 删头像文件，失败仅日志不阻塞。 */
    private fun deleteAvatarFileQuietly(contactId: Long, avatarPath: String?) {
        if (avatarPath.isNullOrBlank()) return
        try {
            Methods.deleteAvatarFile(avatarPath)
            BadgerLog.d(TAG, "hardDeleteContact: avatar file removed contactId=$contactId")
        } catch (e: Exception) {
            BadgerLog.e(TAG, "hardDeleteContact: avatar file remove failed contactId=$contactId", e)
        }
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

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(Dispatchers.IO) {
                val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                    .firstOrNull { it.platformKey == fieldKey }
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
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
            contactPlatformCacheDao.getAllPlatforms().groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(Dispatchers.IO) {
            contactPlatformCacheDao.getPlatformsByContact(contactId)
        }

    /** 确保 CREATE 入队，返回 PATCH 可用的 remoteId。 */
    private suspend fun ensureCreateEnqueued(contact: ContactCacheEntity): String {
        val identity = contact.identity()
        val remoteId = when (identity) {
            is RemoteIdentity.Synced -> identity.serverId
            is RemoteIdentity.PendingCreate -> identity.clientUuid
            is RemoteIdentity.Unidentified -> randomUuid()
        }
        if (identity is RemoteIdentity.Unidentified) {
            contactCacheDao.updateContact(contact.copy(serverId = remoteId, isLocalOnly = true))
        }
        if (identity !is RemoteIdentity.Synced) {
            try {
                serverApi.enqueueCreatePerson(contact.id, contact.name, buildProfile(contact, null), remoteId)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "ensureCreateEnqueued: contactId=${contact.id} CREATE 入队失败(本地已保存)", e)
            }
        }
        return remoteId
    }

    private suspend fun pushPlatformUpdate(contactId: Long) {
        val contact = contactCacheDao.getContactById(contactId) ?: return
        val remoteId = ensureCreateEnqueued(contact)
        try {
            serverApi.updatePerson(contactId, remoteId, name = null, profile = buildProfile(contact, null))
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushPlatformUpdate: contactId=$contactId 入队失败(本地已保存)", e)
        }
    }

    private suspend fun buildProfile(
        contact: ContactCacheEntity,
        platforms: List<ContactPlatformCacheEntity>?,
    ): ProfileDto {
        val rows = platforms ?: try {
            contactPlatformCacheDao.getPlatformsByContact(contact.id)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "buildProfile: 读平台失败 contactId=${contact.id}", e)
            emptyList()
        }
        return ContactMapper.buildProfileDto(contact, rows)
    }

    // ========== 重复检测 ==========

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>,
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
                matchFields = matchedFields,
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

        for ((customFieldId, value) in customFieldValues) {
            if (value.isBlank()) continue
            val contactIds = contactFieldValueCacheDao
                .findContactIdsByCustomFieldValue(customFieldId, value)
            for (contact in contactIds.mapNotNull { contactCacheDao.getContactById(it) }) {
                val similarity = calculateNameSimilarity(newContactName, contact.name)
                val fields = mutableListOf("custom:$customFieldId")
                var score = 1f
                if (similarity > 0.7f) {
                    score += similarity * 0.5f
                    fields += "name"
                }
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = contact
                    matchedFields = fields
                }
            }
        }

        DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields,
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
        private const val TAG = "ContactRepository"
        private const val QQ_PLATFORM_KEY = "qq"
        /** QQ 头像源。 */
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
                                    BadgerLog.w(TAG, "avatar download returned null uin=$uin")
                                }
                            } catch (e: Exception) {
                                BadgerLog.w(TAG, "avatar download/save failed uin=$uin", e)
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
                            QAuxvConflictAction.Skip -> skipped++
                            QAuxvConflictAction.Replace -> {
                                val targetId = existingId?.takeIf { it > 0L }
                                if (targetId == null) {
                                    BadgerLog.w(TAG, "importQAuxvFriends[$index]: Replace w/o existingId, fallback to insert uin=${entry.uin}")
                                    insertOne(entry, localAvatar)
                                    inserted++
                                } else {
                                    replaceOne(targetId, entry, localAvatar)
                                    // replace 更新后同样入队 PATCH，让替换后的资料也同步上云
                                    pushPlatformUpdate(targetId)
                                    replaced++
                                }
                            }
                            QAuxvConflictAction.InsertAnyway -> {
                                insertOne(entry, localAvatar)
                                inserted++
                            }
                        }
                    } catch (e: Exception) {
                        BadgerLog.w(TAG, "importQAuxvFriends[$index]: failed uin=${entry.uin}", e)
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

    /** QAuxv 单条插入：走 insertContact 统一 create-on-push 路径。 */
    private suspend fun insertOne(entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val now = System.currentTimeMillis()
        val newContactId = insertContact(
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
        // bumpContact 由 insertContact 已调用，此处不再重复
        pushPlatformUpdate(newContactId)
    }

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
