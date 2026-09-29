package top.mcxiafeng.badger.page.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import top.mcxiafeng.badger.data.repository.PlatformRepository
import top.mcxiafeng.badger.data.user.entity.Platform
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModel(
    personRepository: PersonRepository = PersonRepository(),
    platformRepository: PlatformRepository = PlatformRepository()
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
                BadgerLog.d(TAG, "开始获取联系人，优先尝试网络拉取...")
                val persons = try {
                    val remote = PersonApi.getPersons()
                    if (remote != null) {
                        BadgerLog.d(TAG, "网络拉取成功，获取到 ${remote.size} 个联系人，正在同步更新本地数据库缓存")
                        runCatching {
                            if (remote.isEmpty()) {
                                personRepository.deleteAll()
                            } else {
                                personRepository.upsertAll(remote)
                                personRepository.deleteNotIn(remote.map { it.uuid })
                            }
                        }.onFailure { e -> BadgerLog.w(TAG, "更新本地联系人数据库缓存失败", e) }
                        remote
                    } else {
                        BadgerLog.w(TAG, "网络拉取返回空或失败，降级读取本地数据库缓存")
                        personRepository.getAll()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    BadgerLog.w(TAG, "网络请求发生异常，降级读取本地数据库缓存", e)
                    personRepository.getAll()
                } finally {
                    _isRefreshing.value = false
                }

                val state: PersonUiState = PersonUiState.Success(persons)
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
