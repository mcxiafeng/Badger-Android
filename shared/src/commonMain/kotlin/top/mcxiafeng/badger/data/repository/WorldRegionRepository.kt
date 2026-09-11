package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.network.BadgerJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.contentOrNull
import top.mcxiafeng.badger.network.longOr
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.SafeLog
import top.mcxiafeng.badger.utils.KtorHttpCore

/** 行政区划节点。level 仅中国(高德 district 链路)有值：province/city/district，district 为叶子。 */
data class RegionNode(
    val name: String,
    val externalId: Long,
    val parentId: Long? = null,
    val cname: String? = null,
    val level: String? = null,
)

/**
 * 全球行政区划仓库。
 *
 * countries.json 与 states.json 在 session 内缓存，首次请求时使用主源 + CDN 备用源；
 * 中国省/市/区级联实时走高德 district 服务端代理（服务端精度封顶到区）。
 */
class WorldRegionRepository(private val serverApi: ServerApi) {
    // 国家和州/省数据彼此独立，使用独立锁避免一次慢下载阻塞另一类查询。
    private val countriesMutex = Mutex()
    private val statesMutex = Mutex()
    private val districtsMutex = Mutex()

    @kotlin.concurrent.Volatile
    private var countriesCache: List<RegionNode>? = null

    @kotlin.concurrent.Volatile
    private var statesCache: List<RegionNode>? = null

    suspend fun loadCountries(): List<RegionNode> = withContext(BadgerDispatchers.io) {
        countriesCache?.let { return@withContext it }
        countriesMutex.withLock {
            countriesCache?.let { return@withLock it }
            val raw = downloadWithFallback(
                listOf(COUNTRIES_PRIMARY_URL, COUNTRIES_FALLBACK_URL),
                timeoutMs = 20_000,
            ) ?: error("无法下载国家列表(已尝试 $COUNTRIES_PRIMARY_URL 和 $COUNTRIES_FALLBACK_URL)")
            val parsed = parseCountries(BadgerJson.parseToJsonElement(raw) as JsonArray)
            countriesCache = parsed
            parsed
        }
    }

    suspend fun loadStatesByCountry(countryId: Long): List<RegionNode> = withContext(BadgerDispatchers.io) {
        ensureStatesLoaded()
        statesCache?.filter { it.parentId == countryId } ?: emptyList()
    }

    suspend fun loadStatesByCountryName(countryName: String): List<RegionNode> = withContext(BadgerDispatchers.io) {
        ensureStatesLoaded()
        // contact 侧只有国家名没有 externalId：先按名查国家 externalId，再按 parentId 过滤州/省。
        // 旧实现拿国家名匹配州名（it.name==countryName）——州名不等于国家名，永远空，非中国国家全废。
        val countryId = countriesCache?.firstOrNull { it.name == countryName }?.externalId
            ?: loadCountries().firstOrNull { it.name == countryName }?.externalId
            ?: return@withContext emptyList()
        statesCache?.filter { it.parentId == countryId } ?: emptyList()
    }

    /**
     * 中国行政区划级联（高德 district 服务端代理）：[adcode] 节点的下一级子区划。
     * null = 中国省级列表（根）。服务端精度封顶到区：level=district 即叶子，无街道级。
     * 子区划按 adcode 会话内缓存（级联来回下钻/返回不重复请求）。
     */
    suspend fun loadChinaDistricts(adcode: String?): List<RegionNode> = withContext(BadgerDispatchers.io) {
        val cacheKey = adcode ?: ROOT_KEY
        districtsMutex.withLock {
            districtCache[cacheKey]?.let { return@withLock it }
            try {
                val page = serverApi.amapDistrict(adcode)
                val nodes = page.districts.mapNotNull { d ->
                    if (d.name.isBlank()) return@mapNotNull null
                    val code = d.adcode.toLongOrNull() ?: return@mapNotNull null
                    RegionNode(name = d.name, externalId = code, level = d.level)
                }
                BadgerLog.d(TAG, "loadChinaDistricts adcode=${adcode ?: "-"} size=${nodes.size}")
                districtCache[cacheKey] = nodes
                nodes
            } catch (e: Exception) {
                BadgerLog.e(TAG, "loadChinaDistricts failed adcode=${adcode ?: "-"}", e)
                throw e
            }
        }
    }

    private suspend fun ensureStatesLoaded() {
        if (statesCache != null) return
        statesMutex.withLock {
            if (statesCache != null) return
            val raw = downloadWithFallback(
                listOf(STATES_PRIMARY_URL, STATES_FALLBACK_URL),
                timeoutMs = 30_000,
            ) ?: error("无法下载州/省列表(已尝试 $STATES_PRIMARY_URL 和 $STATES_FALLBACK_URL)")
            statesCache = parseStates(BadgerJson.parseToJsonElement(raw) as JsonArray)
        }
    }

    private val http = KtorHttpCore()

    /** 中国区划缓存:adcode(根用哨兵) → 下一级节点。 */
    private val districtCache = mutableMapOf<String, List<RegionNode>>()

    private suspend fun downloadWithFallback(
        urls: List<String>,
        timeoutMs: Int,
    ): String? = withContext(BadgerDispatchers.io) {
        for (url in urls) {
            try {
                val result = http.get(url, timeoutMs = timeoutMs.toLong())
                val body = when (result) {
                    is HttpResult.Success -> result.body
                    is HttpResult.Failure -> null
                }
                if (!body.isNullOrBlank()) {
                    BadgerLog.i(TAG, "downloadWithFallback success: ${SafeLog.url(url)} (${body.length} chars)")
                    return@withContext body
                }
                val detail = when (result) {
                    is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
                    is HttpResult.Success -> "empty body"
                }
                BadgerLog.w(TAG, "downloadWithFallback failed: ${SafeLog.url(url)} — $detail")
            } catch (e: Exception) {
                BadgerLog.w(TAG, "downloadWithFallback failed: ${SafeLog.url(url)} (${e::class.simpleName}: ${e.message})")
            }
        }
        null
    }

    suspend fun invalidate() {
        countriesMutex.withLock {
            countriesCache = null
        }
        statesMutex.withLock {
            statesCache = null
        }
        districtsMutex.withLock {
            districtCache.clear()
        }
    }

    private fun parseCountries(arr: JsonArray): List<RegionNode> =
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = longOr(obj["id"], 0L)
            if (id <= 0L) return@mapNotNull null
            val name = obj["name"].contentOrNull()
            if (name.isNullOrBlank()) return@mapNotNull null
            val translations = obj["translations"] as? JsonObject
            val zh = translations?.get("zh").contentOrNull()
                ?: translations?.get("zh-CN").contentOrNull()
                ?: name
            // cname 暂存英文原名：国家选择器搜索/次级排序用
            RegionNode(name = zh, externalId = id, cname = name)
        }

    private fun parseStates(arr: JsonArray): List<RegionNode> =
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = longOr(obj["id"], 0L)
            val countryId = longOr(obj["country_id"], 0L)
            if (id <= 0L || countryId <= 0L) return@mapNotNull null
            val name = obj["name"].contentOrNull()
            if (name.isNullOrBlank()) return@mapNotNull null
            val translations = obj["translations"] as? JsonObject
            val zh = translations?.get("zh").contentOrNull()
                ?: translations?.get("zh-CN").contentOrNull()
                ?: name
            RegionNode(
                name = zh,
                externalId = id,
                parentId = countryId,
                cname = obj["country_name"].contentOrNull(),
            )
        }

    companion object {
        private const val PRIMARY_BASE = "https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json"
        private const val FALLBACK_BASE = "https://cdn.jsdelivr.net/gh/dr5hn/countries-states-cities-database@master/json"
        private const val COUNTRIES_PRIMARY_URL = "$PRIMARY_BASE/countries.json"
        private const val COUNTRIES_FALLBACK_URL = "$FALLBACK_BASE/countries.json"
        private const val STATES_PRIMARY_URL = "$PRIMARY_BASE/states.json"
        private const val STATES_FALLBACK_URL = "$FALLBACK_BASE/states.json"

        /** 高德 district 根查询（中国省级列表）在缓存中的哨兵键。 */
        private const val ROOT_KEY = "<root>"
        private const val TAG = "WorldRegionRepo"
    }
}
