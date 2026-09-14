package top.mcxiafeng.badger.pages.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.domain.LinkUpdateResult
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.network.UserSettings
import top.mcxiafeng.badger.platform.NfcWriter
import top.mcxiafeng.badger.platform.downloadImage
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

enum class NfcWriteState {
    IDLE, PREPARING, READY, SUCCESS, ERROR
}

enum class LinkUpdateState {
    IDLE, UPDATING, SUCCESS, ERROR
}

@Immutable
data class SocialUiState(
    val profile: UserProfile? = null,
    val platforms: List<Pair<String, PlatformEntry>> = emptyList(),
    val selectedPlatformIndex: Int = 0,
    val nfcSupported: Boolean = false,
    val showNfcWriteDialog: Boolean = false,
    val nfcWriteState: NfcWriteState = NfcWriteState.IDLE,
    val nfcWriteMessage: String? = null,
    val shortUrl: String? = null,
    val linkUpdateState: LinkUpdateState = LinkUpdateState.IDLE,
    val shortLinkProvider: String? = null,
    val shortLinkConfigured: Boolean = false,
)

class SocialViewModel : ViewModel() {

    private val repository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val selectPlatformUseCase: SelectPlatformUseCase = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val prepareNfcWriteUseCase: PrepareNfcWriteUseCase = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val nfcWriter: NfcWriter = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val userAuthRepository: UserAuthRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val TAG = "SocialViewModel"

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    
    private val LINK_UPDATE_SUCCESS_DELAY_MS = 1500L
    private val LINK_UPDATE_ERROR_DELAY_MS = 2000L
    
    private val NFC_SUCCESS_DISMISS_DELAY_MS = 1500L

    init {
        loadProfile()
        observeNfcWriteResult()
        observeShortLinkConfig()
    }

    

    
    private fun observeShortLinkConfig() {
        viewModelScope.launch {
            userAuthRepository.state.collect { auth ->
                when (auth) {
                    is AuthState.SignedIn -> refreshShortLinkConfig()
                    else -> _uiState.value = _uiState.value.copy(
                        shortLinkProvider = null,
                        shortLinkConfigured = false,
                    )
                }
            }
        }
    }

    private fun refreshShortLinkConfig() {
        viewModelScope.launch {
            
            runCatching { withContext(BadgerDispatchers.io) { serverApiFactory.get().getUserSettings() } }
                .onSuccess { settings ->
                    val provider = settings.shortLinkProvider
                    _uiState.value = _uiState.value.copy(
                        shortLinkProvider = provider,
                        shortLinkConfigured = !provider.isNullOrBlank(),
                    )
                    BadgerLog.d(TAG, "短链配置刷新: provider=$provider")
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    BadgerLog.e(TAG, "短链配置刷新失败", e)
                }
        }
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
            nfcWriter.writeResult.collect { result ->
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
        return ContactMapper.decodePlatformsMap(profile.platformsJson)
            ?.filter { it.value.jumpLink.isNotBlank() || !it.value.value.isNullOrBlank() }
            ?.map { (key, entry) -> key to entry }
            ?.toList() ?: emptyList()
    }

    
    fun selectPlatform(index: Int) {
        val state = _uiState.value
        if (index == state.selectedPlatformIndex) return

        
        _uiState.value = state.copy(selectedPlatformIndex = index)

        viewModelScope.launch {
            val currentState = _uiState.value
            val newPlatform = currentState.platforms.getOrNull(currentState.selectedPlatformIndex)
            if (newPlatform == null) return@launch

            _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.UPDATING)

            val result = selectPlatformUseCase(newPlatform.first, newPlatform.second)
            when (result) {
                LinkUpdateResult.SUCCESS -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.SUCCESS)
                    delay(LINK_UPDATE_SUCCESS_DELAY_MS)
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
                LinkUpdateResult.ERROR -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.ERROR)
                    delay(LINK_UPDATE_ERROR_DELAY_MS)
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
                LinkUpdateResult.NO_CONFIG -> {
                    _uiState.value = _uiState.value.copy(linkUpdateState = LinkUpdateState.IDLE)
                }
            }
        }
    }

    

    fun setNfcSupported(supported: Boolean) {
        _uiState.value = _uiState.value.copy(nfcSupported = supported)
    }

    

    fun showNfcWriteDialog() {
        _uiState.value = _uiState.value.copy(
            showNfcWriteDialog = true,
            nfcWriteState = NfcWriteState.PREPARING,
            nfcWriteMessage = null
        )
    }

    fun dismissNfcWriteDialog(handler: NfcActivityHandler) {
                if (nfcWriter.isWriting) {
            handler.stopWriting()
        }
        _uiState.value = _uiState.value.copy(
            showNfcWriteDialog = false,
            nfcWriteState = NfcWriteState.IDLE,
            nfcWriteMessage = null
        )
    }

    fun startNfcWrite(handler: NfcActivityHandler) {
                if (nfcWriter.isWriting) {
            BadgerLog.d(TAG, "NFC 已在写入模式中，忽略重复触发")
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

        
        
        val targetUrl = platformShareUrl(selectedPlatform.second)
        if (targetUrl == null) {
            _uiState.value = state.copy(
                nfcWriteState = NfcWriteState.ERROR,
                nfcWriteMessage = "该平台没有可写入的链接，请先填写主页链接或 URL"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nfcWriteState = NfcWriteState.PREPARING)

            val urlToWrite = prepareNfcWriteUseCase(targetUrl) { errorMsg ->
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
            BadgerLog.d(TAG, "链接就绪，等待 NFC 标签: $urlToWrite")
        }
    }

    fun onNfcWriteSuccess(handler: NfcActivityHandler) {
                handler.stopWriting()
        viewModelScope.launch {
            delay(NFC_SUCCESS_DISMISS_DELAY_MS)
            _uiState.value = _uiState.value.copy(
                showNfcWriteDialog = false,
                nfcWriteState = NfcWriteState.IDLE,
                nfcWriteMessage = null
            )
        }
    }

    

    fun addOrUpdatePlatform(fieldKey: String, jumpLink: String, value: String? = null, displayName: String? = null, avatarUrl: String? = null, originalLink: String? = null) {
        viewModelScope.launch { repository.updatePlatformField(fieldKey, jumpLink, value, displayName, avatarUrl, originalLink) }
    }

    fun removePlatform(platformName: String) {
        viewModelScope.launch { repository.removePlatform(platformName) }
    }
}
