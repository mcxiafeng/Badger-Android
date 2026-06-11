package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.domain.LinkUpdateResult
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.pages.social.NfcHelper
import top.mcxiafeng.badger.network.ShortLinkService

/**
 * NFC 标签写入状态
 */
enum class NfcWriteState {
    IDLE, PREPARING, READY, SUCCESS, ERROR
}

/**
 * 短链接更新状态
 */
enum class LinkUpdateState {
    IDLE, UPDATING, SUCCESS, ERROR
}

/**
 * 扩列页面的 UI 状态
 */
@Immutable
data class SocialUiState(
    val profile: UserProfile? = null,
    val platforms: List<Pair<String, PlatformEntry>> = emptyList(),
    val selectedPlatformIndex: Int = 0,
    val showEditProfileDialog: Boolean = false,
    val showAddPlatformDialog: Boolean = false,
    val nfcSupported: Boolean = false,
    val showNfcWriteDialog: Boolean = false,
    val nfcWriteState: NfcWriteState = NfcWriteState.IDLE,
    val nfcWriteMessage: String? = null,
    val shortUrl: String? = null,
    val linkUpdateState: LinkUpdateState = LinkUpdateState.IDLE,
)

/**
 * 扩列页面的 ViewModel
 *
 * 管理用户名片数据、NFC 标签写入、短链接更新。
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val repository: UserProfileRepository,
    @ApplicationContext private val applicationContext: android.content.Context,
    private val selectPlatformUseCase: SelectPlatformUseCase,
    private val prepareNfcWriteUseCase: PrepareNfcWriteUseCase
) : ViewModel() {

    private val TAG = "SocialViewModel"

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    // NFC 写入防抖
    private var lastNfcWriteTime = 0L
    private val NFC_WRITE_DEBOUNCE_MS = 3000L

    init {
        loadProfile()
        observeNfcWriteResult()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.getUserProfile().collect { profile ->
                val platforms = if (profile != null) buildPlatformList(profile) else emptyList()

                val defaultIndex = if (profile?.defaultPlatform != null) {
                    platforms.indexOfFirst { it.first == profile.defaultPlatform }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }

                val oldDefaultPlatform = _uiState.value.profile?.defaultPlatform
                val newDefaultPlatform = profile?.defaultPlatform
                val defaultPlatformChanged = oldDefaultPlatform != newDefaultPlatform

                val currentIndex = _uiState.value.selectedPlatformIndex
                val finalIndex = if (defaultPlatformChanged) {
                    defaultIndex
                } else if (currentIndex >= 0 && currentIndex < platforms.size) {
                    currentIndex
                } else {
                    defaultIndex
                }

                _uiState.value = _uiState.value.copy(
                    profile = profile,
                    platforms = platforms,
                    selectedPlatformIndex = finalIndex.coerceIn(0, (platforms.size - 1).coerceAtLeast(0))
                )
            }
        }
    }

    private fun observeNfcWriteResult() {
        viewModelScope.launch {
            NfcHelper.writeResult.collect { result ->
                if (result != null) {
                    _uiState.value = _uiState.value.copy(
                        nfcWriteState = if (result.success) NfcWriteState.SUCCESS else NfcWriteState.ERROR,
                        nfcWriteMessage = result.message
                    )
                }
            }
        }
    }

    private fun buildPlatformList(profile: UserProfile): List<Pair<String, PlatformEntry>> {
        return profile.platforms
            ?.filter { it.value.jumpLink.isNotBlank() || !it.value.value.isNullOrBlank() }
            ?.map { (key, entry) -> key to entry }
            ?.toList() ?: emptyList()
    }

    /** 切换选中的平台，同时更新短链接目标地址 */
    fun selectPlatform(index: Int) {
        val state = _uiState.value
        if (index == state.selectedPlatformIndex) return

        // 先更新索引
        _uiState.value = state.copy(selectedPlatformIndex = index)

        viewModelScope.launch {
            val currentState = _uiState.value
            val newPlatform = currentState.platforms.getOrNull(currentState.selectedPlatformIndex)
            if (newPlatform == null) return@launch

            _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.UPDATING)

            val result = selectPlatformUseCase(applicationContext, newPlatform.first, newPlatform.second)
            when (result) {
                LinkUpdateResult.SUCCESS -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.SUCCESS)
                    delay(1500)
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
                LinkUpdateResult.ERROR -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.ERROR)
                    delay(2000)
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
                LinkUpdateResult.NO_CONFIG, LinkUpdateResult.SKIPPED -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
            }
        }
    }

    // --- NFC 硬件检测 ---

    fun setNfcSupported(supported: Boolean) {
        _uiState.value = _uiState.value.copy(nfcSupported = supported)
    }

    // --- NFC 标签写入 ---

    fun showNfcWriteDialog() {
        val now = System.currentTimeMillis()
        if (now - lastNfcWriteTime < NFC_WRITE_DEBOUNCE_MS) {
            Log.d(TAG, "NFC 写入对话框防抖，忽略")
            return
        }
        lastNfcWriteTime = now
        _uiState.value = _uiState.value.copy(
            showNfcWriteDialog = true,
            nfcWriteState = NfcWriteState.PREPARING,
            nfcWriteMessage = null
        )
    }

    fun dismissNfcWriteDialog(handler: NfcActivityHandler) {
        Log.d("Tester", "SocialViewModel.dismissNfcWriteDialog: handler=$handler")
        if (NfcHelper.isWriting) {
            handler.stopWriting()
        }
        _uiState.value = _uiState.value.copy(
            showNfcWriteDialog = false,
            nfcWriteState = NfcWriteState.IDLE,
            nfcWriteMessage = null
        )
    }

    fun startNfcWrite(handler: NfcActivityHandler) {
        Log.d("Tester", "SocialViewModel.startNfcWrite: handler=$handler")
        if (NfcHelper.isWriting) {
            Log.d(TAG, "NFC 已在写入模式中，忽略重复触发")
            return
        }
        val state = _uiState.value
        val selectedPlatform = state.platforms.getOrNull(state.selectedPlatformIndex)
        if (selectedPlatform == null) {
            _uiState.value = state.copy(
                nfcWriteState = NfcWriteState.ERROR,
                nfcWriteMessage = "请先添加一个平台"
            )
            return
        }

        val targetUrl = selectedPlatform.second.jumpLink
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nfcWriteState = NfcWriteState.PREPARING)

            val urlToWrite = prepareNfcWriteUseCase(applicationContext, targetUrl) { errorMsg ->
                _uiState.value = _uiState.value.copy(
                    nfcWriteState = NfcWriteState.ERROR,
                    nfcWriteMessage = errorMsg
                )
            } ?: return@launch

            _uiState.value = _uiState.value.copy(
                nfcWriteState = NfcWriteState.READY,
                shortUrl = urlToWrite
            )
            handler.startWriting(urlToWrite)
            Log.d(TAG, "链接就绪，等待 NFC 标签: $urlToWrite")
        }
    }

    fun onNfcWriteSuccess(handler: NfcActivityHandler) {
        Log.d("Tester", "SocialViewModel.onNfcWriteSuccess: handler=$handler")
        handler.stopWriting()
        viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(
                showNfcWriteDialog = false,
                nfcWriteState = NfcWriteState.IDLE,
                nfcWriteMessage = null
            )
        }
    }

    // --- 用户资料 ---

    fun updateProfileBasic(name: String, bio: String?, avatarPath: String?) {
        viewModelScope.launch {
            val current = repository.getUserProfileOnce()
                ?: UserProfile(name = name, bio = bio, avatarPath = avatarPath)
            val updated = current.copy(
                name = name, bio = bio?.ifBlank { null }, avatarPath = avatarPath,
                updateTime = System.currentTimeMillis()
            )
            repository.saveUserProfile(updated)
        }
    }

    fun updateAvatar(avatarPath: String?) {
        Log.d("Tester", "SocialViewModel.updateAvatar: avatarPath=$avatarPath")
        viewModelScope.launch {
            repository.updateAvatarPath(avatarPath)
        }
    }

    fun updateCardImage(cardImagePath: String?) {
        Log.d("Tester", "SocialViewModel.updateCardImage: cardImagePath=$cardImagePath")
        viewModelScope.launch {
            repository.updateCardImagePath(cardImagePath)
        }
    }

    fun addOrUpdatePlatform(fieldKey: String, jumpLink: String, value: String? = null, displayName: String? = null, avatarUrl: String? = null, originalLink: String? = null) {
        viewModelScope.launch { repository.updatePlatformField(fieldKey, jumpLink, value, displayName, avatarUrl, originalLink) }
    }

    fun removePlatform(platformName: String) {
        viewModelScope.launch { repository.removePlatform(platformName) }
    }

    fun setShowEditProfileDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEditProfileDialog = show)
    }

    fun setShowAddPlatformDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddPlatformDialog = show)
    }
}
