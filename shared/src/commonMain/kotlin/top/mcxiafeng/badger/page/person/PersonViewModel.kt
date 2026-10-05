package top.mcxiafeng.badger.page.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import top.mcxiafeng.badger.data.repository.PersonRepository
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncEngineHolder
import top.mcxiafeng.badger.utils.BadgerLog

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModel(
    private val personRepository: PersonRepository = PersonRepository(),
    private val syncEngine: SyncEngine = SyncEngineHolder.get(),
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0L)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<PersonUiState> = refreshTrigger
        .flatMapLatest {
            flow {
                _isRefreshing.value = true
                // 只拉取/和解 PERSON——联系人页刷新不碰其他实体
                BadgerLog.d(TAG, "触发同步：${syncEngine.syncNow(EntityKind.PERSON)}")
                _isRefreshing.value = false
                val state: PersonUiState = PersonUiState.Success(personRepository.getAll())
                emit(state)
            }
        }
        .combine(_searchQuery) { state, query ->
            if ((state is PersonUiState.Success) && query.isNotBlank()) {
                val filtered = state.persons.filter { person ->
                    (person.name?.contains(query, ignoreCase = true) == true) ||
                            person.uuid.toString().contains(query, ignoreCase = true)
                }
                PersonUiState.Success(filtered)
            } else {
                state
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            _isRefreshing.value = false
            BadgerLog.e(TAG, "加载联系人数据失败", e)
            emit(PersonUiState.Error(e.message ?: "加载联系人数据失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PersonUiState.Loading,
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    companion object {
        private const val TAG = "PersonViewModelTester"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
