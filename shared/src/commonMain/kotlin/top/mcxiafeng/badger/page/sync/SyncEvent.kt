package top.mcxiafeng.badger.page.sync

sealed interface SyncEvent {
    /** 重新读取本地快照（不碰网络）。 */
    data object Refresh : SyncEvent

    /** 立即同步：重放 pending（推送）+ 按游标增量拉取。 */
    data object SyncNow : SyncEvent
}
