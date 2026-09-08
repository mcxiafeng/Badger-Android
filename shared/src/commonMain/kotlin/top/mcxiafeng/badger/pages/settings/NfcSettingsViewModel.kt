package top.mcxiafeng.badger.pages.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ShortIoDomain
import top.mcxiafeng.badger.network.ShortIoLink
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "NfcSettingsVM"

/** NFC 设置 UI 状态（short.io 配置 + 自定义短链 + 网络加载态）。 */
data class NfcSettingsUiState(
    val shortLinkEnabled: Boolean = false,
    val apiKey: String = "",
    val domain: String = "",
    val selectedLinkId: String = "",
    val shortUrl: String? = null,
    val customEnabled: Boolean = false,
    val apiUrl: String = "",
    val updatePath: String = "",
    val apiMethod: String = "",
    val authHeader: String = "",
    val authPrefix: String = "",
    val updateBody: String = "",
    val domains: List<ShortIoDomain> = emptyList(),
    val domainsLoading: Boolean = false,
    val domainError: String? = null,
    val links: List<ShortIoLink> = emptyList(),
    val linksLoading: Boolean = false,
    val linkError: String? = null,
    val currentLinkDetails: ShortIoLink? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val defaultPlatform: String? = null,
    val creatingLink: Boolean = false,
    val createError: String? = null,
)

/** 自定义短链高级配置字段。 */
enum class AdvancedField { API_URL, UPDATE_PATH, API_METHOD, AUTH_HEADER, AUTH_PREFIX, UPDATE_BODY }

/**
 * NFC 设置 VM（重写）：接管原页直接调用 [ShortLinkService] 的全部网络与持久化。
 *
 * 修复架构红线：UI 不再直连网络层，仅通过本 VM 的方法 + [state] 渲染。
 * 订阅 [UserProfileRepository.getUserProfile] 以拿到 defaultPlatform 用于「当前指向」展示。
 */
class NfcSettingsViewModel : ViewModel() {

    private val userProfileRepository: UserProfileRepository = KoinComponentBy.get()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<NfcSettingsUiState> = _state.asStateFlow()

    init {
        BadgerLog.d(TAG, "NfcSettingsViewModel initialized")
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                patch { copy(defaultPlatform = profile?.defaultPlatform) }
            }
        }
        loadDomains()
        loadLinks()
        loadLinkDetails()
    }

    private fun snapshot(): NfcSettingsUiState = NfcSettingsUiState(
        shortLinkEnabled = ShortLinkService.isEnabled(),
        apiKey = ShortLinkService.getApiKey(),
        domain = ShortLinkService.getDomain(),
        selectedLinkId = ShortLinkService.getLinkId(),
        shortUrl = ShortLinkService.getShortUrl(),
        customEnabled = ShortLinkService.isCustomEnabled(),
        apiUrl = ShortLinkService.getApiUrl(),
        updatePath = ShortLinkService.getUpdatePath(),
        apiMethod = ShortLinkService.getApiMethod(),
        authHeader = ShortLinkService.getAuthHeader(),
        authPrefix = ShortLinkService.getAuthPrefix(),
        updateBody = ShortLinkService.getUpdateBody(),
    )

    // ── 基础开关 / 选择 ────────────────────────────────────────────

    fun setShortLinkEnabled(v: Boolean) {
        ShortLinkService.setEnabled(v)
        patch { copy(shortLinkEnabled = v) }
        BadgerLog.d(TAG, "短链接功能切换: $v")
    }

    /** API Key 失焦时落盘 + 重拉域名（清空已选域名/链接）。 */
    fun saveApiKey(v: String) {
        ShortLinkService.saveApiKey(v)
        patch {
            copy(
                apiKey = v, domain = "", selectedLinkId = "", shortUrl = null,
                domains = emptyList(), links = emptyList(), currentLinkDetails = null,
            )
        }
        loadDomains()
    }

    fun selectDomain(d: ShortIoDomain) {
        ShortLinkService.saveDomainSelection(d)
        patch { copy(domain = d.hostname, selectedLinkId = "", shortUrl = null, links = emptyList(), currentLinkDetails = null) }
        loadLinks()
    }

    fun selectLink(l: ShortIoLink) {
        ShortLinkService.saveLinkSelection(l)
        val short = l.shortURL.ifBlank { "https://${_state.value.domain}/${l.path}" }
        patch { copy(selectedLinkId = l.idString, shortUrl = short, links = listOf(l)) }
        loadLinkDetails()
    }

    fun createLink(originalUrl: String) {
        if (originalUrl.isBlank()) {
            patch { copy(createError = "请输入目标链接") }
            return
        }
        patch { copy(creatingLink = true, createError = null) }
        viewModelScope.launch {
            ShortLinkService.createShortIoLink(originalUrl)
                .onSuccess { link ->
                    ShortLinkService.saveLinkSelection(link)
                    val short = link.shortURL.ifBlank { "https://${_state.value.domain}/${link.path}" }
                    patch { copy(selectedLinkId = link.idString, shortUrl = short, links = listOf(link), creatingLink = false, createError = null) }
                    loadLinkDetails()
                    BadgerLog.d(TAG, "createLink ok")
                }
                .onFailure { e ->
                    patch { copy(creatingLink = false, createError = e.message ?: "创建失败") }
                    BadgerLog.w(TAG, "createLink failed: ${e::class.simpleName}: ${e.message}")
                }
        }
    }

    fun clearCreateError() = patch { copy(createError = null) }

    // ── 自定义短链高级配置 ──────────────────────────────────────────

    fun setCustomEnabled(v: Boolean) {
        val s = _state.value
        ShortLinkService.saveAdvancedSettings(v, s.apiUrl, s.updatePath, s.apiMethod, s.authHeader, s.authPrefix, s.updateBody)
        patch { copy(customEnabled = v) }
    }

    /** 单字段更新：写回 prefs（saveAdvancedSettings 全量覆盖语义）。 */
    fun updateAdvanced(field: AdvancedField, value: String) {
        val s = _state.value
        val next = when (field) {
            AdvancedField.API_URL -> s.copy(apiUrl = value)
            AdvancedField.UPDATE_PATH -> s.copy(updatePath = value)
            AdvancedField.API_METHOD -> s.copy(apiMethod = value)
            AdvancedField.AUTH_HEADER -> s.copy(authHeader = value)
            AdvancedField.AUTH_PREFIX -> s.copy(authPrefix = value)
            AdvancedField.UPDATE_BODY -> s.copy(updateBody = value)
        }
        ShortLinkService.saveAdvancedSettings(next.customEnabled, next.apiUrl, next.updatePath, next.apiMethod, next.authHeader, next.authPrefix, next.updateBody)
        _state.value = next
    }

    // ── 网络加载（重试入口） ───────────────────────────────────────

    fun retryDomains() = loadDomains()
    fun retryLinks() = loadLinks()
    fun retryLinkDetails() = loadLinkDetails()

    private fun loadDomains() {
        val key = _state.value.apiKey
        if (key.isBlank()) {
            patch { copy(domains = emptyList(), domainError = null) }
            return
        }
        patch { copy(domainsLoading = true, domainError = null) }
        viewModelScope.launch {
            ShortLinkService.fetchDomains()
                .onSuccess { patch { copy(domains = it, domainsLoading = false) } }
                .onFailure { e -> patch { copy(domainsLoading = false, domainError = e.message ?: "获取失败") } }
        }
    }

    private fun loadLinks() {
        val domainId = ShortLinkService.getDomainId()
        if (domainId <= 0) {
            patch { copy(links = emptyList()) }
            return
        }
        patch { copy(linksLoading = true, linkError = null) }
        viewModelScope.launch {
            ShortLinkService.fetchLinks(domainId)
                .onSuccess { patch { copy(links = it, linksLoading = false) } }
                .onFailure { e -> patch { copy(linksLoading = false, linkError = e.message ?: "获取失败") } }
        }
    }

    private fun loadLinkDetails() {
        if (!ShortLinkService.isConfigured()) return
        patch { copy(detailsLoading = true, detailsError = null) }
        viewModelScope.launch {
            ShortLinkService.fetchLinkDetails()
                .onSuccess { patch { copy(currentLinkDetails = it, detailsLoading = false) } }
                .onFailure { e -> patch { copy(detailsLoading = false, detailsError = e.message ?: "获取失败") } }
        }
    }

    private fun patch(block: NfcSettingsUiState.() -> NfcSettingsUiState) {
        _state.value = _state.value.block()
    }
}
