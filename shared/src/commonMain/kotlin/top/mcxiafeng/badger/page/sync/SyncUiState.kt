package top.mcxiafeng.badger.page.sync

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot

@Immutable
sealed interface SyncUiState {
    data object Loading : SyncUiState

    data class Success(
        val snapshot: SyncStatusSnapshot,
        val syncing: Boolean = false,
        val message: String? = null,
    ) : SyncUiState

    data class Error(val message: String) : SyncUiState
}
