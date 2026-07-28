package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class SetupGuideViewModel : ViewModel() {
    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    init {
            }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * 包裹一个异步同步任务：进入时设置 isSyncing=true，结束时（含异常）置回 false。
     * 在引导页"填入平台 -> 拉取信息"期间阻止用户离开当前页。
     */
    fun runSync(block: suspend () -> Unit) {
        if (_isSyncing.value) {
            Log.w(TAG, "[SYNC] runSync called while already syncing, ignoring reentrant call")
            return
        }
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "[SYNC] sync block failed", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private companion object {
        const val TAG = "SetupGuideViewModel"
    }
}
