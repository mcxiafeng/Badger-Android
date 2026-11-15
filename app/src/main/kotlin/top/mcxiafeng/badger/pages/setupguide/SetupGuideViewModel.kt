package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class SetupGuideViewModel @Inject constructor(
    val userProfileRepository: UserProfileRepository,
    val contactRepository: ContactRepository
) : ViewModel() {
    init {
        Log.d("Tester", "SetupGuideViewModel initialized")
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
}

/**
 * 读取"我的名片"并组合成 UI 用的 UserProfile：
 * 身份字段（name/bio/avatarPath/平台）来自 contacts(-1) + contact_platforms，
 * cardImagePath/defaultPlatform 来自 user_profile 行。
 * SetupGuide 各步骤页读写"我的名片"统一走此入口。
 */
internal suspend fun loadMyProfile(
    userProfileRepository: UserProfileRepository,
    contactRepository: ContactRepository
): UserProfile? {
    val row = userProfileRepository.getUserProfileOnce()
    val me = contactRepository.getContactById(MY_CONTACT_ID)
    val platforms = contactRepository.getContactPlatforms(MY_CONTACT_ID)
        .filter { it.jumpLink.isNotBlank() || !it.value.isNullOrBlank() }
        .associate {
            it.platformKey to PlatformEntry(
                displayName = it.displayName,
                jumpLink = it.jumpLink,
                originalLink = it.originalLink,
                value = it.value,
                avatarUrl = it.avatarUrl
            )
        }
    return UserProfile(
        name = me?.name ?: row?.name ?: "",
        avatarPath = me?.avatarPath,
        cardImagePath = row?.cardImagePath,
        bio = me?.bio,
        platforms = platforms,
        defaultPlatform = row?.defaultPlatform,
        updateTime = me?.updateTime ?: row?.updateTime ?: 0L
    )
}
