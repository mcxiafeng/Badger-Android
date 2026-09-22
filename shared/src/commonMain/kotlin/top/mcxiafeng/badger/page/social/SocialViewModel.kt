package top.mcxiafeng.badger.page.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import top.mcxiafeng.badger.data.repository.ProfileRepository
import top.mcxiafeng.badger.data.repository.SystemRepository
import top.mcxiafeng.badger.data.repository.UserRepository
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * 名片主页 ViewModel：拉「同步载体 → User → Profile」三跳只读快照，
 * 并持有名片卡上高亮的联系平台下标（跨 Tab 切换存活）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModel(
    private val systemRepository: SystemRepository = SystemRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0L)

    /** 名片卡上高亮的联系平台下标；平台集合为空或下标越界由页面收敛。 */
    private val _selectedPlatformIndex = MutableStateFlow(0)
    val selectedPlatformIndex: StateFlow<Int> = _selectedPlatformIndex.asStateFlow()

    val uiState: StateFlow<SocialUiState> = refreshTrigger
        .flatMapLatest {
            flow {
                BadgerLog.d(TAG, "开始加载名片主页数据")
                val userInfo = systemRepository.getActiveUserInfo()
                if (userInfo == null) {
                    BadgerLog.w(TAG, "无同步载体，名片主页不可用")
                    emit(SocialUiState.Error("未登录，名片不可用"))
                    return@flow
                }
                val user = userRepository.get(userInfo.userUuid)
                if (user == null) {
                    BadgerLog.w(TAG, "载体指向的用户数据缺失：${userInfo.userUuid}")
                    emit(SocialUiState.Error("用户数据缺失，请重新同步"))
                    return@flow
                }
                val profile = profileRepository.get(user.profileUuid)
                if (profile == null) {
                    BadgerLog.w(TAG, "用户名片数据缺失：${user.uuid}")
                    emit(SocialUiState.Error("名片数据缺失，请重新同步"))
                    return@flow
                }
                BadgerLog.d(
                    TAG,
                    "名片主页数据就绪：user=${user.uuid} 平台=${profile.contactMap.size} 个",
                )
                emit(SocialUiState.Success(userInfo, user, profile))
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            BadgerLog.e(TAG, "加载名片主页数据失败", e)
            emit(SocialUiState.Error(e.message ?: "加载失败"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SocialUiState.Loading,
        )

    fun selectPlatform(index: Int) {
        _selectedPlatformIndex.update { index }
    }

    fun refresh() = refreshTrigger.update { it + 1 }

    companion object {
        private const val TAG = "SocialViewModelTester"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
