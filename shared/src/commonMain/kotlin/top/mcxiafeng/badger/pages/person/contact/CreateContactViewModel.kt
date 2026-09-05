package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadAndStoreAvatar

private const val TAG = "CreateContactVM"

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class CreateContactViewModel : ViewModel() {

    val contactRepository: ContactRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    val collectionRepository: CollectionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    suspend fun createMinimalContact(name: String, collectionId: Long?): Long {
        val now = nowMs()
        val contact = Contact(
            id = 0L,
            name = name,
            createTime = now,
            updateTime = now,
        )
        val contactId = contactRepository.insertContact(contact)
        val effectiveCollectionId = ensureCollectionId(collectionRepository, collectionId)
        collectionRepository.addContactToCollection(
            contactId = contactId,
            collectionId = effectiveCollectionId,
            sourceType = "manual"
        )
        return contactId
    }

    /**
     * [B5] 从解析结果创建联系人。
     *
     * 流程：插入联系人(name/bio/avatarUrl) → 下载头像落盘 → 添加平台条目 → 加入名片夹。
     * 头像下载失败不阻断（保留 avatarUrl 远端地址，有日志）。
     *
     * @param name 联系人姓名（不可为空白）
     * @param bio 个人简介（可空）
     * @param avatarUrl 头像 URL（可空）
     * @param platformKey 平台 fieldKey（可空，如 qq/wechat 等）
     * @param platformValue 平台账号值（可空）
     * @param collectionId 目标名片夹 ID（可空，自动选默认）
     * @param context Android Context，用于头像落盘
     * @return 新建联系人本地 ID
     */
    suspend fun createContactFromResolve(
        name: String,
        bio: String?,
        avatarUrl: String?,
        platformKey: String?,
        platformValue: String?,
        collectionId: Long?,
    ): Long {
        val now = nowMs()
        // 头像先落盘再写 avatarPath（与 ImportFromPlatformDialog / sync 同策略）
        var avatarPath: String? = null
        if (!avatarUrl.isNullOrBlank()) {
            try {
                avatarPath = downloadAndStoreAvatar(avatarUrl, "contact_avatar_${now}.webp")
                if (avatarPath == null) {
                    BadgerLog.w(TAG, "createContactFromResolve: 头像下载失败,保留 avatarUrl")
                }
            } catch (e: Exception) {
                BadgerLog.w(TAG, "createContactFromResolve: 头像下载异常", e)
            }
        }

        val contact = Contact(
            id = 0L,
            name = name,
            bio = bio?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            avatarPath = avatarPath,
            createTime = now,
            updateTime = now,
        )
        val contactId = contactRepository.insertContact(contact)

        // 添加平台条目
        if (!platformKey.isNullOrBlank() && !platformValue.isNullOrBlank()) {
            contactRepository.updateContactPlatform(
                contactId = contactId,
                fieldKey = platformKey,
                entry = PlatformEntry(
                    displayName = null,
                    jumpLink = "",
                    originalLink = null,
                    value = platformValue,
                )
            )
        }

        val effectiveCollectionId = ensureCollectionId(collectionRepository, collectionId)
        collectionRepository.addContactToCollection(
            contactId = contactId,
            collectionId = effectiveCollectionId,
            sourceType = "auto_resolve"
        )
        return contactId
    }
}
