package top.mcxiafeng.badger.page.collection

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.user.entity.Collection

@Immutable
sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    data class Success(
        val collections: List<Collection>
    ) : CollectionUiState

    data class Error(val message: String) : CollectionUiState
}
