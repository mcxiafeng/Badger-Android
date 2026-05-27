package top.mcxiafeng.badger.testutil

import top.mcxiafeng.badger.data.*

object TestDataProvider {
    fun testContact(
        id: Long = 0,
        name: String = "测试联系人",
        avatarUrl: String? = null,
        avatarPath: String? = null,
        note: String? = null
    ) = Contact(
        id = id, name = name, avatarUrl = avatarUrl,
        avatarPath = avatarPath, note = note
    )

    fun testContactField(
        id: Long = 0,
        fieldKey: String = "phone",
        fieldName: String = "手机",
        isSystem: Boolean = true,
        isEnabled: Boolean = true,
        sortOrder: Int = 0
    ) = ContactField(
        id = id, fieldKey = fieldKey, fieldName = fieldName,
        isSystem = isSystem, isEnabled = isEnabled, sortOrder = sortOrder
    )

    fun testCustomField(
        id: Long = 0,
        fieldName: String = "公司",
        fieldType: String = "text",
        isEnabled: Boolean = true,
        sortOrder: Int = 0
    ) = CustomField(
        id = id, fieldName = fieldName, fieldType = fieldType,
        options = "[]", isEnabled = isEnabled, sortOrder = sortOrder
    )

    fun testFieldValue(
        id: Long = 0,
        contactId: Long = 1,
        fieldId: Long? = null,
        customFieldId: Long? = null,
        value: String = "test"
    ) = ContactFieldValue(
        id = id, contactId = contactId,
        fieldId = fieldId, customFieldId = customFieldId, value = value
    )

    fun testCardCollection(
        id: Long = 0,
        name: String = "测试名片夹",
        description: String? = null
    ) = CardCollection(id = id, name = name, description = description)

    fun testScanResult(
        id: Long = 0,
        contactId: Long = 1,
        collectionId: Long = 1,
        sourceType: String = "scan",
        qrCodeContent: String? = null,
        ocrText: String? = null
    ) = ScanResult(
        id = id, contactId = contactId, collectionId = collectionId,
        sourceType = sourceType, qrCodeContent = qrCodeContent, ocrText = ocrText
    )

    fun testUserProfile(
        name: String = "测试用户",
        platforms: Map<String, PlatformEntry>? = null,
        defaultPlatform: String? = null
    ) = UserProfile(
        id = 1L, name = name, platforms = platforms, defaultPlatform = defaultPlatform
    )
}
