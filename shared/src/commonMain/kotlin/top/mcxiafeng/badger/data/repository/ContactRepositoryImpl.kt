package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
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
import top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity
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
import top.mcxiafeng.badger.shared.util.deleteFileQuietly
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.OutboxQueue
import top.mcxiafeng.badger.sync.RemoteIdentity
import top.mcxiafeng.badger.sync.identity
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.shared.util.randomUuid
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

class ContactRepositoryImpl(
    private val contactCacheDao: ContactCacheDao,
    private val contactFieldCacheDao: ContactFieldCacheDao,
    private val contactFieldValueCacheDao: ContactFieldValueCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val personProfileCacheDao: PersonProfileCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val serverApi: ServerApi,
    private val outboxStore: top.mcxiafeng.badger.sync.OutboxQueue,
    

    private val avatarFetcher: suspend (url: String, uin: Long) -> String?,
) : ContactRepository {

    private val contactMutex = Mutex()

    

    override fun getAllContacts(): Flow<List<ContactCacheEntity>> = contactCacheDao.getAllContacts()

    override fun getLetterIndex(): Flow<List<LetterCount>> = contactCacheDao.getLetterIndex()

    override suspend fun getContactById(id: Long): ContactCacheEntity? = withContext(BadgerDispatchers.io) {
        contactCacheDao.getContactById(id)
    }

    
    override suspend fun getContactByServerId(serverId: String): ContactCacheEntity? = withContext(BadgerDispatchers.io) {
        contactCacheDao.getContactByServerId(serverId)
    }

    override fun getAllContactsWithFields(): Flow<List<PersonWithFields>> {
        return contactCacheDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                contact.toPersonWithFields(emptyList())
            }
        }
    }

    override suspend fun getPersonWithFieldsById(id: Long): PersonWithFields? = withContext(BadgerDispatchers.io) {
        val contact = contactCacheDao.getContactById(id) ?: return@withContext null
        
        
        val fieldValues = contactFieldValueCacheDao.getFieldValuesByContactOnce(id)
            .sortedByDescending { it.updateTime }
            .distinctBy { it.fieldId }

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

    override suspend fun insertContact(contact: ContactCacheEntity): Long = withContext(BadgerDispatchers.io) {
        val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
            contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
        } else contact
        val clientUuid = randomUuid()
        val newId = contactCacheDao.insertContact(
            withPinyin.copy(
                
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

    override suspend fun updateContact(contact: ContactCacheEntity) = withContext(BadgerDispatchers.io) {
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

    override suspend fun updateContactBio(contactId: Long, bio: String?) = withContext(BadgerDispatchers.io) {
        val existing = contactCacheDao.getContactById(contactId) ?: return@withContext
        if (existing.bio == bio) return@withContext
        val updated = existing.copy(bio = bio, updateTime = top.mcxiafeng.badger.shared.util.nowMs())
        contactCacheDao.updateContact(updated)
        contactCacheDao.bumpContact(contactId)
        val remoteId = ensureCreateEnqueued(updated)
        try {
            serverApi.updatePerson(contactId, remoteId, name = null, profile = buildProfile(updated, null))
        } catch (e: Exception) {
            BadgerLog.w(TAG, "updateContactBio: contactId=$contactId 入队失败(本地已保存)", e)
        }
    }

    override suspend fun deleteContact(contact: ContactCacheEntity) = withContext(BadgerDispatchers.io) {
        contactCacheDao.deleteByIds(listOf(contact.id))
    }

    

    override suspend fun commitDelete(contactId: Long): CommitResult = withContext(BadgerDispatchers.io) {
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
        val now = top.mcxiafeng.badger.shared.util.nowMs()
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

    
    override suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult = withContext(BadgerDispatchers.io) {
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
        
        val localOnlyMerged = mergedEntities.filter { it.serverId.isNullOrBlank() }
        val syncedMerged = mergedEntities.filter { !it.serverId.isNullOrBlank() }
        
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

    
    private suspend fun copyFieldsAndPlatformsToTarget(sourceId: Long, targetId: Long) {
        
        val sourceFields = contactFieldValueCacheDao.getFieldValuesByContactOnce(sourceId)
        val existingFields = contactFieldValueCacheDao.getFieldValuesByContactOnce(targetId)
        val existingFieldIds = existingFields.mapNotNull { it.fieldId }.toSet()
        for (fv in sourceFields) {
            if (fv.fieldId != null && fv.fieldId in existingFieldIds) continue
            contactFieldValueCacheDao.insertFieldValue(
                fv.copy(id = 0, contactId = targetId)
            )
        }
        
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

    
    private suspend fun hardDeleteContact(contactId: Long) {
        val avatarPath = contactCacheDao.getContactById(contactId)?.avatarPath
        contactPlatformCacheDao.deleteByContact(contactId)
        contactFieldValueCacheDao.deleteByContact(contactId)
        contactTagCacheDao.clearContactTags(contactId)
        contactCacheDao.deleteById(contactId)
        contactCacheDao.bumpContact(contactId)
        deleteAvatarFileQuietly(contactId, avatarPath)
    }

    
    private fun deleteAvatarFileQuietly(contactId: Long, avatarPath: String?) {
        if (avatarPath.isNullOrBlank()) return
        try {
            deleteFileQuietly(avatarPath)
            BadgerLog.d(TAG, "hardDeleteContact: avatar file removed contactId=$contactId")
        } catch (e: Exception) {
            BadgerLog.e(TAG, "hardDeleteContact: avatar file remove failed contactId=$contactId", e)
        }
    }

    private suspend fun restoreSoftDeleted(contactId: Long) {
        contactCacheDao.setDeleted(contactId, deleted = false, now = top.mcxiafeng.badger.shared.util.nowMs())
        contactCacheDao.bumpContact(contactId)
    }

    override fun searchContacts(query: String): Flow<List<ContactCacheEntity>> {
        return if (query.isBlank()) {
            contactCacheDao.getAllContacts()
        } else {
            contactCacheDao.searchContacts(query)
        }
    }

    override suspend fun bumpContact(contactId: Long) = withContext(BadgerDispatchers.io) {
        contactCacheDao.bumpContact(contactId)
    }

    

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) {
        contactMutex.withLock {
            withContext(BadgerDispatchers.io) {
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
        withContext(BadgerDispatchers.io) {
            val existing = contactPlatformCacheDao.getPlatformsByContact(contactId)
                .firstOrNull { it.platformKey == fieldKey } ?: return@withContext
            contactPlatformCacheDao.deleteByContactAndKey(contactId, fieldKey)
            contactCacheDao.bumpContact(contactId)
            pushPlatformUpdate(contactId)
        }
    }

    override suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>> =
        withContext(BadgerDispatchers.io) {
            contactPlatformCacheDao.getAllPlatforms().groupBy { it.contactId }
        }

    override suspend fun getContactPlatformKeys(contactId: Long): Set<String> =
        withContext(BadgerDispatchers.io) {
            contactPlatformCacheDao.getPlatformsByContact(contactId).map { it.platformKey }.toSet()
        }

    override suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform> =
        withContext(BadgerDispatchers.io) {
            contactPlatformCacheDao.getPlatformsByContact(contactId)
        }

    
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
        
        val basicInfo = ContactMapper.loadBasicFieldValues(contactFieldCacheDao, contactFieldValueCacheDao, contact.id)
        
        val profileEntity = contact.serverId?.let { personProfileCacheDao.getByServerId(it) }
        return ContactMapper.buildProfileDto(
            contact, rows, basicInfo,
            profileExtra = profileEntity?.extra, backgroundURL = profileEntity?.backgroundURL,
        )
    }

    override suspend fun pushBasicInfoEdit(contactId: Long) {
        
        withContext(BadgerDispatchers.io) {
            pushPlatformUpdate(contactId)
        }
    }

    

    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>,
    ): DuplicateCheckResult = withContext(BadgerDispatchers.io) {
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

    

    companion object {
        private const val TAG = "ContactRepository"
        private const val QQ_PLATFORM_KEY = "qq"
        
        private const val QQ_AVATAR_URL_TEMPLATE = "https://q1.qlogo.cn/g?b=qq&nk=%s&s=100"
        
        private const val AVATAR_CONCURRENCY = 6

        fun qqAvatarUrl(uin: Long): String = QQ_AVATAR_URL_TEMPLATE.replace("%s", uin.toString())
        fun qqAvatarFileName(uin: Long): String = "contact_qq_${uin}_avatar.webp"
    }

    override suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long> =
        withContext(BadgerDispatchers.io) {
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
        onProgress: ((QAuxvImportProgress) -> Unit)?,
    ): QAuxvImportSummary {
        val toDownload = decisions.filter { it.third != QAuxvConflictAction.Skip }.map { it.first }
        
        val avatarMapMutex = Mutex()
        val avatarPathByUin = HashMap<Long, String>()
        if (toDownload.isNotEmpty()) {
            onProgress?.invoke(
                QAuxvImportProgress(
                    phase = QAuxvImportProgress.Phase.AvatarDownloading,
                    current = 0, total = toDownload.size,
                )
            )
            val done = atomic(0)
            val sem = Semaphore(permits = AVATAR_CONCURRENCY)
            coroutineScope {
                for (entry in toDownload) {
                    launch {
                        sem.withPermit {
                            val uin = entry.uin
                            try {
                                val url = qqAvatarUrl(uin)
                                val savedPath = avatarFetcher(url, uin)
                                if (savedPath != null) {
                                    avatarMapMutex.withLock { avatarPathByUin[uin] = savedPath }
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
            withContext(BadgerDispatchers.io) {
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

    
    private suspend fun insertOne(entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val now = top.mcxiafeng.badger.shared.util.nowMs()
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
        
        pushPlatformUpdate(newContactId)
    }

    private suspend fun replaceOne(contactId: Long, entry: QAuxvFriendEntry, localAvatarPath: String?) {
        val latest = contactCacheDao.getContactById(contactId)
        if (latest != null) {
            if (!localAvatarPath.isNullOrBlank() && !latest.avatarPath.isNullOrBlank()
                && latest.avatarPath != localAvatarPath
            ) {
                deleteFileQuietly(latest.avatarPath)
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
