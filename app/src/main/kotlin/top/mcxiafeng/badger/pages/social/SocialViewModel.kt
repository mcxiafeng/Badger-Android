package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.domain.LinkUpdateResult
import top.mcxiafeng.badger.domain.LoadPlatformProfileUseCase
import top.mcxiafeng.badger.domain.PlatformProfile
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.pages.social.NfcHelper

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
    val platformProfiles: Map<String, PlatformProfile> = emptyMap(),
    val selectedPlatformIndex: Int = 0,
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
 * "我的名片"按真实联系人存储（contacts 表 id = MY_CONTACT_ID），
 * 本 VM 组合 contacts(-1) + user_profile（cardImagePath/defaultPlatform）为 UI 用的 UserProfile。
 * 平台读写直接走 ContactRepository（与联系人详情页同一套数据表）。
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val userProfileRepository: UserProfileRepository,
    @ApplicationContext private val applicationContext: android.content.Context,
    private val selectPlatformUseCase: SelectPlatformUseCase,
    private val prepareNfcWriteUseCase: PrepareNfcWriteUseCase,
    private val loadPlatformProfileUseCase: LoadPlatformProfileUseCase
) : ViewModel() {

    private val TAG = "SocialViewModel"

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    // NFC 写入防抖
    private var lastNfcWriteTime = 0L
    private val NFC_WRITE_DEBOUNCE_MS = 3000L

    // 组合 profile 的三路数据源缓存
    private var latestMyContact: Contact? = null
    private var latestProfileRow: UserProfile? = null
    private var latestPlatforms: List<Pair<String, PlatformEntry>> = emptyList()
    private val profileCache = mutableMapOf<PlatformAccount, PlatformProfile>()
    private val loadingProfiles = mutableSetOf<PlatformAccount>()

    private data class PlatformAccount(
        val fieldKey: String,
        val value: String?,
        val jumpLink: String,
        val originalLink: String?
    )

    private fun accountFor(fieldKey: String, entry: PlatformEntry) =
        PlatformAccount(fieldKey, entry.value, entry.jumpLink, entry.originalLink)

    init {
        observeMyContact()
        observeMyPlatforms()
        observeProfileRow()
        observeNfcWriteResult()
    }

    /** contacts 表 id=-1 行：名字/头像/简介（getAllContacts 已排除 -1，须用单联系人 Flow） */
    private fun observeMyContact() {
        viewModelScope.launch {
            contactRepository.observeContactById(MY_CONTACT_ID).collect { me ->
                latestMyContact = me
                publishProfile()
            }
        }
    }

    /** contact_platforms 表：平台列表（有跳转链接或账号值才算已添加） */
    private fun observeMyPlatforms() {
        viewModelScope.launch {
            contactRepository.observeContactPlatforms(MY_CONTACT_ID).collect { list ->
                latestPlatforms = list
                    .filter { it.jumpLink.isNotBlank() || !it.value.isNullOrBlank() }
                    .map { it.platformKey to PlatformEntry(
                        displayName = it.displayName,
                        jumpLink = it.jumpLink,
                        originalLink = it.originalLink,
                        value = it.value,
                        avatarUrl = it.avatarUrl
                    ) }
                publishPlatforms()
            }
        }
    }

    /** user_profile 行：cardImagePath / defaultPlatform */
    private fun observeProfileRow() {
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { row ->
                latestProfileRow = row
                publishProfile()
            }
        }
    }

    // 已应用过默认选中态的 defaultPlatform 值；变化时（含首次设置）重新跟随默认平台
    private var lastAppliedDefaultPlatform: String? = null

    private fun publishPlatforms() {
        val state = _uiState.value
        val default = state.profile?.defaultPlatform
        val defaultIndex = default?.let { d ->
            latestPlatforms.indexOfFirst { it.first == d }.takeIf { it >= 0 } ?: 0
        } ?: 0
        val currentIndex = state.selectedPlatformIndex
        val finalIndex = when {
            // defaultPlatform 变化（含首帧数据到达）→ 重新跟随默认
            default != lastAppliedDefaultPlatform -> defaultIndex
            currentIndex in latestPlatforms.indices -> currentIndex
            else -> defaultIndex
        }
        lastAppliedDefaultPlatform = default
        _uiState.value = state.copy(
            platforms = latestPlatforms,
            platformProfiles = latestPlatforms.mapNotNull { (key, entry) ->
                profileCache[accountFor(key, entry)]?.let { key to it }
            }.toMap(),
            selectedPlatformIndex = finalIndex.coerceIn(0, (latestPlatforms.size - 1).coerceAtLeast(0))
        )
    }

    private fun publishProfile() {
        val me = latestMyContact
        val row = latestProfileRow
        val platforms = latestPlatforms.toMap()
        _uiState.value = _uiState.value.copy(
            profile = UserProfile(
                name = me?.name ?: "",
                avatarPath = me?.avatarPath,
                cardImagePath = row?.cardImagePath,
                bio = me?.bio,
                platforms = platforms,
                defaultPlatform = row?.defaultPlatform,
                updateTime = me?.updateTime ?: row?.updateTime ?: 0L
            )
        )
        // 平台列表可能随 profile.defaultPlatform 变化需要重算默认索引
        publishPlatforms()
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

    /** 当前居中平台的展示资料；请求只更新对应账号的缓存，不改联系人资料或分享目标。 */
    fun loadPlatformProfile(fieldKey: String) {
        val entry = latestPlatforms.firstOrNull { it.first == fieldKey }?.second ?: return
        val account = accountFor(fieldKey, entry)
        if (profileCache.containsKey(account) || !loadingProfiles.add(account)) return
        Log.d("SocialViewModelTester", "Load platform profile: platform=$fieldKey")
        viewModelScope.launch {
            try {
                val result = loadPlatformProfileUseCase(fieldKey, entry) ?: return@launch
                val latest = latestPlatforms.firstOrNull { it.first == fieldKey }?.second
                if (latest == null || accountFor(fieldKey, latest) != account) {
                    Log.d("SocialViewModelTester", "Discard profile for changed account: platform=$fieldKey")
                    return@launch
                }
                profileCache[account] = result
                _uiState.value = _uiState.value.copy(
                    platformProfiles = _uiState.value.platformProfiles + (fieldKey to result)
                )
                Log.d("SocialViewModelTester", "Platform profile ready: platform=$fieldKey")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SocialViewModelTester", "Load platform profile failed: platform=$fieldKey", e)
            } finally {
                loadingProfiles.remove(account)
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

    // --- 用户资料 / 平台 ---

    fun updateCardImage(cardImagePath: String?) {
        Log.d("Tester", "SocialViewModel.updateCardImage: cardImagePath=$cardImagePath")
        viewModelScope.launch {
            userProfileRepository.updateCardImagePath(cardImagePath)
        }
    }

    /** 添加/更新"我的名片"平台（与联系人详情页同一套 contact_platforms 数据表） */
    fun addOrUpdatePlatform(
        fieldKey: String,
        jumpLink: String,
        value: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        originalLink: String? = null
    ) {
        viewModelScope.launch {
            contactRepository.updateContactPlatform(
                MY_CONTACT_ID,
                fieldKey,
                PlatformEntry(
                    displayName = displayName,
                    jumpLink = jumpLink,
                    originalLink = originalLink,
                    value = value,
                    avatarUrl = avatarUrl
                )
            )
        }
    }
}
