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
import top.mcxiafeng.badger.utils.SafeLog

/** 行政区划节点。 */
data class RegionNode(
    val name: String,
    val externalId: Long,
    val parentId: Long? = null,
    val children: List<RegionNode> = emptyList(),
    val cname: String? = null,
)

/**
 * 全球行政区划仓库。
 *
 * countries.json 与 states.json 在 session 内缓存，首次请求时使用主源 + CDN 备用源。
 */
class WorldRegionRepository {
    private val cacheMutex = Mutex()

    @Volatile
    private var countriesCache: List<RegionNode>? = null

    @Volatile
    private var statesCache: List<RegionNode>? = null

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
            statesCache = parseStates(JsonParser.parseString(raw).asJsonArray)
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
                    Log.i(TAG, "downloadWithFallback success: ${SafeLog.url(url)} (${body.length} chars)")
                    return@withContext body
                }
                val detail = when (result) {
                    is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
                    is HttpResult.Success -> "empty body"
                }
                Log.w(TAG, "downloadWithFallback failed: ${SafeLog.url(url)} — $detail")
            } catch (e: Exception) {
                Log.w(TAG, "downloadWithFallback failed: ${SafeLog.url(url)} (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        null
    }

    suspend fun invalidate() = cacheMutex.withLock {
        countriesCache = null
        statesCache = null
    }

    private fun parseCountries(arr: JsonArray): List<RegionNode> =
        arr.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            val name = obj.get("name")?.asString
            if (name.isNullOrBlank()) return@mapNotNull null
            val zh = obj.getAsJsonObject("translations")?.get("zh")?.asString
                ?: obj.getAsJsonObject("translations")?.get("zh-CN")?.asString
                ?: name
            RegionNode(name = zh, externalId = id)
        }

    private fun parseStates(arr: JsonArray): List<RegionNode> =
        arr.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            val countryId = obj.get("country_id")?.asLong ?: return@mapNotNull null
            val name = obj.get("name")?.asString
            if (name.isNullOrBlank()) return@mapNotNull null
            val zh = obj.getAsJsonObject("translations")?.get("zh")?.asString
                ?: obj.getAsJsonObject("translations")?.get("zh-CN")?.asString
                ?: name
            RegionNode(
                name = zh,
                externalId = id,
                parentId = countryId,
                cname = obj.get("country_name")?.asString,
            )
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
