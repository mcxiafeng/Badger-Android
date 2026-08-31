package top.mcxiafeng.badger.data.repository

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * 行政区划节点
 *
 * 来自 dr5hn/countries-states-cities-database。
 * 中文显示名优先取 `name` 字段(translations.zh-CN 在该数据集中不完整,改用原始 name)。
 *
 * @property name 显示名
 * @property externalId 数据集中的 id(country/state/city 的主键)
 * @property parentId 上级节点的 id(国家为 null)
 * @property children 子节点(国/省/市,叶节点为空)
 */
data class RegionNode(
    val name: String,
    val externalId: Long,
    val parentId: Long? = null,
    val children: List<RegionNode> = emptyList(),
    /**
     * 国家中文名(states 数据集中的 country_name 字段),只在国家级节点填充。
     * 用于按国家名直接反向匹配 state。
     */
    val cname: String? = null,
)

/**
 * 全球行政区划仓库
 *
 * 数据源:`https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json/`
 * - `countries.json` 一次性拉取(250 个国家)
 * - `states.json` 一次性拉取(5299 行,按 country_id 过滤)
 * - `cities.json` 一次性拉取(15 万行,按 state_id 过滤)
 *
 * **资源大小**:
 * - countries.json ~80 KB
 * - states.json ~700 KB
 * - cities.json ~15 MB → **不下载**,只下载 states.json,州以下不细分
 *
 * **缓存策略**:所有 JSON 拉取后整个 session 内复用,首次稍慢 (~1-3 秒 WiFi)。
 *
 * **数据源 License**:ODbL-1.0(署名)。本项目附文末注解:
 * `Data by Countries States Cities Database, ODbL v1.0, https://github.com/dr5hn/countries-states-cities-database`
 */
/**
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ...)` → Koin
 * `singleOf(::WorldRegionRepository)`。
 */
class WorldRegionRepository(
    private val context: android.content.Context,
) {
    private val cacheMutex = Mutex()
    @Volatile private var countriesCache: List<RegionNode>? = null
    @Volatile private var statesCache: List<RegionNode>? = null

    suspend fun loadCountries(): List<RegionNode> = withContext(Dispatchers.IO) {
        countriesCache?.let { return@withContext it }
        cacheMutex.withLock {
            countriesCache?.let { return@withLock it }
            val raw = downloadWithFallback(
                listOf(COUNTRIES_PRIMARY_URL, COUNTRIES_FALLBACK_URL),
                timeoutMs = 20_000,
            ) ?: error("无法下载国家列表(已尝试 $COUNTRIES_PRIMARY_URL 和 $COUNTRIES_FALLBACK_URL)")
            val parsed = parseCountries(JsonParser.parseString(raw).asJsonArray)
            countriesCache = parsed
            parsed
        }
    }

    suspend fun loadStatesByCountry(countryId: Long): List<RegionNode> = withContext(Dispatchers.IO) {
        ensureStatesLoaded()
        statesCache?.filter { it.parentId == countryId } ?: emptyList()
    }

    suspend fun loadStatesByCountryName(countryName: String): List<RegionNode> = withContext(Dispatchers.IO) {
        ensureStatesLoaded()
        statesCache?.filter { it.name == countryName || it.cname == countryName } ?: emptyList()
    }

    private suspend fun ensureStatesLoaded() {
        if (statesCache != null) return
        cacheMutex.withLock {
            if (statesCache != null) return
            val raw = downloadWithFallback(
                listOf(STATES_PRIMARY_URL, STATES_FALLBACK_URL),
                timeoutMs = 30_000,
            ) ?: error("无法下载州/省列表(已尝试 $STATES_PRIMARY_URL 和 $STATES_FALLBACK_URL)")
            val parsed = parseStates(JsonParser.parseString(raw).asJsonArray)
            statesCache = parsed
        }
    }

    private suspend fun downloadWithFallback(
        urls: List<String>,
        timeoutMs: Int,
    ): String? = withContext(Dispatchers.IO) {
        for (url in urls) {
            try {
                val result = HttpUtil.getResult(url, timeoutMs = timeoutMs)
                val body = when (result) {
                    is HttpResult.Success -> result.body
                    is HttpResult.Failure -> null
                }
                if (!body.isNullOrBlank()) {
                    Log.i(TAG, "downloadWithFallback success: $url (${body.length} chars)")
                    return@withContext body
                }
                val detail = when (result) {
                    is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
                    is HttpResult.Success -> "empty body"
                }
                Log.w(TAG, "downloadWithFallback failed: $url — $detail")
            } catch (e: Exception) {
                Log.w(TAG, "downloadWithFallback failed: $url (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        null
    }

    suspend fun invalidate() = cacheMutex.withLock {
        countriesCache = null
        statesCache = null
    }

    private fun parseCountries(arr: JsonArray): List<RegionNode> {
        return arr.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            val name = obj.get("name")?.asString
            if (name.isNullOrBlank()) return@mapNotNull null
            val zh = obj.getAsJsonObject("translations")?.get("zh")?.asString
                ?: obj.getAsJsonObject("translations")?.get("zh-CN")?.asString
                ?: name
            RegionNode(name = zh, externalId = id)
        }
    }

    private fun parseStates(arr: JsonArray): List<RegionNode> {
        return arr.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            val countryId = obj.get("country_id")?.asLong ?: return@mapNotNull null
            val name = obj.get("name")?.asString
            if (name.isNullOrBlank()) return@mapNotNull null
            val zh = obj.getAsJsonObject("translations")?.get("zh")?.asString
                ?: obj.getAsJsonObject("translations")?.get("zh-CN")?.asString
                ?: name
            val countryNameZh = obj.get("country_name")?.asString
            RegionNode(
                name = zh,
                externalId = id,
                parentId = countryId,
                cname = countryNameZh,
            )
        }
    }

    companion object {
        private const val PRIMARY_BASE = "https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json"
        private const val FALLBACK_BASE = "https://cdn.jsdelivr.net/gh/dr5hn/countries-states-cities-database@master/json"

        private const val COUNTRIES_PRIMARY_URL = "$PRIMARY_BASE/countries.json"
        private const val COUNTRIES_FALLBACK_URL = "$FALLBACK_BASE/countries.json"
        private const val STATES_PRIMARY_URL = "$PRIMARY_BASE/states.json"
        private const val STATES_FALLBACK_URL = "$FALLBACK_BASE/states.json"

        private const val TAG = "WorldRegionRepo"
    }
}
