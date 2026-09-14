package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserDevice

class DeviceRepository(
    private val serverApi: ServerApi,
    private val userAuthRepository: UserAuthRepository,
    private val ioDispatcher: CoroutineDispatcher = BadgerDispatchers.io,
    externalScope: CoroutineScope? = null,
) {
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _devices = MutableStateFlow<List<UserDevice>>(emptyList())
    
    val devices: StateFlow<List<UserDevice>> = _devices.asStateFlow()

    init {
        scope.launch {
            userAuthRepository.state.collect { state ->
                when (state) {
                    AuthState.SignedIn -> Unit 
                    AuthState.SignedOut, is AuthState.Error -> {
                        _devices.value = emptyList()
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    

    suspend fun refresh() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            BadgerLog.d(TAG, "refresh skipped: no token")
            return
        }
        val list = withContext(ioDispatcher) { serverApi.listDevices() }
        _devices.value = list
    }

    

    suspend fun renameDevice(uuid: String, newName: String) {
        withContext(ioDispatcher) { serverApi.renameDevice(uuid, newName) }
        _devices.update { rows ->
            rows.map { if (it.uuid == uuid) it.copy(deviceName = newName) else it }
        }
    }

    

    suspend fun deleteDevice(uuid: String): Boolean {
        val ok = withContext(ioDispatcher) { serverApi.deleteDevice(uuid) }
        if (ok) _devices.update { rows -> rows.filterNot { it.uuid == uuid } }
        return ok
    }

    companion object {
        private const val TAG = "DeviceRepo"
    }
}
