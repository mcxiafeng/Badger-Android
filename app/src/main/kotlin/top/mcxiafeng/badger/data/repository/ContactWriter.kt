package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.utils.BadgerLog
import androidx.room.withTransaction
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.model.FieldMergeEntry
import top.mcxiafeng.badger.data.model.MergeChoice
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.sync.RemoteIdentity
import top.mcxiafeng.badger.sync.identity
import top.mcxiafeng.badger.utils.PinyinUtils
import top.mcxiafeng.badger.shared.util.randomUuid

/**
 * 扫码保存 / 合并 / 附加的单一写路径（T54）。
 *
 * 三个入口内部用 Room [withTransaction] 包住 Contact + Field + Platform + Collection 成员，
 * 中途失败回滚，不留下无字段的孤儿联系人。字段 key 统一 [stripFieldKeySuffix]
 * （`qq_1` → `qq`）。事务提交后再入队 CREATE/PATCH/MEMBER。
 *
 * 合并语义是用户冲突选择的 KEEP / REPLACE / APPEND，不是「只补空」。
 */
class ContactWriter(
    private val db: AppDatabase,
    private val serverApi: ServerApi,
) {
    private val contactDao = db.contactCacheDao()
    private val fieldDao = db.contactFieldCacheDao()
    private val fieldValueDao = db.contactFieldValueCacheDao()
    private val platformDao = db.contactPlatformCacheDao()
    private val collectionDao = db.cardCollectionCacheDao()
    private val memberDao = db.collectionMemberCacheDao()
    private val customFieldDao = db.customFieldCacheDao()

    /**
     * 新建联系人：insert + 平台 + 字段 + 名片夹成员。
     */
    suspend fun saveScanned(
        contact: ContactCacheEntity,
        info: ExtractedContactInfo,
        sourceType: String,
        collectionId: Long? = null,
    ): CommitResult {
        BadgerLog.d(TAG, "saveScanned: name='${contact.name}' collectionId=$collectionId source=$sourceType")
        return runCatching {
            val pending = db.withTransaction {
                val effectiveCollectionId = resolveCollectionId(collectionId)
                val clientUuid = randomUuid()
                val withPinyin = if (contact.pinyinInitial.isBlank() && contact.name.isNotBlank()) {
                    contact.copy(pinyinInitial = PinyinUtils.getContactPinyinInitial(contact.name))
                } else contact
                val newId = contactDao.insertContact(
                    withPinyin.copy(serverId = clientUuid, isLocalOnly = true),
                )
                contactDao.bumpContact(newId)
                writeNewPlatforms(newId, buildPlatformEntries(info), existingKeys = emptySet())
                writeFieldMap(newId, buildFieldMap(info))
                memberDao.insert(CollectionMemberCacheEntity(contactId = newId, collectionId = effectiveCollectionId))
                PendingSave(
                    contactId = newId,
                    collectionId = effectiveCollectionId,
                    clientUuid = clientUuid,
                    name = withPinyin.name,
                    sourceType = sourceType,
                )
            }
            enqueueAfterSave(pending)
            BadgerLog.d(TAG, "saveScanned: written id=${pending.contactId} collection=${pending.collectionId} source=${pending.sourceType}")
            CommitResult.Written(pending.contactId)
        }.getOrElse { e ->
            BadgerLog.e(TAG, "saveScanned failed", e)
            CommitResult.SentFailed(e.message ?: "saveScanned")
        }
    }

    /**
     * 按用户 KEEP/REPLACE/APPEND 选择合并到已有联系人。
     */
    suspend fun mergeScanned(
        existingContactId: Long,
        newInfo: ExtractedContactInfo,
        mergeEntries: List<FieldMergeEntry>,
        collectionId: Long,
        sourceType: String,
        chosenName: String? = null,
    ): CommitResult {
        BadgerLog.d(TAG, "mergeScanned: existingId=$existingContactId entries=${mergeEntries.size}")
        return runCatching {
            val pending = db.withTransaction {
                val existing = contactDao.getContactById(existingContactId)
                    ?: return@withTransaction null
                applyMergeChoices(existingContactId, mergeEntries)
                val existingKeys = platformDao.getPlatformsByContact(existingContactId)
                    .map { it.platformKey }
                    .toSet()
                writeNewPlatforms(existingContactId, buildPlatformEntries(newInfo), existingKeys)
                val updatedName = chosenName ?: existing.name
                val pinyin = if (updatedName != existing.name) {
                    PinyinUtils.getContactPinyinInitial(updatedName)
                } else existing.pinyinInitial
                val updated = existing.copy(
                    name = updatedName,
                    pinyinInitial = pinyin,
                    updateTime = System.currentTimeMillis(),
                )
                contactDao.updateContact(updated)
                contactDao.bumpContact(existingContactId)
                val effectiveCollectionId = resolveCollectionId(collectionId)
                memberDao.insert(
                    CollectionMemberCacheEntity(contactId = existingContactId, collectionId = effectiveCollectionId),
                )
                PendingMerge(
                    contact = updated,
                    collectionId = effectiveCollectionId,
                    sourceType = sourceType,
                )
            } ?: return CommitResult.NotFound
            enqueueAfterMerge(pending)
            BadgerLog.d(TAG, "mergeScanned: written id=$existingContactId")
            CommitResult.Written(existingContactId)
        }.getOrElse { e ->
            BadgerLog.e(TAG, "mergeScanned failed existingId=$existingContactId", e)
            CommitResult.SentFailed(e.message ?: "mergeScanned")
        }
    }

    /**
     * 把扫描到的字段/平台附加到已有联系人（同值跳过、异值新增）。
     */
    suspend fun attachScanned(
        existingContactId: Long,
        info: ExtractedContactInfo,
        selectedFields: List<String>,
        customFields: Map<Int, String> = emptyMap(),
        networkResult: NetworkResolveResult? = null,
        collectionId: Long? = null,
    ): CommitResult {
        BadgerLog.d(TAG, "attachScanned: existingId=$existingContactId fields=${selectedFields.size}")
        return runCatching {
            val pending = db.withTransaction {
                val existing = contactDao.getContactById(existingContactId)
                    ?: return@withTransaction null
                val existingKeys = platformDao.getPlatformsByContact(existingContactId)
                    .map { it.platformKey }
                    .toSet()
                writeNewPlatforms(existingContactId, buildPlatformEntries(info), existingKeys)
                val avatarToSet = networkResult?.avatarUrl?.ifBlank { null }
                val withAvatar = if (existing.avatarUrl.isNullOrBlank() && !avatarToSet.isNullOrBlank()) {
                    existing.copy(avatarUrl = avatarToSet, updateTime = System.currentTimeMillis())
                } else {
                    existing.copy(updateTime = System.currentTimeMillis())
                }
                contactDao.updateContact(withAvatar)
                contactDao.bumpContact(existingContactId)
                if (selectedFields.isNotEmpty()) {
                    writeAttachFields(existingContactId, info, selectedFields.toSet())
                }
                if (customFields.isNotEmpty()) {
                    writeCustomFields(existingContactId, customFields)
                }
                val effectiveCollectionId = resolveCollectionId(collectionId)
                memberDao.insert(
                    CollectionMemberCacheEntity(contactId = existingContactId, collectionId = effectiveCollectionId),
                )
                PendingMerge(
                    contact = withAvatar,
                    collectionId = effectiveCollectionId,
                    sourceType = "scan",
                )
            } ?: return CommitResult.NotFound
            enqueueAfterMerge(pending)
            BadgerLog.d(TAG, "attachScanned: written id=$existingContactId")
            CommitResult.Written(existingContactId)
        }.getOrElse { e ->
            BadgerLog.e(TAG, "attachScanned failed existingId=$existingContactId", e)
            CommitResult.SentFailed(e.message ?: "attachScanned")
        }
    }

    /**
     * 构建字段合并对比列表。查 fieldId 前 strip 后缀，同值跳过。
     */
    suspend fun buildMergeEntries(
        existingContactId: Long,
        newInfo: ExtractedContactInfo,
    ): List<FieldMergeEntry> {
        val existingMap = fieldValueMap(existingContactId)
        val enabledFields = fieldDao.getAllFieldsOnce().filter { it.isEnabled }
        val fieldNameMap = enabledFields.associate { it.fieldKey to it.fieldName }
        return newInfo.toFieldValues().mapNotNull { (key, newValue) ->
            val baseKey = stripFieldKeySuffix(key)
            val existingValue = existingMap[baseKey] ?: existingMap[key]
            if (existingValue != null && existingValue == newValue) return@mapNotNull null
            FieldMergeEntry(
                fieldKey = baseKey,
                fieldName = fieldNameMap[baseKey] ?: baseKey,
                existingValue = existingValue,
                newValue = newValue,
                selectedValue = MergeChoice.APPEND,
            )
        }
    }

    // ========== 事务内写入 ==========

    private suspend fun resolveCollectionId(preferredId: Long?): Long {
        if (preferredId != null && preferredId > 0L) {
            val exists = collectionDao.getCollectionById(preferredId) != null
            if (exists) return preferredId
        }
        val collections = collectionDao.getAllCollectionsOnce()
        if (collections.isNotEmpty()) return collections.first().id
        return collectionDao.insertCollection(
            CardCollectionCacheEntity(
                name = "默认名片夹",
                createTime = System.currentTimeMillis(),
                isLocalOnly = true,
            ),
        )
    }

    private suspend fun writeNewPlatforms(
        contactId: Long,
        entries: Map<String, PlatformEntry>,
        existingKeys: Set<String>,
    ) {
        for ((key, entry) in entries) {
            if (key in existingKeys) continue
            platformDao.insertPlatform(
                ContactPlatformCacheEntity(
                    contactId = contactId,
                    platformKey = key,
                    value = entry.value,
                    displayName = entry.displayName,
                    jumpLink = entry.jumpLink,
                    originalLink = entry.originalLink,
                    avatarUrl = entry.avatarUrl,
                ),
            )
        }
    }

    private suspend fun writeFieldMap(contactId: Long, fieldMap: List<Pair<Long, String>>) {
        if (fieldMap.isEmpty()) return
        val now = System.currentTimeMillis()
        fieldValueDao.insertOrUpdateFieldValues(
            fieldMap.map { (fieldId, value) ->
                ContactFieldValueCacheEntity(
                    contactId = contactId,
                    fieldId = fieldId,
                    value = value,
                    createTime = now,
                    updateTime = now,
                )
            },
        )
    }

    private suspend fun applyMergeChoices(contactId: Long, mergeEntries: List<FieldMergeEntry>) {
        val enabledFields = fieldDao.getAllFieldsOnce().filter { it.isEnabled }
        val fieldIdMap = enabledFields.associate { it.fieldKey to it.id }
        val allValues = fieldValueDao.getFieldValuesByContactOnce(contactId)
        val now = System.currentTimeMillis()
        for (entry in mergeEntries) {
            if (entry.selectedValue == MergeChoice.KEEP) continue
            val fieldId = fieldIdMap[entry.fieldKey] ?: continue
            val newValue = entry.newValue ?: continue
            when (entry.selectedValue) {
                MergeChoice.REPLACE -> {
                    val target = allValues.find { it.fieldId == fieldId }
                    if (target != null) {
                        fieldValueDao.updateFieldValue(
                            target.copy(value = newValue, updateTime = now),
                        )
                    } else {
                        fieldValueDao.insertOrUpdateFieldValues(
                            listOf(
                                ContactFieldValueCacheEntity(
                                    contactId = contactId,
                                    fieldId = fieldId,
                                    value = newValue,
                                    createTime = now,
                                    updateTime = now,
                                ),
                            ),
                        )
                    }
                }
                MergeChoice.APPEND -> {
                    fieldValueDao.insertOrUpdateFieldValues(
                        listOf(
                            ContactFieldValueCacheEntity(
                                contactId = contactId,
                                fieldId = fieldId,
                                value = newValue,
                                createTime = now,
                                updateTime = now,
                            ),
                        ),
                    )
                }
                MergeChoice.KEEP -> Unit
            }
        }
    }

    private suspend fun writeAttachFields(
        contactId: Long,
        info: ExtractedContactInfo,
        selectedFields: Set<String>,
    ) {
        val fieldMap = buildFieldMap(info, filterKeys = selectedFields)
        if (fieldMap.isEmpty()) return
        val existingValues = fieldValueDao.getFieldValuesByContactOnce(contactId)
        val insertList = fieldMap.filter { (fieldId, value) ->
            existingValues.none { it.fieldId == fieldId && it.value == value }
        }
        writeFieldMap(contactId, insertList)
    }

    private suspend fun writeCustomFields(contactId: Long, customFields: Map<Int, String>) {
        val defs = customFieldDao.getAllEnabledCustomFieldsOnce()
        val now = System.currentTimeMillis()
        val rows = customFields.mapNotNull { (_, value) ->
            val name = value.split(":").getOrNull(0)?.trim() ?: return@mapNotNull null
            val matched = defs.find { it.fieldName == name } ?: return@mapNotNull null
            ContactFieldValueCacheEntity(
                contactId = contactId,
                customFieldId = matched.id,
                value = value,
                createTime = now,
                updateTime = now,
            )
        }
        if (rows.isNotEmpty()) {
            fieldValueDao.insertOrUpdateFieldValues(rows)
        }
    }

    private suspend fun buildFieldMap(
        info: ExtractedContactInfo,
        filterKeys: Set<String>? = null,
    ): List<Pair<Long, String>> {
        val fields = fieldDao.getAllFieldsOnce().filter { it.isEnabled }
        val fieldKeyToId = fields.associate { it.fieldKey to it.id }
        val result = mutableListOf<Pair<Long, String>>()
        for ((key, value) in info.toFieldValues()) {
            val baseKey = stripFieldKeySuffix(key)
            if (baseKey in PLATFORM_FIELD_KEYS) continue
            if (filterKeys != null && baseKey !in filterKeys && key !in filterKeys) continue
            val fieldId = fieldKeyToId[baseKey] ?: continue
            result.add(fieldId to value)
        }
        return result
    }

    private suspend fun fieldValueMap(contactId: Long): Map<String, String> {
        val fieldValues = fieldValueDao.getFieldValuesByContactOnce(contactId)
        val fieldMap = fieldDao.getFieldsByIds(fieldValues.mapNotNull { it.fieldId }.distinct())
            .associateBy { it.id }
        return buildMap {
            for (fv in fieldValues) {
                val key = fv.fieldId?.let { fieldMap[it]?.fieldKey } ?: continue
                if (key !in this) put(key, fv.value)
            }
            for (platform in platformDao.getPlatformsByContact(contactId)) {
                val value = platform.value
                if (platform.platformKey.isNotBlank() && value != null && platform.platformKey !in this) {
                    put(platform.platformKey, value)
                }
            }
        }
    }

    // ========== 事务后入队 ==========

    private suspend fun enqueueAfterSave(pending: PendingSave) {
        try {
            val saved = contactDao.getContactById(pending.contactId)
            val platforms = platformDao.getPlatformsByContact(pending.contactId)
            val profile = saved?.let { ContactMapper.buildProfileDto(it, platforms) }
            serverApi.enqueueCreatePerson(pending.contactId, pending.name, profile, pending.clientUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "enqueueAfterSave: CREATE 入队失败(本地已保存) id=${pending.contactId}", e)
        }
        enqueueMember(pending.collectionId, pending.contactId)
    }

    private suspend fun enqueueAfterMerge(pending: PendingMerge) {
        val remoteId = ensureCreateEnqueued(pending.contact)
        try {
            val platforms = platformDao.getPlatformsByContact(pending.contact.id)
            serverApi.updatePerson(
                pending.contact.id,
                remoteId,
                name = pending.contact.name,
                profile = ContactMapper.buildProfileDto(pending.contact, platforms),
            )
        } catch (e: Exception) {
            BadgerLog.w(TAG, "enqueueAfterMerge: PATCH 入队失败(本地已保存) id=${pending.contact.id}", e)
        }
        enqueueMember(pending.collectionId, pending.contact.id)
    }

    private suspend fun ensureCreateEnqueued(contact: ContactCacheEntity): String {
        val identity = contact.identity()
        val remoteId = when (identity) {
            is RemoteIdentity.Synced -> identity.serverId
            is RemoteIdentity.PendingCreate -> identity.clientUuid
            is RemoteIdentity.Unidentified -> randomUuid()
        }
        if (identity is RemoteIdentity.Unidentified) {
            contactDao.updateContact(contact.copy(serverId = remoteId, isLocalOnly = true))
        }
        if (identity !is RemoteIdentity.Synced) {
            try {
                val platforms = platformDao.getPlatformsByContact(contact.id)
                serverApi.enqueueCreatePerson(
                    contact.id,
                    contact.name,
                    ContactMapper.buildProfileDto(contact, platforms),
                    remoteId,
                )
            } catch (e: Exception) {
                BadgerLog.w(TAG, "ensureCreateEnqueued: CREATE 入队失败 contactId=${contact.id}", e)
            }
        }
        return remoteId
    }

    private suspend fun enqueueMember(collectionId: Long, contactId: Long) {
        val collection = collectionDao.getCollectionById(collectionId) ?: return
        val identity = collection.identity()
        val colUuid = when (identity) {
            is RemoteIdentity.Synced -> identity.serverId
            is RemoteIdentity.PendingCreate -> identity.clientUuid
            is RemoteIdentity.Unidentified -> randomUuid()
        }
        if (identity is RemoteIdentity.Unidentified) {
            collectionDao.updateCollection(collection.copy(serverId = colUuid, isLocalOnly = true))
        }
        if (identity !is RemoteIdentity.Synced) {
            try {
                serverApi.enqueueCreateCollection(
                    localId = collection.id,
                    name = collection.name,
                    description = collection.description,
                    backgroundURL = collection.coverAvatarUrl,
                    clientUuid = colUuid,
                )
            } catch (e: Exception) {
                BadgerLog.w(TAG, "enqueueMember: collection CREATE 入队失败 id=$collectionId", e)
            }
        }
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addCollectionMember(collectionId, colUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "enqueueMember: MEMBER 入队失败 col=$collectionId person=$contactId", e)
        }
    }

    private data class PendingSave(
        val contactId: Long,
        val collectionId: Long,
        val clientUuid: String,
        val name: String,
        val sourceType: String,
    )

    private data class PendingMerge(
        val contact: ContactCacheEntity,
        val collectionId: Long,
        val sourceType: String,
    )

    companion object {
        private const val TAG = "ContactWriter"

        /** 剥离去重后缀，如 `qq_1` → `qq`。 */
        fun stripFieldKeySuffix(key: String): String {
            val idx = key.lastIndexOf('_')
            if (idx <= 0) return key
            val suffix = key.substring(idx + 1)
            return if (suffix.all { it.isDigit() }) key.substring(0, idx) else key
        }

        internal fun buildPlatformEntries(info: ExtractedContactInfo): Map<String, PlatformEntry> {
            val result = mutableMapOf<String, PlatformEntry>()
            for ((key, value) in info.platforms) {
                val baseKey = stripFieldKeySuffix(key)
                if (baseKey !in PLATFORM_FIELD_KEYS) continue
                if (value.isBlank()) continue
                if (baseKey in result) continue
                result[baseKey] = PlatformEntry(
                    jumpLink = buildPlatformLink(baseKey, value),
                    value = value,
                )
            }
            return result
        }
    }
}
