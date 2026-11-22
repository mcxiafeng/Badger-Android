package top.mcxiafeng.badger.network.core

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** 一条连接地址：同一服务器的多入口（内网/公网/动态 IP）别名。 */
data class ServerAddress(
    val name: String = "",
    val url: String,
)

/**
 * 服务器地址状态源（对应 dev 分支 System 库 UserInfo.serverIps 的角色）。
 *
 * - 地址列表顺序 = 用户排列 = 连接尝试顺序，绝不自动排序或优选；
 * - activeUrl 是当前全部网络请求的 base（PublicApi 实时读取，热更生效）；
 * - history 为 ChoiceServer 页的手输地址历史（最近在前，上限 [HISTORY_MAX]）。
 *
 * 持久化用 SharedPreferences + Gson（服务器配置属配置数据而非业务数据，
 * 避免为它升级 Room 版本号）。明文存储与项目既有安全限制同级。
 */
@Singleton
class ServerAddressStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _addresses = MutableStateFlow(readAddresses())
    val addresses: StateFlow<List<ServerAddress>> = _addresses.asStateFlow()

    private val _activeUrl = MutableStateFlow(prefs.getString(KEY_ACTIVE_URL, null) ?: DEFAULT_SERVER_URL)
    val activeUrl: StateFlow<String> = _activeUrl.asStateFlow()

    /**
     * 用户是否配置过服务器地址（走过一次 ChoiceServer 的「下一步」即视为已配置）。
     * 「登录」入口据此分流：未配置 → 服务器页；已配置 → 直接登录页。
     */
    private val _hasActiveServer = MutableStateFlow(prefs.getBoolean(KEY_ACTIVE_CONFIGURED, false))
    val hasActiveServer: StateFlow<Boolean> = _hasActiveServer.asStateFlow()

    /** 用户配置的当前地址，供界面显示。 */
    val configuredUrl: Flow<String?> = combine(activeUrl, hasActiveServer) { url, configured ->
        url.takeIf { configured }
    }

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /**
     * 自上次激活地址变更以来，当前 URL 是否被验证可达（连通探测/登录成功）。
     * 不持久化：重启后重新探测，避免上次验证的网络环境被默认可信。
     */
    private val _isUrlVerified = MutableStateFlow(false)
    val isUrlVerified: StateFlow<Boolean> = _isUrlVerified.asStateFlow()

    /** 激活地址并持久化；URL 一变验证自动失效，即便回填同样 URL 也按重新配置处理。 */
    fun setActive(url: String) {
        Log.d(TAG, "setActive: ${url.length} 字符地址写入，验证态重置，标记已配置")
        prefs.edit()
            .putString(KEY_ACTIVE_URL, url)
            .putBoolean(KEY_ACTIVE_CONFIGURED, true)
            .apply()
        _activeUrl.value = url
        _hasActiveServer.value = true
        _isUrlVerified.value = false
    }

    /** 整表替换地址列表（拖动排序/编辑/删除共用），并持久化。 */
    fun setAddresses(list: List<ServerAddress>) {
        prefs.edit().putString(KEY_ADDRESSES, gson.toJson(list)).apply()
        _addresses.value = list
    }

    /** 追加手输历史（去重、最近在前、截断上限）。 */
    fun addHistory(url: String) {
        val next = (listOf(url) + _history.value).distinct().take(HISTORY_MAX)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(next)).apply()
        _history.value = next
    }

    fun removeHistory(entry: String) {
        val next = _history.value - entry
        prefs.edit().putString(KEY_HISTORY, gson.toJson(next)).apply()
        _history.value = next
    }

    /** 由连通探测成功 / 登录成功路径调用；幂等。 */
    fun markUrlVerified() {
        if (!_isUrlVerified.value) {
            _isUrlVerified.value = true
            Log.d(TAG, "markUrlVerified: 服务器验证通过")
        }
    }

    private fun readAddresses(): List<ServerAddress> = runCatching {
        val raw = prefs.getString(KEY_ADDRESSES, null) ?: return emptyList()
        val type = TypeToken.getParameterized(List::class.java, ServerAddress::class.java).type
        // Gson 对空串/空字面量返回 null 而不抛异常，getOrDefault 不生效，必须显式兜底
        gson.fromJson<List<ServerAddress>>(raw, type) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readHistory(): List<String> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = TypeToken.getParameterized(List::class.java, String::class.java).type
        gson.fromJson<List<String>>(raw, type) ?: emptyList()
    }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "ServerAddressStoreTester"
        private const val PREFS_NAME = "server_config"
        private const val KEY_ADDRESSES = "addresses"
        private const val KEY_ACTIVE_URL = "active_url"
        private const val KEY_ACTIVE_CONFIGURED = "active_configured"
        private const val KEY_HISTORY = "history"

        /** 历史条数上限（与 ChoiceServer 历史区展示一致）。 */
        const val HISTORY_MAX = 4

        /** 无已保存地址时的兜底地址（局域网开发服务器）。 */
        const val DEFAULT_SERVER_URL = "http://192.168.10.19:8080"
    }
}
