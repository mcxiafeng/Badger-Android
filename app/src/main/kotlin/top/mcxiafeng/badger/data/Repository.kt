package top.mcxiafeng.badger.data

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

/**
 * 联系人数据仓库实现
 *
 * 封装所有数据访问层的操作，为 UI 层提供统一的数据接口。
 * 所有耗时操作都通过 [withContext] 切换到 IO 调度器执行。
 * 接口定义见 [ContactRepository]。
 */
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFieldDao: ContactFieldDao,
    private val customFieldDao: CustomFieldDao,
    private val contactFieldValueDao: ContactFieldValueDao,
    private val scanResultDao: ScanResultDao,
    private val collectionDao: CardCollectionDao,
    private val userProfileDao: UserProfileDao
) : ContactRepository {

    // 防止 UserProfile 的并发 read-modify-write 竞态
    private val userProfileMutex = Mutex()
    // 防止 Contact 社交平台操作的并发 read-modify-write 竞态（全局互斥）
    private val contactMutex = Mutex()
    // 防止 CardCollection 的并发 read-modify-write 竞态（如 autoAssignTheme）
    private val collectionMutex = Mutex()
    
    // ========== 联系人基本操作 ==========
    
    /** 获取所有联系人（响应式 Flow） */
    override fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()
    
    /** 根据 ID 获取联系人 */
    override suspend fun getContactById(id: Long): Contact? = withContext(Dispatchers.IO) {
        contactDao.getContactById(id)
    }
    
    /**
     * 获取所有联系人（不含字段值）
     *
     * @note 当前实现未加载字段值，返回的 [ContactWithFields] 中 fieldValues 为空列表。
     *       如需完整数据，请使用 [getContactWithFieldsById]。
     */
    override fun getAllContactsWithFields(): Flow<List<ContactWithFields>> {
        return contactDao.getAllContacts().map { contacts ->
            contacts.map { contact ->
                ContactWithFields(contact, emptyList())
            }
        }
    }
    
    /**
     * 根据 ID 获取联系人及其所有字段值
     *
     * 将系统预置字段值和自定义字段值合并后按 sortOrder 排序返回。
     * 仅包含已启用的字段。
     */
    override suspend fun getContactWithFieldsById(id: Long): ContactWithFields? = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(id) ?: return@withContext null
        val fieldValues = contactFieldValueDao.getFieldValuesByContactOnce(id)

        // 批量收集需要查询的 fieldId 和 customFieldId
        val fieldIds = fieldValues.mapNotNull { it.fieldId }.distinct()
        val customFieldIds = fieldValues.mapNotNull { it.customFieldId }.distinct()

        // 一次性查询所有字段定义，避免 N+1
        val fieldMap = if (fieldIds.isNotEmpty()) {
            contactFieldDao.getFieldsByIds(fieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()
        val customFieldMap = if (customFieldIds.isNotEmpty()) {
            customFieldDao.getCustomFieldsByIds(customFieldIds).filter { it.isEnabled }.associateBy { it.id }
        } else emptyMap()

        // 合并系统预置字段值和自定义字段值，按 sortOrder 排序返回。
        // 不再按 fieldKey 去重——同一联系人可以有多个相同字段类型的值（如多个手机号）。
        val fields = fieldValues.mapNotNull { value ->
            if (value.fieldId != null) {
                val field = fieldMap[value.fieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = field.id,
                    customFieldId = null,
                    fieldName = field.fieldName,
                    fieldKey = field.fieldKey,
                    icon = field.icon,
                    fieldType = null,
                    value = value.value,
                    sortOrder = field.sortOrder
                )
            } else if (value.customFieldId != null) {
                val customField = customFieldMap[value.customFieldId] ?: return@mapNotNull null
                ContactFieldDisplay(
                    valueId = value.id,
                    fieldId = null,
                    customFieldId = customField.id,
                    fieldName = customField.fieldName,
                    fieldKey = null,
                    icon = null,
                    fieldType = customField.fieldType,
                    value = value.value,
                    sortOrder = customField.sortOrder
                )
            } else null
        }.sortedBy { it.sortOrder }

        ContactWithFields(contact, fields)
    }
    
    /** 插入新联系人，返回插入后的 ID */
    override suspend fun insertContact(contact: Contact): Long = withContext(Dispatchers.IO) {
        contactDao.insertContact(contact)
    }
    
    /** 更新联系人信息 */
    override suspend fun updateContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.updateContact(contact)
    }
    
    /** 删除联系人及其所有关联数据 */
    override suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.deleteContact(contact)
    }
    
    /** 模糊搜索联系人（搜索姓名和字段值） */
    override fun searchContacts(query: String): Flow<List<Contact>> {
        return contactDao.searchContacts(query)
    }

    // ========== 系统预置字段操作 ==========
    
    /** 获取所有已启用的系统预置字段 */
    override fun getAllEnabledFields(): Flow<List<ContactField>> = contactFieldDao.getAllEnabledFields()
    
    /** 获取所有系统预置字段（一次性） */
    override suspend fun getAllFieldsOnce(): List<ContactField> = withContext(Dispatchers.IO) {
        contactFieldDao.getAllFieldsOnce()
    }
    
    /** 根据字段标识键获取字段定义 */
    override suspend fun getFieldByKey(key: String): ContactField? = withContext(Dispatchers.IO) {
        contactFieldDao.getFieldByKey(key)
    }
    
    /** 根据 ID 获取字段定义 */
    override suspend fun getFieldById(id: Long): ContactField? = withContext(Dispatchers.IO) {
        contactFieldDao.getFieldById(id)
    }
    
    /** 新增系统预置字段 */
    override suspend fun insertField(field: ContactField): Long = withContext(Dispatchers.IO) {
        contactFieldDao.insertField(field)
    }
    
    /** 更新系统预置字段 */
    override suspend fun updateField(field: ContactField) = withContext(Dispatchers.IO) {
        contactFieldDao.updateField(field)
    }
    
    /**
     * 删除系统预置字段
     *
     * @note 系统预置字段 ([isSystem] = true) 不允许删除，调用此方法会静默忽略。
     */
    override suspend fun deleteField(field: ContactField) = withContext(Dispatchers.IO) {
        if (!field.isSystem) {
            contactFieldDao.deleteField(field)
        }
    }
    
    /** 设置系统预置字段的启用/禁用状态 */
    override suspend fun setFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        contactFieldDao.setFieldEnabled(id, enabled)
    }
    
    /** 更新系统预置字段的排序权重 */
    override suspend fun updateFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        contactFieldDao.updateFieldOrder(id, order)
    }

    // ========== 自定义字段操作 ==========
    
    /** 获取所有已启用的自定义字段 */
    override fun getAllEnabledCustomFields(): Flow<List<CustomField>> = customFieldDao.getAllEnabledCustomFields()
    
    /** 根据 ID 获取自定义字段 */
    override suspend fun getCustomFieldById(id: Long): CustomField? = withContext(Dispatchers.IO) {
        customFieldDao.getCustomFieldById(id)
    }
    
    /** 新增自定义字段 */
    override suspend fun insertCustomField(field: CustomField): Long = withContext(Dispatchers.IO) {
        customFieldDao.insertCustomField(field)
    }
    
    /** 更新自定义字段 */
    override suspend fun updateCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldDao.updateCustomField(field)
    }
    
    /** 删除自定义字段 */
    override suspend fun deleteCustomField(field: CustomField) = withContext(Dispatchers.IO) {
        customFieldDao.deleteCustomField(field)
    }
    
    /** 设置自定义字段的启用/禁用状态 */
    override suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        customFieldDao.setCustomFieldEnabled(id, enabled)
    }
    
    /** 更新自定义字段的排序权重 */
    override suspend fun updateCustomFieldOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        customFieldDao.updateCustomFieldOrder(id, order)
    }
    
    // ========== 字段值操作 ==========
    
    /** 获取指定联系人的所有字段值（一次性，非 Flow） */
    override suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue> = withContext(Dispatchers.IO) {
        contactFieldValueDao.getFieldValuesByContactOnce(contactId)
    }
    
    /** 插入或更新单个字段值 */
    override suspend fun insertFieldValue(value: ContactFieldValue): Long = withContext(Dispatchers.IO) {
        contactFieldValueDao.insertFieldValue(value)
    }
    
    /** 更新字段值 */
    override suspend fun updateFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueDao.updateFieldValue(value)
    }
    
    /** 删除字段值 */
    override suspend fun deleteFieldValue(value: ContactFieldValue) = withContext(Dispatchers.IO) {
        contactFieldValueDao.deleteFieldValue(value)
    }
    
    /**
     * 批量保存联系人的系统预置字段值
     *
     * @param contactId 联系人ID
     * @param fieldValues 字段ID到值的映射
     */
    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (fieldId, value) ->
            ContactFieldValue(
                contactId = contactId,
                fieldId = fieldId,
                value = value
            )
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }

    override suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (fieldId, value) ->
            ContactFieldValue(
                contactId = contactId,
                fieldId = fieldId,
                value = value
            )
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }
    
    /**
     * 批量保存联系人的自定义字段值
     *
     * @param contactId 联系人ID
     * @param fieldValues 自定义字段ID到值的映射
     */
    override suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>) = withContext(Dispatchers.IO) {
        val values = fieldValues.map { (customFieldId, value) ->
            ContactFieldValue(
                contactId = contactId,
                customFieldId = customFieldId,
                value = value
            )
        }
        contactFieldValueDao.insertOrUpdateFieldValues(values)
    }
    
    /**
     * 根据字段标识键获取联系人的字段值
     *
     * @param contactId 联系人ID
     * @param fieldKey 系统字段的标识键（如"phone"、"email"）
     * @return 字段值，不存在则返回 null
     */
    override suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String? = withContext(Dispatchers.IO) {
        val field = contactFieldDao.getFieldByKey(fieldKey) ?: return@withContext null
        return@withContext contactFieldValueDao.getFieldValue(contactId, field.id)
    }
    
    /**
     * 根据自定义字段ID获取联系人的字段值
     */
    override suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String? = withContext(Dispatchers.IO) {
        contactFieldValueDao.getCustomFieldValue(contactId, customFieldId)
    }

    // ========== 名片夹操作 ==========
    
    /** 获取所有名片夹 */
    override fun getAllCollections(): Flow<List<CardCollection>> = collectionDao.getAllCollections()

    /** 获取所有名片夹（一次性） */
    override suspend fun getAllCollectionsOnce(): List<CardCollection> = withContext(Dispatchers.IO) {
        collectionDao.getAllCollectionsOnce()
    }

    /** 获取名片夹下的联系人（一次性） */
    override suspend fun getContactsByCollectionOnce(collectionId: Long): List<Contact> = withContext(Dispatchers.IO) {
        contactDao.getContactsByCollectionOnce(collectionId)
    }

    /** 获取所有名片夹及其联系人数量 */
    override fun getCollectionsWithCount(): Flow<List<CollectionWithCount>> = collectionDao.getCollectionsWithCount()
    
    /** 根据 ID 获取名片夹 */
    override suspend fun getCollectionById(id: Long): CardCollection? = withContext(Dispatchers.IO) {
        collectionDao.getCollectionById(id)
    }
    
    /** 创建名片夹 */
    override suspend fun insertCollection(collection: CardCollection): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(collection)
    }

    /** 更新名片夹信息 */
    override suspend fun updateCollection(collection: CardCollection) = collectionMutex.withLock {
        Log.d("Tester", "updateCollection: id=${collection.id}, name=${collection.name}, dominantColor=${collection.dominantColor}")
        withContext(Dispatchers.IO) {
            collectionDao.updateCollection(collection)
        }
    }

    /** 删除名片夹及其所有关联的扫描记录 */
    override suspend fun deleteCollection(collection: CardCollection) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(collection)
    }
    
    /** 获取指定名片夹下的所有联系人 */
    override fun getContactsByCollection(collectionId: Long): Flow<List<Contact>> {
        return contactDao.getContactsByCollection(collectionId)
    }

    // ========== 扫描记录操作 ==========

    /** 获取指定联系人的所有扫描记录 */
    override fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>> {
        return scanResultDao.getScanResultsByContact(contactId)
    }

    /**
     * 将联系人添加到名片夹
     *
     * 同时记录扫描来源信息（原始数据、OCR文本、二维码内容）。
     * 同一联系人在同一名片夹可以有多条记录（不同样式）。
     *
     * @param contactId 联系人ID
     * @param collectionId 名片夹ID
     * @param sourceType 来源类型："scan"（扫码）或 "photo"（拍照）或 "manual"（手动添加）
     * @param styleColor 名片主色调（ARGB Long），可选
     * @param rawData 原始扫描数据
     * @param ocrText OCR识别文本
     * @param qrCodeContent 二维码内容
     */
    override suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        styleColor: Long?,
        rawData: String?,
        ocrText: String?,
        qrCodeContent: String?
    ) = withContext(Dispatchers.IO) {
        val result = ScanResult(
            contactId = contactId,
            collectionId = collectionId,
            sourceType = sourceType,
            styleColor = styleColor,
            rawData = rawData,
            ocrText = ocrText,
            qrCodeContent = qrCodeContent
        )
        scanResultDao.insertScanResult(result)
    }

    /**
     * 根据主键删除扫描记录
     */
    override suspend fun deleteScanResultById(id: Long) = withContext(Dispatchers.IO) {
        scanResultDao.deleteScanResultById(id)
    }

    /**
     * 将联系人从名片夹移除
     */
    override suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) = withContext(Dispatchers.IO) {
        scanResultDao.deleteScanResultsByContactAndCollection(contactId, collectionId)
    }

    // ========== 重复检测 ==========
    
    /**
     * 检查联系人是否与已有联系人重复
     *
     * 算法逻辑：
     * 1. 遍历新联系人的每个字段值，搜索已有联系人
     * 2. 对每个潜在重复，逐字段比对计算匹配分数
     * 3. 名字相似度超过 70% 时额外加权 0.5 分
     * 4. 匹配分数 >= 1.0 时判定为重复
     *
     * @param newContactName 新联系人姓名
     * @param fieldValues 系统字段键值对（如 "phone" -> "13800138000"）
     * @param customFieldValues 自定义字段键值对
     * @return 重复检查结果
     */
    override suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<String, String>
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        if (fieldValues.isEmpty() && customFieldValues.isEmpty()) {
            return@withContext DuplicateCheckResult(
                isDuplicate = false,
                existingContact = null,
                similarityScore = 0f,
                matchFields = emptyList()
            )
        }

        var bestMatch: Contact? = null
        var bestScore = 0f
        var matchedFields = emptyList<String>()

        val allContacts = contactDao.getAllContacts().first()
        val platformKeys = fieldValues.keys.filter { it in PLATFORM_FIELD_KEYS }.toSet()
        val regularKeys = fieldValues.keys - platformKeys

        for ((key, value) in fieldValues) {
            if (value.isBlank()) continue

            if (key in platformKeys) {
                // 检查 Contact.platforms 中的平台字段
                for (contact in allContacts) {
                    val platforms = contact.platforms ?: continue
                    val entry = platforms[key] ?: continue
                    if (entry.value == value) {
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
                }
            } else {
                // 原有逻辑：检查 contact_field_values
                val potentialDuplicates = scanResultDao.findPotentialDuplicates(value, null)
                for (potential in potentialDuplicates) {
                    var score = 0f
                    val fields = mutableListOf<String>()
                    val existingValues = contactFieldValueDao.getFieldValuesByContactOnce(potential.id)
                    for (existingValue in existingValues) {
                        val fieldId = existingValue.fieldId ?: continue
                        val field = contactFieldDao.getFieldById(fieldId)
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

        Log.d("Tester", "checkDuplicate: bestScore=$bestScore, bestMatch=${bestMatch?.id}, matchedFields=$matchedFields")

        DuplicateCheckResult(
            isDuplicate = bestScore >= 1.0f,
            existingContact = bestMatch,
            similarityScore = bestScore.coerceIn(0f, 2f),
            matchFields = matchedFields
        )
    }

    /**
     * 计算两个名字的相似度
     *
     * 使用 Jaccard 相似度（基于字符集的交并比）。
     * 完全相同返回 1.0，完全不同返回 0.0。
     */
    private fun calculateNameSimilarity(name1: String, name2: String): Float {
        if (name1.equals(name2, ignoreCase = true)) return 1.0f
        
        val set1 = name1.lowercase().toSet()
        val set2 = name2.lowercase().toSet()
        val intersection = set1.intersect(set2).size.toFloat()
        val union = set1.union(set2).size.toFloat()
        
        return if (union > 0) intersection / union else 0f
    }

    override suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val fieldValues = contactFieldValueDao.getFieldValuesByContactOnce(contactId)
        val map = mutableMapOf<String, String>()
        for (fv in fieldValues) {
            val key = when {
                fv.fieldId != null -> contactFieldDao.getFieldById(fv.fieldId)?.fieldKey
                fv.customFieldId != null -> "custom_${fv.customFieldId}"
                else -> null
            }
            if (key != null && key !in map) map[key] = fv.value
        }
        map
    }

    override suspend fun getStyleCountsByCollection(collectionId: Long): Map<Long, Int> = withContext(Dispatchers.IO) {
        scanResultDao.getStyleCountsByCollection(collectionId)
    }

    // ========== 联系人社交平台操作 ==========

    override suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val contact = contactDao.getContactById(contactId) ?: return@withContext
            val newPlatforms = (contact.platforms?.toMutableMap() ?: mutableMapOf()).apply {
                if (entry.jumpLink.isBlank() && entry.value.isNullOrBlank()) {
                    remove(fieldKey)
                } else {
                    this[fieldKey] = entry
                }
            }
            val updated = contact.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            contactDao.updateContact(updated)
        }
    }

    override suspend fun removeContactPlatform(contactId: Long, fieldKey: String) = contactMutex.withLock {
        withContext(Dispatchers.IO) {
            val contact = contactDao.getContactById(contactId) ?: return@withContext
            val newPlatforms = (contact.platforms?.toMutableMap() ?: mutableMapOf()).apply {
                remove(fieldKey)
            }
            val updated = contact.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            contactDao.updateContact(updated)
        }
    }

    // ========== 用户个人资料（我的名片）==========

    /** 获取用户资料（响应式 Flow） */
    override fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getProfile()

    /** 一次性获取用户资料 */
    override suspend fun getUserProfileOnce(): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getProfileOnce()
    }

    /** 保存或更新用户资料 */
    override suspend fun saveUserProfile(profile: UserProfile) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            userProfileDao.saveProfile(profile)
        }
    }

    override suspend fun updateAvatarPath(avatarPath: String?) = userProfileMutex.withLock {
        Log.d("Tester", "updateAvatarPath: avatarPath=$avatarPath")
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: run {
                Log.d("Tester", "updateAvatarPath: profile is null, skipping")
                return@withContext
            }
            userProfileDao.saveProfile(profile.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis()))
        }
    }

    override suspend fun updateCardImagePath(cardImagePath: String?) = userProfileMutex.withLock {
        Log.d("Tester", "updateCardImagePath: cardImagePath=$cardImagePath")
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: run {
                Log.d("Tester", "updateCardImagePath: profile is null, skipping")
                return@withContext
            }
            userProfileDao.saveProfile(profile.copy(cardImagePath = cardImagePath, updateTime = System.currentTimeMillis()))
        }
    }

    /**
     * 更新用户资料的某个平台
     *
     * @param fieldKey 平台标识键（如 "qq"、"wechat"），语义为 fieldKey
     * @param jumpLink 跳转链接
     * @param value 平台ID/账号
     * @param displayName 平台昵称
     * @param avatarUrl 平台头像 URL
     * @param originalLink 用户粘贴的原始链接
     */
    override suspend fun updatePlatformField(fieldKey: String, jumpLink: String, value: String?, displayName: String?, avatarUrl: String?, originalLink: String?) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce()
                ?: UserProfile(name = "用户", updateTime = System.currentTimeMillis())
            val newPlatforms = (profile.platforms?.toMutableMap() ?: mutableMapOf()).apply {
                if (jumpLink.isBlank() && value.isNullOrBlank()) {
                    remove(fieldKey)
                } else {
                    this[fieldKey] = PlatformEntry(
                        displayName = displayName?.ifBlank { null },
                        jumpLink = jumpLink,
                        originalLink = originalLink?.ifBlank { null },
                        value = value?.ifBlank { null },
                        avatarUrl = avatarUrl?.ifBlank { null }
                    )
                }
            }
            val updated = profile.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            userProfileDao.saveProfile(updated)
        }
    }

    /**
     * 删除用户资料的某个平台
     *
     * @param platformName 平台名称
     */
    override suspend fun removePlatform(platformName: String) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: return@withContext
            val newPlatforms = profile.platforms?.toMutableMap() ?: mutableMapOf()
            newPlatforms.remove(platformName)
            val updated = profile.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            userProfileDao.saveProfile(updated)
        }
    }
}
