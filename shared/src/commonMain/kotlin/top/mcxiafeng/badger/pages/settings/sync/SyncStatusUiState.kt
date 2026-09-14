package top.mcxiafeng.badger.pages.settings.sync

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot

@Immutable
sealed interface SyncStatusUiState {
    data object Loading : SyncStatusUiState

    data class Success(
        val snapshot: SyncStatusSnapshot,
        
        val batteryOptimized: Boolean,
    ) : SyncStatusUiState

    data class Error(val message: String) : SyncStatusUiState
}
