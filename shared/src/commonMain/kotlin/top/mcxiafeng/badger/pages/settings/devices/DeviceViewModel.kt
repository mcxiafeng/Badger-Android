package top.mcxiafeng.badger.pages.settings.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.UserDevice
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

/**
 * [B4] 设备管理页 VM。
 *
 * 设备列表来自 [DeviceRepository]，按需 [refresh]（无轮询）；
 * rename / delete 走乐观更新（仓库侧），失败写 [DeviceUiState.error]。
 * [currentDeviceId] 用于 UI 高亮当前设备 + 禁止自删。
 */
class DeviceViewModel(
    private val dispatcher: CoroutineDispatcher = BadgerDispatchers.io,
) : ViewModel() {

    private val repository: DeviceRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val deviceIdProvider: DeviceIdProvider = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /** 当前设备的 deviceId，用于 UI 高亮 + 禁止自删。 */
    val currentDeviceId: String = deviceIdProvider.deviceId()

    val uiState: StateFlow<DeviceUiState> = combine(
        repository.devices,
        userAuthRepository.state,
        _loading,
        _error,
    ) { devices, auth, loading, error ->
        DeviceUiState(
            devices = devices,
            loading = loading,
            error = error,
            isLoggedIn = auth is AuthState.SignedIn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceUiState(
            isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn,
        ),
    )

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = runCatching {
                withContext(dispatcher) { repository.refresh() }
            }
            result.onFailure { e ->
                BadgerLog.w(TAG, "refresh failed: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "加载失败"
            }
            _loading.value = false
        }
    }

    fun renameDevice(uuid: String, newName: String) {
        if (uuid.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) { repository.renameDevice(uuid, newName) }
            }.onFailure { e ->
                BadgerLog.w(TAG, "renameDevice failed uuid=${uuid.take(8)}: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "重命名失败"
            }
        }
    }

    fun deleteDevice(uuid: String) {
        if (uuid.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(dispatcher) { repository.deleteDevice(uuid) }
            }.onFailure { e ->
                BadgerLog.w(TAG, "deleteDevice failed uuid=${uuid.take(8)}: ${e::class.simpleName}: ${e.message}")
                _error.value = e.message ?: "注销失败"
            }
        }
    }

    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    companion object {
        private const val TAG = "DeviceVM"
    }
}

data class DeviceUiState(
    val devices: List<UserDevice> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)
