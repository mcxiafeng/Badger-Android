package top.mcxiafeng.badger.testutil

import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.ContactFieldCacheEntity as ContactField
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity as ContactFieldValue
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper

object TestDataProvider {
    fun testContact(
        id: Long = 0,
        name: String = "测试联系人",
        avatarUrl: String? = null,
        avatarPath: String? = null,
        note: String? = null
    ): Contact = Contact(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        avatarPath = avatarPath,
        note = note,
        createTime = System.currentTimeMillis(),
        updateTime = System.currentTimeMillis(),
    )

    fun testContactField(
        id: Long = 0,
        fieldKey: String = "phone",
        fieldName: String = "手机",
        isSystem: Boolean = true,
        isEnabled: Boolean = true,
        sortOrder: Int = 0
    ): ContactField = ContactField(
        id = id,
        fieldKey = fieldKey,
        fieldName = fieldName,
        isSystem = isSystem,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
        createTime = System.currentTimeMillis(),
    )

    fun testFieldValue(
        id: Long = 0,
        contactId: Long = 1,
        fieldId: Long? = null,
        customFieldId: Long? = null,
        value: String = "test"
    ): ContactFieldValue = ContactFieldValue(
        id = id,
        contactId = contactId,
        fieldId = fieldId,
        customFieldId = customFieldId,
        value = value,
        createTime = System.currentTimeMillis(),
        updateTime = System.currentTimeMillis(),
    )

    fun testCardCollection(
        id: Long = 0,
        name: String = "测试名片夹",
        description: String? = null
    ): CardCollection = CardCollection(
        id = id,
        name = name,
        description = description,
        createTime = System.currentTimeMillis(),
    )

    fun testUserProfile(
        name: String = "测试用户",
        platforms: Map<String, PlatformEntry>? = null,
        defaultPlatform: String? = null
    ): UserProfile = UserProfile(
        id = 1L,
        name = name,
        defaultPlatform = defaultPlatform,
        platformsJson = ContactMapper.encodePlatformsMap(platforms),
        updateTime = System.currentTimeMillis(),
    )
}
