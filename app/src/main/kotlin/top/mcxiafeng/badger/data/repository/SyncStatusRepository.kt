package top.mcxiafeng.badger.data.repository

/**
 * 同步状态仓库接口。
 *
 * [Phase 4 Task #21] 退役 `pending_uploads` 队列语义：
 * - [snapshot] 改为读 `sync_cursor` + `isLocalOnly` 计数（不再数 pending 状态）；
 * - [retryAll] 触发一次服务端增量同步（`SyncRepository.pullOnceIfIdle`）；
 * - [retryOne] / [purgeFinished] 删除（队列已退役，无消费语义）。
 */
interface SyncStatusRepository {

    /**
     * 取同步状态快照 — 用于 Settings 同步状态页头部卡片展示。
     *
     * 读 `sync_cursor`（lastVersion / updatedAt）+ `contacts_cache` 中 `isLocalOnly=1` 计数。
     */
    suspend fun snapshot(): SyncStatusSnapshot

    /**
     * 触发一次服务端增量同步。返回本次同步成功重放的 change 数。
     */
    suspend fun retryAll(): Int

    companion object {
        /** 默认清理阈值 30 天 —— 历史遗留，不再使用。 */
        const val DEFAULT_PURGE_DAYS = 30
    }
}

/**
 * 同步状态快照。
 *
 * [Phase 4 Task #21] 退役队列计数，改为同步游标 + 未同步联系人计数。
 */
data class SyncStatusSnapshot(
    /** 最后一次成功同步的游标版本号（`sync_cursor.lastVersion`）。 */
    val lastSyncVersion: Long = 0,
    /** 最后一次成功同步的时间戳（`sync_cursor.updatedAt`）。 */
    val lastSyncedAt: Long = 0,
    /** `contacts_cache` 中 `isLocalOnly=1` 的联系人数（尚未推送到服务端）。 */
    val unsyncedCount: Int = 0,
) {
    /**
     * 是否"有需要关注的项"（有未同步的本地联系人）。
     */
    val hasAttention: Boolean
        get() = unsyncedCount > 0
}
