package top.mcxiafeng.badger.pages.settings.sync

import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot

/**
 * [V2-P9] SyncStatusPage UI 状态(sealed interface,与 NowInAndroid 模式对齐)。
 *
 * - `Loading`: 首帧 + 强制刷新期间
 * - `Success`: 快照 + 电池优化状态可用 —— UI 主显示态
 * - `Error`: 致命失败(读取同步状态异常),右下角"重试"按钮
 */
sealed interface SyncStatusUiState {
    data object Loading : SyncStatusUiState

    data class Success(
        val snapshot: SyncStatusSnapshot,
        /** Android 6.0+ 系统 battery_optimizations 是否已把 App 加入白名单。 */
        val batteryOptimized: Boolean,
    ) : SyncStatusUiState

    data class Error(val message: String) : SyncStatusUiState
}

/**
 * SyncStatusPage 瞬时 UI 反馈消息(走 Channel 上抛 Composable 转 Snackbar)。
 *
 * 持久状态走 `uiState`,瞬时反馈走 Channel,符合 NowInAndroid 模式。
 */
sealed interface SyncStatusMessage {
    val text: String

    data class Info(override val text: String) : SyncStatusMessage
    data class Error(override val text: String) : SyncStatusMessage
}
