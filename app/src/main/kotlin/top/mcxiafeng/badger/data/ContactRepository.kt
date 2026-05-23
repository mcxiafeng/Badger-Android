package top.mcxiafeng.badger.data

import kotlinx.coroutines.flow.Flow

/**
 * 联系人数据仓库接口
 *
 * 定义所有数据访问层的操作契约，为 UI 层提供统一的数据接口。
 * 具体实现见 [ContactRepositoryImpl]。
 */
interface ContactRepository {

    // ========== 联系人基本操作 ==========

    /** 获取所有联系人（响应式 Flow） */
    fun getAllContacts(): Flow<List<Contact>>

    /** 根据 ID 获取联系人 */
    suspend fun getContactById(id: Long): Contact?

    /**
     * 获取所有联系人（不含字段值）
     *
     * @note 当前实现未加载字段值，返回的 [ContactWithFields] 中 fieldValues 为空列表。
     *       如需完整数据，请使用 [getContactWithFieldsById]。
     */
    fun getAllContactsWithFields(): Flow<List<ContactWithFields>>

    /**
     * 根据 ID 获取联系人及其所有字段值
     *
     * 将系统预置字段值和自定义字段值合并后按 sortOrder 排序返回。
     * 仅包含已启用的字段。
     */
    suspend fun getContactWithFieldsById(id: Long): ContactWithFields?

    /** 插入新联系人，返回插入后的 ID */
    suspend fun insertContact(contact: Contact): Long

    /** 更新联系人信息 */
    suspend fun updateContact(contact: Contact)

    /** 删除联系人及其所有关联数据 */
    suspend fun deleteContact(contact: Contact)

    /** 模糊搜索联系人（搜索姓名和字段值） */
    fun searchContacts(query: String): Flow<List<Contact>>

    // ========== 系统预置字段操作 ==========

    /** 获取所有已启用的系统预置字段 */
    fun getAllEnabledFields(): Flow<List<ContactField>>

    /** 获取所有系统预置字段（一次性） */
    suspend fun getAllFieldsOnce(): List<ContactField>

    /** 根据字段标识键获取字段定义 */
    suspend fun getFieldByKey(key: String): ContactField?

    /** 根据 ID 获取字段定义 */
    suspend fun getFieldById(id: Long): ContactField?

    /** 新增系统预置字段 */
    suspend fun insertField(field: ContactField): Long

    /** 更新系统预置字段 */
    suspend fun updateField(field: ContactField)

    /**
     * 删除系统预置字段
     *
     * @note 系统预置字段 ([isSystem] = true) 不允许删除，调用此方法会静默忽略。
     */
    suspend fun deleteField(field: ContactField)

    /** 设置系统预置字段的启用/禁用状态 */
    suspend fun setFieldEnabled(id: Long, enabled: Boolean)

    /** 更新系统预置字段的排序权重 */
    suspend fun updateFieldOrder(id: Long, order: Int)

    // ========== 自定义字段操作 ==========

    /** 获取所有已启用的自定义字段 */
    fun getAllEnabledCustomFields(): Flow<List<CustomField>>

    /** 根据 ID 获取自定义字段 */
    suspend fun getCustomFieldById(id: Long): CustomField?

    /** 新增自定义字段 */
    suspend fun insertCustomField(field: CustomField): Long

    /** 更新自定义字段 */
    suspend fun updateCustomField(field: CustomField)

    /** 删除自定义字段 */
    suspend fun deleteCustomField(field: CustomField)

    /** 设置自定义字段的启用/禁用状态 */
    suspend fun setCustomFieldEnabled(id: Long, enabled: Boolean)

    /** 更新自定义字段的排序权重 */
    suspend fun updateCustomFieldOrder(id: Long, order: Int)

    // ========== 字段值操作 ==========

    /** 获取指定联系人的所有字段值（一次性，非 Flow） */
    suspend fun getFieldValuesByContactOnce(contactId: Long): List<ContactFieldValue>

    /** 插入或更新单个字段值 */
    suspend fun insertFieldValue(value: ContactFieldValue): Long

    /** 更新字段值 */
    suspend fun updateFieldValue(value: ContactFieldValue)

    /** 删除字段值 */
    suspend fun deleteFieldValue(value: ContactFieldValue)

    /**
     * 批量保存联系人的系统预置字段值
     *
     * @param contactId 联系人ID
     * @param fieldValues 字段ID到值的映射
     */
    suspend fun saveContactFieldValues(contactId: Long, fieldValues: Map<Long, String>)

    /**
     * 批量保存联系人的系统预置字段值（支持同字段多值）
     *
     * @param contactId 联系人ID
     * @param fieldValues 字段ID与值的配对列表，同一 fieldId 可出现多次
     */
    suspend fun saveContactFieldValues(contactId: Long, fieldValues: List<Pair<Long, String>>)

    /**
     * 批量保存联系人的自定义字段值
     *
     * @param contactId 联系人ID
     * @param fieldValues 自定义字段ID到值的映射
     */
    suspend fun saveContactCustomFieldValues(contactId: Long, fieldValues: Map<Long, String>)

    /**
     * 根据字段标识键获取联系人的字段值
     *
     * @param contactId 联系人ID
     * @param fieldKey 系统字段的标识键（如"phone"、"email"）
     * @return 字段值，不存在则返回 null
     */
    suspend fun getFieldValueByContactAndKey(contactId: Long, fieldKey: String): String?

    /**
     * 根据自定义字段ID获取联系人的字段值
     */
    suspend fun getCustomFieldValueByContactAndFieldId(contactId: Long, customFieldId: Long): String?

    // ========== 名片夹操作 ==========

    /** 获取所有名片夹 */
    fun getAllCollections(): Flow<List<CardCollection>>

    /** 获取所有名片夹（一次性） */
    suspend fun getAllCollectionsOnce(): List<CardCollection>

    /** 获取名片夹下的联系人（一次性） */
    suspend fun getContactsByCollectionOnce(collectionId: Long): List<Contact>

    /** 获取所有名片夹及其联系人数量 */
    fun getCollectionsWithCount(): Flow<List<CollectionWithCount>>

    /** 根据 ID 获取名片夹 */
    suspend fun getCollectionById(id: Long): CardCollection?

    /** 创建名片夹 */
    suspend fun insertCollection(collection: CardCollection): Long

    /** 删除名片夹及其所有关联的扫描记录 */
    suspend fun deleteCollection(collection: CardCollection)

    /** 获取指定名片夹下的所有联系人 */
    fun getContactsByCollection(collectionId: Long): Flow<List<Contact>>

    // ========== 扫描记录操作 ==========

    /** 获取指定联系人的所有扫描记录 */
    fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>>

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
    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        styleColor: Long? = null,
        rawData: String? = null,
        ocrText: String? = null,
        qrCodeContent: String? = null
    )

    /**
     * 根据主键删除扫描记录
     */
    suspend fun deleteScanResultById(id: Long)

    /**
     * 将联系人从名片夹移除
     */
    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long)

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
    suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<String, String>
    ): DuplicateCheckResult

    /** 获取联系人的所有字段值，以 fieldKey→value 映射返回 */
    suspend fun getFieldValueMapByContact(contactId: Long): Map<String, String>

    /** 获取指定名片夹下每个联系人的样式数量 */
    suspend fun getStyleCountsByCollection(collectionId: Long): Map<Long, Int>

    // ========== 联系人社交平台操作 ==========

    /**
     * 更新联系人的某个社交平台
     *
     * @param contactId 联系人ID
     * @param fieldKey 平台标识键（如 "qq"、"wechat"）
     * @param entry 平台条目
     */
    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)

    /**
     * 删除联系人的某个社交平台
     *
     * @param contactId 联系人ID
     * @param fieldKey 平台标识键
     */
    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    // ========== 用户个人资料（我的名片）==========

    /** 获取用户资料（响应式 Flow） */
    fun getUserProfile(): Flow<UserProfile?>

    /** 一次性获取用户资料 */
    suspend fun getUserProfileOnce(): UserProfile?

    /** 保存或更新用户资料 */
    suspend fun saveUserProfile(profile: UserProfile)

    /**
     * 更新用户资料的某个平台
     *
     * @param fieldKey 平台标识键（如 "qq"、"wechat"）
     * @param jumpLink 跳转链接
     * @param value 平台ID/账号
     * @param displayName 平台昵称
     * @param avatarUrl 平台头像 URL
     * @param originalLink 用户粘贴的原始链接
     */
    suspend fun updatePlatformField(fieldKey: String, jumpLink: String, value: String? = null, displayName: String? = null, avatarUrl: String? = null, originalLink: String? = null)

    /**
     * 删除用户资料的某个平台
     *
     * @param platformName 平台名称
     */
    suspend fun removePlatform(platformName: String)
}
