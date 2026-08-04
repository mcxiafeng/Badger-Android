package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.BackupSummary

/**
 * [§16] 云端备份独立页的 VM。
 *
 * UI 状态走 [uiState]: `StateFlow<CloudBackupUiState>`,`StateFlow` 标准模式,
 * 旋转屏 / 切换主题不丢。
 *
 * 删除走 [delete]:按 spec §14 一致幂等(404 = 成功),返回 `Result.failure` 仅当
 * 非 2xx / 非 4xx 之外的错误。
 *
 * [§14.2] 移除 `@HiltViewModel` 与 `@Inject` —— Koin 字段注入。
 *
 * [§16 测试] `dispatcher` 参数暴露给测试,默认 [Dispatchers.IO]。测试用 UnconfinedTestDispatcher
 * 替代,让 viewModelScope.launch 块同步推进,断言时拿到终态。
 */
class CloudBackupViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _uiState = MutableStateFlow(CloudBackupUiState())
    val uiState: StateFlow<CloudBackupUiState> = _uiState.asStateFlow()

    /**
     * 拉取服务端 backup 列表。失败时写入 [CloudBackupUiState.error],不抛。
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val result: Result<List<BackupSummary>> = runCatching {
                withContext(dispatcher) { serverApiFactory.get().listBackups() }
            }
            result
                .onSuccess { items ->
                    Log.d(TAG, "refresh: items.size=${items.size}")
                    val now = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        items = items,
                        error = null,
                        lastSuccessAt = now,
                    )
                }
                .onFailure { e ->
                    Log.w(TAG, "refresh: failed ${e.javaClass.simpleName}: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "加载失败")
                }
        }
    }

    /**
     * 删除服务端单条 backup。成功 → 从 [uiState].items 移除该 id,
     * `deletingId` 复位;失败 → 保留列表 + 写错误消息。
     */
    fun delete(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingId = id, error = null)
            val result: Result<Boolean> = runCatching {
                withContext(dispatcher) { serverApiFactory.get().deleteBackup(id) }
            }
            result
                .onSuccess {
                    Log.d(TAG, "delete: OK id=$id")
                    _uiState.value = _uiState.value.copy(
                        deletingId = null,
                        items = _uiState.value.items.filterNot { it.id == id },
                    )
                }
                .onFailure { e ->
                    Log.w(TAG, "delete: failed id=$id ${e.javaClass.simpleName}: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(deletingId = null, error = e.message ?: "删除失败")
                }
        }
    }

    /**
     * 清除 transient error,例如 snackbar 显示完之后用户 dismiss。
     */
    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    companion object {
        private const val TAG = "CloudBackup"
    }
}

/**
 * [§16] CloudBackupPage 完整 UI 状态。
 *
 * - [items]:服务端列表
 * - [loading]:首屏 / refresh 中
 * - [deletingId]:正在删除的 backup id,按钮据此显示 loading 锁
 * - [error]:transient 错误,snackbar/dialog 用
 */
data class CloudBackupUiState(
    val items: List<BackupSummary> = emptyList(),
    val loading: Boolean = false,
    val deletingId: String? = null,
    val error: String? = null,
    // [V2-E2E #5] 上次成功刷新时间,UTC 毫秒。UI 列表底部显示 "上次刷新时间: HH:mm:ss"。
    val lastSuccessAt: Long? = null,
)