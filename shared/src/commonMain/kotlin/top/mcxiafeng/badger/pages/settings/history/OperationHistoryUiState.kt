package top.mcxiafeng.badger.pages.settings.history

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact

@Immutable
sealed interface OperationHistoryUiState {
    data object Loading : OperationHistoryUiState

    data class Success(
        val records: List<OperationHistoryWithContact>,
        val filter: HistoryFilter,
    ) : OperationHistoryUiState

    data class Empty(val filter: HistoryFilter) : OperationHistoryUiState

    data class Error(val message: String) : OperationHistoryUiState
}
