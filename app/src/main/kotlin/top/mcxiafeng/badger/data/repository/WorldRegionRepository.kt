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
        // [修复防御]:用户报告 jsDelivr CDN 中国跨境连接超时,改回 GitHub raw(主源)
        // + jsDelivr 作为 fallback。两个源任一能拉就行,加载空指针抛异常给 UI 处理。
        val raw = downloadWithFallback(
            listOf(COUNTRIES_PRIMARY_URL, COUNTRIES_FALLBACK_URL),
            timeoutMs = 20_000,
        ) ?: error("无法下载国家列表(已尝试 $COUNTRIES_PRIMARY_URL 和 $COUNTRIES_FALLBACK_URL)")
        val parsed = parseCountries(JsonParser.parseString(raw).asJsonArray)
        cacheMutex.withLock { countriesCache = parsed }
        parsed
    }

    /**
     * 加载某国家的州/省列表。
     *
     * 整个 states.json (~700KB) 在首次调用时一次性下载并解析,后续命中 cache。
     */
    suspend fun loadStatesByCountry(countryId: Long): List<RegionNode> = withContext(Dispatchers.IO) {
        ensureStatesLoaded()
        statesCache?.filter { it.parentId == countryId } ?: emptyList()
    }

    /**
     * 用国家中文名加载州/省列表。
     *
     * **一次网络** 拉 states.json 缓存,直接按 country_name 过滤。
     * 不需要先拉 countries 找 id——避免双网络串行任一失败导致 UI 崩。
     */
    suspend fun loadStatesByCountryName(countryName: String): List<RegionNode> = withContext(Dispatchers.IO) {
        ensureStatesLoaded()
        // states.json 里有 country_name 字段(中英文),用中文名匹配
        statesCache?.filter { it.name == countryName || it.cname == countryName } ?: emptyList()
    }

    /**
     * 保证 states 已加载。**只拉一次**;失败抛异常。
     */
    private suspend fun ensureStatesLoaded() {
        if (statesCache != null) return
        // [修复防御]:主源 GitHub raw + fallback jsDelivr
        val raw = downloadWithFallback(
            listOf(STATES_PRIMARY_URL, STATES_FALLBACK_URL),
            timeoutMs = 30_000,
        ) ?: error("无法下载州/省列表(已尝试 $STATES_PRIMARY_URL 和 $STATES_FALLBACK_URL)")
        val parsed = parseStates(JsonParser.parseString(raw).asJsonArray)
        cacheMutex.withLock { statesCache = parsed }
    }

    /**
     * 顺序尝试多个 URL,任一返回非空 body 即返回。
     *
     * **[修复防御](PR2 修复 #3)**:主源 GitHub raw 经常被墙(中国大陆),jsDelivr 备用 CDN
     * 偶尔也连不上,任一能拉就能用。本方法不抛异常、超时即跳下一个,全部失败才返回 null。
     */
    private suspend fun downloadWithFallback(
        urls: List<String>,
        timeoutMs: Int,
    ): String? = withContext(Dispatchers.IO) {
        for (url in urls) {
            try {
                val result = HttpUtil.getResult(url, timeoutMs = timeoutMs)
                val body = result.bodyOrNull()
                if (!body.isNullOrBlank()) {
                    Log.i(TAG, "downloadWithFallback success: $url (${body.length} chars)")
                    return@withContext body
                } else {
                    val detail = when (result) {
                        is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
                        else -> "empty body"
                    }
                    Log.w(TAG, "downloadWithFallback failed: $url — $detail")
                }
            } catch (e: Exception) {
                Log.w(TAG, "downloadWithFallback failed: $url (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        null
    }

    /** 清缓存(预留调试用) */
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
            // 中文名(translations.zh-CN 经常缺失,fallback 到 name)
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
            // 同步存 country_name:虽然只在国家级用,但存这里便于跨表查找
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
        // [修复防御](PR2 修复 #3):用户反馈 jsDelivr CDN 中国跨境连接失败,
        // 改用主源 GitHub raw + jsDelivr 备用的双源策略。任一源能拉即可。
        private const val PRIMARY_BASE = "https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json"
        private const val FALLBACK_BASE = "https://cdn.jsdelivr.net/gh/dr5hn/countries-states-cities-database@master/json"

        private const val COUNTRIES_PRIMARY_URL = "$PRIMARY_BASE/countries.json"
        private const val COUNTRIES_FALLBACK_URL = "$FALLBACK_BASE/countries.json"
        private const val STATES_PRIMARY_URL = "$PRIMARY_BASE/states.json"
        private const val STATES_FALLBACK_URL = "$FALLBACK_BASE/states.json"

        private const val TAG = "WorldRegionRepo"
    }
}
