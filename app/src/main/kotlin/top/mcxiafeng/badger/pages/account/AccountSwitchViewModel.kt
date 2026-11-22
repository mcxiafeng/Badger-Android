package top.mcxiafeng.badger.pages.account

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.auth.AuthApi
import top.mcxiafeng.badger.network.core.ServerAddressStore
import javax.inject.Inject

@Immutable
data class AccountSwitchUiState(
    val current: AccountScope,
    val accounts: List<AccountScope>,
    val selected: AccountScope? = null,
    val password: String = "",
    val passwordVisible: Boolean = false,
    val showLocalConfirmation: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = selected != null && password.isNotEmpty() && !busy
    override fun toString(): String = "AccountSwitchUiState[redacted]"
}

@HiltViewModel
class AccountSwitchViewModel @Inject constructor(
    private val accounts: AccountStore,
    private val auth: AuthApi,
    private val addresses: ServerAddressStore,
) : ViewModel() {
    private val sourceScope = accounts.current()
    private val mutableState = MutableStateFlow(AccountSwitchUiState(sourceScope, accounts.registeredScopes()))
    val state: StateFlow<AccountSwitchUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var previousAddress: String? = null
    private var attemptedAddress: String? = null

    init { Log.d(TAG, "账号切换页面已初始化") }

    fun select(scopeKey: String) {
        if (state.value.busy || accounts.current().key != sourceScope.key) return
        val target = state.value.accounts.firstOrNull { it.key == scopeKey } ?: return
        if (target.key == sourceScope.key) {
            Log.d(TAG, "忽略当前账号的重复选择")
            return
        }
        Log.d(TAG, "选择切换目标；本地=${target.isLocal}")
        mutableState.update { it.copy(selected = target.takeUnless { it.isLocal }, password = "",
            passwordVisible = false, showLocalConfirmation = target.isLocal, error = null) }
    }

    fun onPassword(value: String) {
        if (!state.value.busy) mutableState.update { it.copy(password = value, error = null) }
    }

    fun togglePasswordVisibility() {
        mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
        Log.d(TAG, "已切换密码可见状态")
    }

    fun dismissLocalConfirmation() {
        mutableState.update { it.copy(showLocalConfirmation = false) }
        Log.d(TAG, "已取消返回本地用户确认")
    }

    fun showAccounts() {
        cancelOperation()
        mutableState.update { it.copy(selected = null, password = "", passwordVisible = false,
            showLocalConfirmation = false, error = null) }
        Log.d(TAG, "返回账号列表，当前空间保留")
    }

    fun signIn() {
        val form = state.value
        val target = form.selected ?: return
        if (!form.canSubmit || accounts.current().key != sourceScope.key) return
        val requestGeneration = ++generation
        mutableState.update { it.copy(busy = true, error = null) }
        Log.d(TAG, "开始历史账号重新验证")
        operation = viewModelScope.launch {
            try {
                previousAddress = addresses.activeUrl.value
                attemptedAddress = target.baseUrl.trimEnd('/')
                addresses.setActive(checkNotNull(attemptedAddress))
                // Existing AuthViewModel observes this address and invalidates old login work.
                // Let queued Main observers finish before AuthApi creates this attempt's generation.
                yield()
                currentCoroutineContext().ensureActive()
                if (requestGeneration != generation || accounts.current().key != sourceScope.key ||
                    addresses.activeUrl.value != attemptedAddress) throw CancellationException("切换任务已失效")
                val result = auth.login(target.username, form.password, target.baseUrl, expectedScope = target)
                currentCoroutineContext().ensureActive()
                if (requestGeneration == generation) completeLogin(result)
            } catch (e: CancellationException) {
                Log.d(TAG, "历史账号验证已取消，当前会话保留")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "历史账号验证失败 (${e.javaClass.simpleName})")
                if (requestGeneration == generation) mutableState.update { it.copy(error = "切换未完成，请重试") }
            } finally {
                if (requestGeneration == generation) {
                    restoreAddress()
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun completeLogin(result: HttpResult) {
        when (result) {
            is HttpResult.Success -> {
                previousAddress = null
                attemptedAddress = null
                addresses.markUrlVerified()
                mutableState.update { it.copy(password = "", passwordVisible = false) }
                Log.d(TAG, "历史账号验证成功，等待空间导航重建")
            }
            is HttpResult.Failure -> {
                mutableState.update { it.copy(error = result.switchMessage()) }
                Log.d(TAG, "历史账号验证未通过，当前会话保留")
            }
        }
    }

    fun confirmLocal() {
        if (!state.value.showLocalConfirmation || state.value.busy) return
        mutableState.update { it.copy(showLocalConfirmation = false) }
        if (accounts.current().key != sourceScope.key || sourceScope.isLocal) return
        val requestGeneration = ++generation
        mutableState.update { it.copy(busy = true, error = null) }
        Log.d(TAG, "已确认返回本地用户，账号资料保留")
        operation = viewModelScope.launch {
            try {
                auth.logout(expectedScopeKey = sourceScope.key)
            } catch (e: CancellationException) {
                Log.d(TAG, "返回本地用户任务已取消")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "返回本地用户失败 (${e.javaClass.simpleName})")
                if (requestGeneration == generation) mutableState.update { it.copy(error = "切换未完成，请重试") }
            } finally {
                if (requestGeneration == generation) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun onExit() {
        cancelOperation()
        mutableState.update { it.copy(password = "", passwordVisible = false, showLocalConfirmation = false) }
        Log.d(TAG, "离开账号切换页面，已清除密码输入")
    }

    private fun cancelOperation() {
        generation++
        if (operation?.isActive == true) auth.cancelPendingLogin()
        operation?.cancel()
        operation = null
        restoreAddress()
        mutableState.update { it.copy(busy = false) }
        Log.d(TAG, "已取消页面在途切换任务")
    }

    private fun restoreAddress() {
        val previous = previousAddress
        if (previous != null && accounts.current().key == sourceScope.key &&
            addresses.activeUrl.value == attemptedAddress) {
            addresses.setActive(previous)
            Log.d(TAG, "已恢复切换前连接地址")
        }
        previousAddress = null
        attemptedAddress = null
    }

    override fun onCleared() {
        onExit()
        super.onCleared()
    }

    private fun HttpResult.Failure.switchMessage(): String = when (errorType) {
        HttpResult.ErrorType.NETWORK -> "网络连接失败，请检查网络后重试"
        HttpResult.ErrorType.TIMEOUT -> "连接超时，请重试"
        HttpResult.ErrorType.AUTH -> "密码无效或账号不可用，请重试"
        HttpResult.ErrorType.RATE_LIMIT -> "操作过于频繁，请稍后重试"
        HttpResult.ErrorType.SERVER -> "服务器暂不可用，请稍后重试"
        else -> message ?: "账号验证未完成，请重试"
    }

    companion object { private const val TAG = "AccountSwitchViewModelTester" }
}
