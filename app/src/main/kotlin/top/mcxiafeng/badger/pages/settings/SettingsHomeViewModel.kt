package top.mcxiafeng.badger.pages.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import top.mcxiafeng.badger.data.AuthPrefs
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import javax.inject.Inject

private const val TAG = "SettingsHome"

data class SettingsHomeState(
    val username: String?,
    val isLoggedIn: Boolean,
    val serverUrl: String,
    /** [V2-P9] 设置主页"同步状态"项 summary: "N 个待同步 · M 个冲突" / "同步正常"。 */
    val pendingHint: String,
)

/**
 * Lightweight VM backing the top-level [SettingsPage]. Exposes the minimum
 * snapshot the home page needs (username + login state + server URL + 同步状态摘要) so
 * Composable code never talks to Repository / SharedPreferences directly.
 *
 * UI 刷新机制:serverUrl 字段不再读 prefs,而是订阅 [ServerUrlHolder] 持有的
 * StateFlow —— 这样 `AccountSettingsViewModel.updateServerUrl` 改了 URL,
 * 所有订阅者(包括本 VM 与 [AccountProfilePage])立刻收到,无须退出页面再进。
 *
 * pendingHint 由 [SyncStatusRepository.snapshot] 在 combine 内异步读,失败兜底"同步状态"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userAuthRepository: UserAuthRepository,
    private val serverUrlHolder: ServerUrlHolder,
    private val syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    init {
        Log.d(TAG, "SettingsHomeViewModel initialized")
    }

    val state: StateFlow<SettingsHomeState> = combine(
        userAuthRepository.state,
        serverUrlHolder.url,
        pendingHintFlow(),
    ) { auth, url, pendingHint ->
        SettingsHomeState(
            username = AuthPrefs.readUsername(context),
            isLoggedIn = auth is AuthState.SignedIn,
            serverUrl = url,
            pendingHint = pendingHint,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsHomeState(
            username = AuthPrefs.readUsername(context),
            isLoggedIn = userAuthRepository.state.value is AuthState.SignedIn,
            serverUrl = serverUrlHolder.url.value,
            pendingHint = DEFAULT_PENDING_HINT,
        ),
    )

    /**
     * 同步状态摘要 Flow。失败兜底"同步状态"字符串,避免 SettingsHomeState 阻塞。
     *
     * [修复防御]: readUsername 每次 combine 重读 prefs,可能比 snapshot() 慢 ——
     * 让 snapshotFlow 独立走,失败 fallback 而不是阻塞整个 state。
     */
    private fun pendingHintFlow() = flow {
        val hint = runCatching {
            val s = syncStatusRepository.snapshot()
            val parts = mutableListOf<String>()
            val attention = s.failedCount + s.conflictCount + s.failedPermanentCount
            if (attention > 0) {
                parts.add("有 $attention 项需要关注")
            }
            if (s.pendingCount > 0) {
                parts.add("${s.pendingCount} 个待同步")
            }
            when {
                parts.isNotEmpty() -> parts.joinToString(" · ")
                s.totalCount == 0 -> DEFAULT_PENDING_HINT
                else -> "同步正常"
            }
        }.getOrElse {
            Log.w(TAG, "pendingHintFlow: 读 snapshot 失败,fallback", it)
            DEFAULT_PENDING_HINT
        }
        emit(hint)
    }

    companion object {
        private const val DEFAULT_PENDING_HINT = "同步状态"
    }
}