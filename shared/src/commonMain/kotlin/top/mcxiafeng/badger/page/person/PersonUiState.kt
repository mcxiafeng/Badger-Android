package top.mcxiafeng.badger.page.person

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.user.entity.Person

@Immutable
sealed interface PersonUiState {
    data object Loading : PersonUiState

    data class Success(
        val persons: List<Person>
    ) : PersonUiState

    data class Error(val message: String) : PersonUiState
}
