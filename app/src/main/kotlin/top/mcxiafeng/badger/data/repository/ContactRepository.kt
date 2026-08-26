package top.mcxiafeng.badger.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.DuplicateCheckResult
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.data.QAuxvImportProgress
import top.mcxiafeng.badger.data.QAuxvImportSummary
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * 联系人数据仓库接口。
 *
 * [A3] 全部输出 V2 cache entity(`ContactCacheEntity` / `ContactPlatform` / `ContactWithFields`)
 * 与共享 `PlatformEntry` JSON shape。
 *
 * 管理联系人基本操作和社交平台操作。
 */
interface ContactRepository {

    // ========== 联系人基本操作 ==========

    fun getAllContacts(): Flow<List<ContactCacheEntity>>

    fun getLetterIndex(): Flow<List<LetterCount>>

    suspend fun getContactById(id: Long): ContactCacheEntity?

    fun getAllContactsWithFields(): Flow<List<ContactWithFields>>

    suspend fun getContactWithFieldsById(id: Long): ContactWithFields?

    suspend fun insertContact(contact: ContactCacheEntity): Long

    suspend fun updateContact(contact: ContactCacheEntity)

    /**
     * 仅更新个人介绍(bio)字段。保留其它字段不变,写入后自动 bumpContact
     * 触发 PagingSource/Flow 重发(详见 [[feedback_room_paging_invalidation]])。
     */
    suspend fun updateContactBio(contactId: Long, bio: String?)

    suspend fun deleteContact(contact: ContactCacheEntity)

    suspend fun deleteByIds(ids: List<Long>)

    /**
     * [V2-P6] 关键操作"双通道删除"骨架(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5)。
     *
     * 流程:
     * 1. 立刻 markDeleted(UI 隐藏),**但保留数据行**(恢复窗口可回滚)
     * 2. 入队 PendingUpload(opType=DELETE_CONTACT) + history(snapshotBefore)
     * 3. 直接 HTTP DELETE(不等 Worker,体感 0 延迟)
     * 4. 200 → hardDelete(物理删除) + markDone
     * 5. 失败 → recoverFromDirect(Worker 接力)
     * 6. 30s 后 RevertStuckOpWorker 检查:若仍未 DONE → 复活 isDeleted=false
     *
     * @param contactId 要删除的联系人本地 id
     * @return CommitResult.SentSuccess(已 200 + 物理删除) / CommitResult.SentFailed(直发失败,Worker 接力) / CommitResult.NotFound(联系人不存在)
     */
    suspend fun commitDelete(contactId: Long): CommitResult

    /**
     * [V2-P6] 关键操作"双通道合并"。
     *
     * 合并 server-side:服务端返回合并后的 target person + 新快照。
     * 客户端 cache:清掉 mergedIds(子表 platform / fieldValue / tag crossRef)+ 保留 target。
     *
     * 失败兜底:同 commitDelete(http 失败 → recoverFromDirect + Worker 接力;30s revert 由服务端反向
     * 决定 → 矛盾,因此合并走**强一致直发** + worker 兜底,但不做 30s 假象恢复 — 合并是单边决断,
     * 失败可撤销代价高,P7 历史页提供"恢复被合并联系人"按钮)。
     */
    suspend fun commitMerge(targetId: Long, mergedIds: List<Long>): CommitResult

    fun searchContacts(query: String): Flow<List<ContactCacheEntity>>

    /**
     * 触发 PagingSource/Flow 重发。
     */
    suspend fun bumpContact(contactId: Long)

    // ========== 联系人社交平台操作 ==========

    suspend fun updateContactPlatform(contactId: Long, fieldKey: String, entry: PlatformEntry)

    suspend fun removeContactPlatform(contactId: Long, fieldKey: String)

    /** 批量获取所有联系人的平台数据,按 contactId 分组 */
    suspend fun getAllContactPlatformsGrouped(): Map<Long, List<ContactPlatform>>

    /** 获取指定联系人已有的平台 key 集合 */
    suspend fun getContactPlatformKeys(contactId: Long): Set<String>

    /** 获取指定联系人的所有平台数据 */
    suspend fun getContactPlatforms(contactId: Long): List<ContactPlatform>

    // ========== 重复检测 ==========

    suspend fun checkDuplicate(
        newContactName: String,
        fieldValues: Map<String, String>,
        customFieldValues: Map<Long, String>
    ): DuplicateCheckResult

    // ========== QAuxv 导入 ==========

    suspend fun findExistingQQContacts(entries: List<QAuxvFriendEntry>): Map<Long, Long>

    suspend fun importQAuxvFriends(
        decisions: List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>,
        context: Context,
        onProgress: ((QAuxvImportProgress) -> Unit)? = null,
    ): QAuxvImportSummary
}