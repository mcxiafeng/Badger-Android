package top.mcxiafeng.badger.pages.server

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.HttpResult
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.network.core.ServerAddress
import top.mcxiafeng.badger.network.core.ServerAddressStore
import top.mcxiafeng.badger.utils.SafeLog
import javax.inject.Inject

private const val TAG = "ServerAddressesViewModelTester"

/** 单条连接地址的最近一次测试结果（瞬时状态，不持久）。 */
data class AddressTestResult(
    val success: Boolean,
    val message: String?,
)

@Immutable
data class ServerAddressesUiState(
    val servers: List<ServerAddress> = emptyList(),
    val activeBase: String? = null,
    val testing: Set<String> = emptySet(),
    val testResults: Map<String, AddressTestResult> = emptyMap(),
    val editingUrl: String? = null,
    val formName: String = "",
    val formUrl: String = "",
    val formHttps: Boolean = DEFAULT_HTTPS,
    val formUrlReason: String? = null,
) {
    val canSave: Boolean get() = formUrl.isNotBlank() && formUrlReason == null
}

/**
 * 连接地址管理页 VM（移植自 dev 分支 page/server/ServerAddressesViewModel）：
 * 同一台服务器的多入口别名 + 增删、拖动排序、编辑、自动连通测试。
 * 列表顺序 = 用户排列 = 连接尝试顺序，VM 绝不自动排序或优选；
 * 添加原样追加队尾，URL 仅大小写不同视为同一条，拒绝重复。
 */
@HiltViewModel
class ServerAddressesViewModel @Inject constructor(
    private val addressStore: ServerAddressStore,
    private val publicApi: PublicApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerAddressesUiState())
    val uiState: StateFlow<ServerAddressesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            addressStore.configuredUrl.collect { url ->
                Log.d(TAG, "当前配置地址已更新，已配置=${url != null}")
                _uiState.update { it.copy(activeBase = url) }
            }
        }
        viewModelScope.launch {
            addressStore.addresses.collect { servers ->
                Log.d(TAG, "地址列表观察更新：${servers.size} 个")
                val previousUrls = _uiState.value.servers.map { it.url }.toSet()
                val currentUrls = servers.map { it.url }.toSet()
                _uiState.update { state ->
                    state.copy(
                        servers = servers,
                        testResults = state.testResults.filterKeys { it in currentUrls },
                        testing = state.testing.intersect(currentUrls),
                    )
                }
                servers.filter { it.url !in previousUrls }.forEach { testAddress(it.url) }
            }
        }
    }

    fun startEdit(address: ServerAddress) {
        Log.d(TAG, "进入编辑：${SafeLog.url(address.url)}")
        _uiState.update {
            it.copy(
                editingUrl = address.url,
                formName = address.name,
                formUrl = address.url,
                formHttps = address.url.startsWith("https://", ignoreCase = true),
                formUrlReason = null,
            )
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(editingUrl = null, formName = "", formUrl = "", formHttps = DEFAULT_HTTPS, formUrlReason = null)
        }
    }

    fun onFormNameChange(value: String) = _uiState.update { it.copy(formName = value) }

    fun onFormUrlChange(raw: String) {
        val (text, scheme) = ServerValidator.normalizeInput(raw, _uiState.value.formHttps)
        _uiState.update { it.copy(formUrl = text, formHttps = scheme, formUrlReason = ServerValidator.blockReason(text)) }
    }

    fun onFormHttpsChange(enabled: Boolean) {
        _uiState.update { it.copy(formHttps = enabled, formUrlReason = ServerValidator.blockReason(_uiState.value.formUrl)) }
    }

    suspend fun saveForm(): String? {
        val state = _uiState.value
        val editingUrl = state.editingUrl
        val error = if (editingUrl == null) {
            addAddress(state.formName, state.formUrl, state.formHttps)
        } else {
            updateAddress(editingUrl, state.formName, state.formUrl, state.formHttps)
        }
        if (error == null) cancelEdit()
        return error
    }

    suspend fun addAddress(name: String, rawInput: String, https: Boolean): String? {
        val input = ServerValidator.normalizeInput(rawInput, https).first
        val reason = ServerValidator.blockReason(input)
        if (reason != null) {
            Log.w(TAG, "添加地址被拦：$reason（${SafeLog.url(input)}）")
            return reason
        }
        val url = ServerValidator.composeUrl(ServerValidator.withDefaultPort(input), https)
        if (_uiState.value.servers.any { it.url.equals(url, ignoreCase = true) }) {
            Log.w(TAG, "添加地址被拦：已存在（${SafeLog.url(url)}）")
            return "该地址已存在"
        }
        addressStore.setAddresses(_uiState.value.servers + ServerAddress(name = name.trim(), url = url))
        Log.d(TAG, "连接地址已追加：${SafeLog.url(input)}")
        return null
    }

    suspend fun updateAddress(oldUrl: String, name: String, rawInput: String, https: Boolean): String? {
        val input = ServerValidator.normalizeInput(rawInput, https).first
        val reason = ServerValidator.blockReason(input)
        if (reason != null) {
            Log.w(TAG, "编辑地址被拦：$reason（${SafeLog.url(input)}）")
            return reason
        }
        val url = ServerValidator.composeUrl(ServerValidator.withDefaultPort(input), https)
        val duplicated = _uiState.value.servers.any {
            it.url.equals(url, ignoreCase = true) && !it.url.equals(oldUrl, ignoreCase = true)
        }
        if (duplicated) {
            Log.w(TAG, "编辑地址被拦：目标 URL 已存在（${SafeLog.url(url)}）")
            return "该地址已存在"
        }
        // URL 未变只改名；URL 变了视为新地址：测试态作废，激活态跟随迁移
        if (oldUrl.equals(url, ignoreCase = true)) {
            addressStore.setAddresses(_uiState.value.servers.map { if (it.url == oldUrl) it.copy(name = name.trim()) else it })
            return null
        }
        addressStore.setAddresses(_uiState.value.servers.map { if (it.url == oldUrl) ServerAddress(name = name.trim(), url = url) else it })
        _uiState.update { state ->
            state.copy(testResults = state.testResults - oldUrl, testing = state.testing - oldUrl)
        }
        if (addressStore.activeUrl.value == oldUrl) addressStore.setActive(url)
        Log.d(TAG, "连接地址已编辑：${SafeLog.url(oldUrl)} → ${SafeLog.url(input)}")
        return null
    }

    /** 删除一条连接地址。 */
    fun removeAddress(url: String) {
        viewModelScope.launch {
            addressStore.setAddresses(_uiState.value.servers.filterNot { it.url == url })
            _uiState.update { state -> state.copy(testResults = state.testResults - url, testing = state.testing - url) }
            Log.d(TAG, "连接地址已删除：${SafeLog.url(url)}")
        }
    }

    /** 拖动排序提交：整表按新顺序落库。 */
    fun reorderAddresses(urls: List<String>) {
        val byUrl = _uiState.value.servers.associateBy { it.url }
        addressStore.setAddresses(urls.mapNotNull { byUrl[it] })
        Log.d(TAG, "拖动排序已保存：${urls.size} 个地址")
    }

    /** GET {url}/api/info；任意 HTTP 响应算可达，仅网络异常/超时算失败。 */
    private fun testAddress(url: String) {
        if (url in _uiState.value.testing) return
        _uiState.update { it.copy(testing = it.testing + url, testResults = it.testResults - url) }
        viewModelScope.launch {
            try {
                val result = publicApi.getDirect(url, PublicApi.INFO_PATH)
                val outcome = when (result) {
                    is HttpResult.Success -> {
                        publicApi.recordSuccessBase(url)
                        AddressTestResult(success = true, message = null)
                    }
                    is HttpResult.Failure -> AddressTestResult(
                        success = false,
                        message = when (result.errorType) {
                            HttpResult.ErrorType.TIMEOUT -> "连接超时"
                            HttpResult.ErrorType.NETWORK -> "无法连接"
                            else -> "HTTP ${result.code}"
                        },
                    )
                }
                Log.d(TAG, "自动检测${if (outcome.success) "通过" else "失败"}：${SafeLog.url(url)}")
                _uiState.update { state ->
                    if (state.servers.none { it.url == url }) state
                    else state.copy(testResults = state.testResults + (url to outcome))
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "地址检测取消：${SafeLog.url(url)}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "自动检测异常：${SafeLog.url(url)}", e)
                _uiState.update { state ->
                    if (state.servers.none { it.url == url }) state
                    else state.copy(testResults = state.testResults + (url to AddressTestResult(false, "无法连接")))
                }
            } finally {
                _uiState.update { state -> state.copy(testing = state.testing - url) }
            }
        }
    }
}
