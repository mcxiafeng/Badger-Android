package top.mcxiafeng.badger.data.repository

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

/**
 * [B3] 设备管理仓库：拉取 + 重命名 + 注销。
 *
 * 与 [NotificationRepository] 不同，设备列表**无需轮询**——用户主动进设备页时才拉一次，
 * 因此只有 `StateFlow` 快照，没有定时 Job。
 *
 * 登出清空列表（避免设备信息在 SignedOut 后残留 UI）。
 */
class DeviceRepository(
    private val serverApi: ServerApi,
    private val userAuthRepository: UserAuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
) {
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _devices = MutableStateFlow<List<UserDevice>>(emptyList())
    /** 当前用户全部已登录设备快照。UI collectAsState 即可。 */
    val devices: StateFlow<List<UserDevice>> = _devices.asStateFlow()

    init {
        scope.launch {
            userAuthRepository.state.collect { state ->
                when (state) {
                    AuthState.SignedIn -> Unit // 不自动拉，等 UI 主动 refresh
                    AuthState.SignedOut, is AuthState.Error -> {
                        _devices.value = emptyList()
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    /**
     * 拉全量设备列表（B4 设备页进入时调用）。
     *
     * 失败抛给调用方，不静默清空已有列表（与 [NotificationRepository.refreshNotifications] 同策略）。
     */
    suspend fun refresh() {
        if (userAuthRepository.currentToken().isNullOrBlank()) {
            BadgerLog.d(TAG, "refresh skipped: no token")
            return
        }
        val list = withContext(ioDispatcher) { serverApi.listDevices() }
        _devices.value = list
    }

    /**
     * 重命名设备。乐观更新 UI，失败则下次 refresh 矫正。
     *
     * @throws top.mcxiafeng.badger.network.ApiException 非 2xx 时抛给调用方（有 snackbar）。
     */
    suspend fun renameDevice(uuid: String, newName: String) {
        withContext(ioDispatcher) { serverApi.renameDevice(uuid, newName) }
        _devices.update { rows ->
            rows.map { if (it.uuid == uuid) it.copy(deviceName = newName) else it }
        }
    }

    /**
     * 注销设备（踢下线）。乐观移除行，失败则下次 refresh 矫正。
     *
     * @return true 表示成功（含 404 幂等）。
     * @throws top.mcxiafeng.badger.network.ApiException 非 2xx/404 时抛给调用方。
     */
    suspend fun deleteDevice(uuid: String): Boolean {
        val ok = withContext(ioDispatcher) { serverApi.deleteDevice(uuid) }
        if (ok) _devices.update { rows -> rows.filterNot { it.uuid == uuid } }
        return ok
    }

    companion object {
        private const val TAG = "DeviceRepo"
    }
}
