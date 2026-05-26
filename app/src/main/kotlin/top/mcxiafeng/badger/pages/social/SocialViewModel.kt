package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.ContactRepository
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.pages.social.NfcHelper
import top.mcxiafeng.badger.network.ShortLinkService

/**
 * NFC 标签写入状态
 */
enum class NfcWriteState {
    IDLE,       // 未激活
    PREPARING,  // 正在准备短链接
    READY,      // 等待用户贴标签
    SUCCESS,    // 写入成功
    ERROR       // 写入失败
}

/**
 * 短链接更新状态（平台切换时的实时反馈）
 */
enum class LinkUpdateState {
    IDLE,       // 默认状态（未配置或已完成）
    UPDATING,   // 正在更新
    SUCCESS,    // 更新成功
    ERROR       // 更新失败
}

/**
 * 扩列页面的 UI 状态
 *
 * @property profile 用户个人资料
 * @property platforms 已添加的平台列表 (平台名, PlatformEntry)
 * @property selectedPlatformIndex 当前选中的平台索引
 * @property showEditProfileDialog 是否显示编辑名片对话框
 * @property showAddPlatformDialog 是否显示添加平台对话框
 * @property nfcSupported 设备是否支持 NFC 硬件
 * @property showNfcWriteDialog 是否显示 NFC 写入对话框
 * @property nfcWriteState NFC 写入状态
 * @property nfcWriteMessage NFC 写入结果消息
 * @property shortUrl 当前短链接 URL
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
    val linkUpdateState: LinkUpdateState = LinkUpdateState.IDLE,  // 短链接更新状态
)

/**
 * 扩列页面的 ViewModel
 *
 * 管理用户名片数据、NFC 标签写入、短链接更新。
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    val repository: ContactRepository,
    @ApplicationContext private val applicationContext: android.content.Context
) : ViewModel() {

    private val TAG = "SocialViewModel"

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    // 防抖保护：记录上次切换时间和当前正在处理的 job
    private var lastSwitchTime = 0L
    private val DEBOUNCE_MS = 2000L // 2秒防抖间隔
    private var currentUpdateJob: kotlinx.coroutines.Job? = null

    // NFC 写入防抖
    private var lastNfcWriteTime = 0L
    private val NFC_WRITE_DEBOUNCE_MS = 3000L // 3秒防抖间隔

    init {
        loadProfile()
        observeNfcWriteResult()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.getUserProfile().collect { profile ->
                val platforms = if (profile != null) buildPlatformList(profile) else emptyList()

                // 根据 defaultPlatform 确定索引
                val defaultIndex = if (profile?.defaultPlatform != null) {
                    platforms.indexOfFirst { it.first == profile.defaultPlatform }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }

                // 检查 defaultPlatform 是否变化了（设置页面修改）
                val oldDefaultPlatform = _uiState.value.profile?.defaultPlatform
                val newDefaultPlatform = profile?.defaultPlatform
                val defaultPlatformChanged = oldDefaultPlatform != newDefaultPlatform

                // 如果 defaultPlatform 变化了，使用新的 defaultIndex
                // 否则保持用户手动切换的索引（如果有效）
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

    /** 监听 NfcHelper 的写入结果 */
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

    /** 切换选中的平台，同时更新短链接目标地址并显示状态 */
    fun selectPlatform(index: Int) {
        val state = _uiState.value
        if (index == state.selectedPlatformIndex) return

        // 防抖检查
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < DEBOUNCE_MS) {
            Log.d(TAG, "切换过于频繁，忽略此次切换 (间隔 ${now - lastSwitchTime}ms)")
            return
        }
        lastSwitchTime = now

        // 取消之前的更新任务
        currentUpdateJob?.cancel()

        // 先更新索引
        _uiState.value = state.copy(selectedPlatformIndex = index)

        currentUpdateJob = viewModelScope.launch {
            // 协程内部重新读取最新状态，确保获取正确的平台
            val currentState = _uiState.value
            val newPlatform = currentState.platforms.getOrNull(currentState.selectedPlatformIndex)
            if (newPlatform == null) return@launch

            // 从数据库重新读取最新 profile，避免用过时的 UI 快照覆盖并发修改
            val profile = repository.getUserProfileOnce()
            if (profile != null && profile.defaultPlatform != newPlatform.first) {
                repository.saveUserProfile(profile.copy(
                    defaultPlatform = newPlatform.first,
                    updateTime = System.currentTimeMillis()
                ))
                Log.d(TAG, "defaultPlatform 已更新: ${newPlatform.first}")
            }

            // 检查是否配置了短链接
            if (!ShortLinkService.isConfigured(applicationContext)) {
                _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                return@launch
            }

            // 开始更新
            _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.UPDATING)

            val result = ShortLinkService.updateLinkDestination(applicationContext, newPlatform.second.jumpLink)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.SUCCESS)
                Log.d(TAG, "短链接更新成功: ${newPlatform.second.jumpLink}")
                // 1.5秒后恢复 IDLE
                kotlinx.coroutines.delay(1500)
                _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.ERROR)
                Log.w(TAG, "短链接更新失败", e)
                // 2秒后恢复 IDLE
                kotlinx.coroutines.delay(2000)
                _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
            }
        }
    }

    // --- NFC 硬件检测 ---

    fun setNfcSupported(supported: Boolean) {
        _uiState.value = _uiState.value.copy(nfcSupported = supported)
    }

    // --- NFC 标签写入 ---

    /** 显示 NFC 写入对话框 */
    fun showNfcWriteDialog() {
        // 防抖
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

    /** 关闭 NFC 写入对话框 */
    fun dismissNfcWriteDialog(activity: android.app.Activity) {
        if (NfcHelper.isWriting) {
            NfcHelper.stopWriting(activity)
        }
        _uiState.value = _uiState.value.copy(
            showNfcWriteDialog = false,
            nfcWriteState = NfcWriteState.IDLE,
            nfcWriteMessage = null
        )
    }

    /**
     * 开始 NFC 写入流程：
     * 1. 准备短链接（创建或更新）
     * 2. 进入等待标签状态
     * 3. 标签贴上后自动写入
     */
    fun startNfcWrite(activity: android.app.Activity) {
        // 防抖：如果已经在写入中，不重复触发
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

            val savedUrl = ShortLinkService.getShortUrl(applicationContext)
            if (savedUrl == null) {
                _uiState.value = _uiState.value.copy(
                    nfcWriteState = NfcWriteState.ERROR,
                    nfcWriteMessage = "请先在设置中选择一个短链接"
                )
                return@launch
            }

            // 更新短链接目标地址
            val updateResult = ShortLinkService.updateLinkDestination(applicationContext, targetUrl)
            updateResult.onFailure {
                Log.w(TAG, "更新短链接目标地址失败，仍使用已有链接写入", it)
            }

            val urlToWrite = updateResult.getOrDefault(savedUrl)

            _uiState.value = _uiState.value.copy(
                nfcWriteState = NfcWriteState.READY,
                shortUrl = urlToWrite
            )
            NfcHelper.startWriting(activity, urlToWrite)
            Log.d(TAG, "短链接就绪，等待 NFC 标签: $urlToWrite")
        }
    }

    /** NFC 写入成功后关闭对话框（stopWriting 内部会延迟 3 秒禁用 ReaderMode，防止系统弹选择器） */
    fun onNfcWriteSuccess(activity: android.app.Activity) {
        // 先停止写入（清除 URI，但 ReaderMode 延迟 3 秒后才禁用）
        NfcHelper.stopWriting(activity)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
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
            // 从数据库重新读取最新 profile，避免用过时的 UI 快照覆盖并发修改
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
