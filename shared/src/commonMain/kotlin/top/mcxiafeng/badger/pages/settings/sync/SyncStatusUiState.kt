package top.mcxiafeng.badger.pages.settings.sync

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot

/**
 * SyncStatusPage UI 状态（sealed interface，NowInAndroid 模式）。
 *
 * - `Loading`: 首帧 + 强制刷新期间
 * - `Success`: 快照 + 电池优化状态可用 —— UI 主显示态
 * - `Error`: 致命失败（读取同步状态异常），右下角"重试"按钮
 *
 * 瞬时反馈（Snackbar）走共享 [top.mcxiafeng.badger.pages.settings.components.SettingsUiMessage]，
 * 不再自建 SyncStatusMessage sealed 类（原 Error 分支从未构造，已删）。
 */
@Immutable
sealed interface SyncStatusUiState {
    data object Loading : SyncStatusUiState

    data class Success(
        val snapshot: SyncStatusSnapshot,
        /** Android 6.0+ 系统 battery_optimizations 是否已把 App 加入白名单。 */
        val batteryOptimized: Boolean,
    ) : SyncStatusUiState

    data class Error(val message: String) : SyncStatusUiState
}
