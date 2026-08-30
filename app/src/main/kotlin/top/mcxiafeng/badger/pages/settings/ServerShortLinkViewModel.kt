package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.ServerShortLink
import top.mcxiafeng.badger.network.ShortLinkConfig

/**
 * 自建短链管理页 VM。
 *
 * 登录后拉取配置 + 列表；支持创建 / 修改 / 删除。
 */
class ServerShortLinkViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _links = MutableStateFlow<List<ServerShortLink>>(emptyList())
    private val _config = MutableStateFlow<ShortLinkConfig?>(null)

    val uiState: StateFlow<ServerShortLinkUiState> = combine(
        _links, _config, _loading, _error, userAuthRepository.state,
    ) { links, config, loading, error, auth ->
        ServerShortLinkUiState(
            links = links, config = config, loading = loading, error = error,
            isLoggedIn = auth is AuthState.SignedIn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerShortLinkUiState(isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn),
    )

    // ── CRUD ────────────────────────────────────────────────────────

    fun refresh() {
        launchCrud { doRefresh() }
    }

    fun createLink(originalURL: String, code: String?) {
        if (originalURL.isBlank()) {
            _error.value = "请输入原始 URL"
            return
        }
        launchCrud("短码已被占用") {
            serverApiFactory.get().createServerShortLink(originalURL, code?.takeIf { it.isNotBlank() })
            Log.d(TAG, "createLink OK")
            doRefresh()
        }
    }

    fun updateLink(uuid: String, originalURL: String?, code: String?) {
        if (originalURL == null && code == null) return
        launchCrud("短码已被占用") {
            serverApiFactory.get().updateServerShortLink(uuid, originalURL, code)
            Log.d(TAG, "updateLink OK: uuid=${uuid.take(8)}")
            doRefresh()
        }
    }

    fun deleteLink(uuid: String) {
        launchCrud {
            serverApiFactory.get().deleteServerShortLink(uuid)
            Log.d(TAG, "deleteLink OK: uuid=${uuid.take(8)}")
            doRefresh()
        }
    }

    // ── 工具 ────────────────────────────────────────────────────────

    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    /** 纯挂起：拉取配置 + 列表，不包装 launchCrud，供 CRUD 方法在同一次 launchCrud 内调用。 */
    private suspend fun doRefresh() {
        val api = serverApiFactory.get()
        val config = api.getShortLinkConfig()
        val links = if (config.serverEnabled) api.listServerShortLinks() else emptyList()
        _config.value = config
        _links.value = links
    }

    /**
     * CRUD 操作统一骨架：loading→runCatching→onSuccess→onFailure(映射错误)。
     *
     * @param conflictMsg 409 冲突时的用户提示；null 则用通用 ApiException 映射。
     * @param block 实际 API 调用，成功后通常调 [refresh] 刷新列表。
     */
    private fun launchCrud(
        conflictMsg: String? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = runCatching {
                withContext(dispatcher) { block() }
            }
            result.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "CRUD failed: ${e.javaClass.simpleName}: ${e.message}")
                _error.value = when {
                    e is ApiException && e.status == 409 && conflictMsg != null -> conflictMsg
                    e is ApiException -> e.bodyText ?: "操作失败 (${e.status})"
                    else -> e.message ?: "操作失败"
                }
                _loading.value = false
            }
            // onSuccess 分支：block 内部已处理状态更新；若 block 调了 refresh()，refresh 会接管 _loading
            if (result.isSuccess) _loading.value = false
        }
    }

    companion object {
        private const val TAG = "ServerShortLinkVM"
    }
}

@Immutable
data class ServerShortLinkUiState(
    val links: List<ServerShortLink> = emptyList(),
    val config: ShortLinkConfig? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
)
