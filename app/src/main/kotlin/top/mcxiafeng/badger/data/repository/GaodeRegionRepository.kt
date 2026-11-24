package top.mcxiafeng.badger.data.repository

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import top.mcxiafeng.badger.utils.CountryCatalog
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 街道没有独立 adcode，使用父区县的编码。 */
data class GaodeDistrict(val name: String, val adcode: String, val level: String) {
    internal val pathKey: String get() = "$adcode:$name"
}

data class GaodeQueryResult(val top: GaodeDistrict?, val children: List<GaodeDistrict>)

@Singleton
class GaodeRegionRepository @Inject constructor(
    private val config: GaodeConfig,
    client: OkHttpClient,
) {
    // Key 是高德要求的查询参数，禁用重定向以防止凭据被带到其他地址。
    private val gaodeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun queryCountry(country: String): GaodeQueryResult {
        if (country !in CountryCatalog.ALL) {
            throw GaodeException("该国家或地区可手动填写")
        }
        val domesticCode = CountryCatalog.domesticAdcode(country)
        return request(domesticCode ?: country, global = domesticCode == null).toQueryResult().also {
            requireCountryRoot(it.top, country, domesticCode)
            Log.d(TAG, "已收到国家行政区划，下级数量=${it.children.size}")
        }
    }

    /** 旧地区只有名称。拉取公开区划目录，在本机精确匹配完整路径。 */
    internal suspend fun findSavedPath(country: String, region: String): List<GaodeQueryResult> = withContext(Dispatchers.Default) {
        if (country !in CountryCatalog.ALL) return@withContext emptyList()
        val domesticCode = CountryCatalog.domesticAdcode(country)
        val root = request(domesticCode ?: country, global = domesticCode == null, depth = SAVED_PATH_DEPTH)
            ?: return@withContext emptyList()
        val top = requireCountryRoot(root.toDistrictOrNull(), country, domesticCode)
        val matches = mutableListOf<List<GaodeQueryResult>>()
        collectMatchingPaths(root, region, listOf(root.toQueryResult()), top.adcode.length, matches)
        Log.d(TAG, "旧地区路径匹配数=${matches.size}")
        matches.singleOrNull().orEmpty()
    }

    private fun collectMatchingPaths(
        node: JsonObject,
        name: String,
        path: List<GaodeQueryResult>,
        codeLength: Int,
        matches: MutableList<List<GaodeQueryResult>>,
    ) {
        for (child in node.districtObjects()) {
            val result = child.toQueryResult()
            val district = result.top ?: continue
            if (district.adcode.length != codeLength) continue
            val next = path + result
            if (district.name == name || next.drop(1).joinToString("") { it.top?.name.orEmpty() } == name) {
                matches.add(next)
            }
            if (matches.size > 1) return
            if (next.size <= SAVED_PATH_DEPTH) collectMatchingPaths(child, name, next, codeLength, matches)
            if (matches.size > 1) return
        }
    }

    private fun requireCountryRoot(top: GaodeDistrict?, country: String, domesticCode: String?): GaodeDistrict {
        top ?: throw GaodeException(COUNTRY_NOT_FOUND)
        val matches = if (domesticCode != null) {
            top.adcode == domesticCode
        } else {
            top.level == "country" && top.name == country && top.adcode.length == GLOBAL_ADCODE_LENGTH
        }
        if (!matches) throw GaodeException(COUNTRY_NOT_FOUND)
        return top
    }

    suspend fun queryChildren(district: GaodeDistrict): List<GaodeDistrict> {
        if (district.level == STREET_LEVEL) return emptyList()
        if (!ADCODE.matches(district.adcode)) throw GaodeException(FORMAT_ERROR)
        val global = district.adcode.length == GLOBAL_ADCODE_LENGTH
        val result = request(district.adcode, global).toQueryResult()
        if (result.top?.adcode != district.adcode) {
            Log.w(TAG, "下级查询返回的区划编码与请求不符")
            throw GaodeException("高德没有返回所选地区的区划，请重试或手动填写")
        }
        return result.children.also { Log.d(TAG, "已收到下级行政区划，数量=${it.size}") }
    }

    private suspend fun request(keywords: String, global: Boolean, depth: Int = 1): JsonObject? = withContext(Dispatchers.IO) {
        val key = config.getApiKey()
        if (key.isBlank()) throw GaodeException("请先在设置中配置高德 Key，或手动填写地区")
        val endpoint = if (global) GLOBAL_DISTRICT_URL else DISTRICT_URL
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("keywords", keywords)
            .addQueryParameter("subdistrict", depth.toString())
            .apply { if (global) addQueryParameter("langCode", "zh") }
            .build()
        Log.d(TAG, "开始查询行政区划，海外接口=$global")
        try {
            gaodeClient.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "行政区划 HTTP 请求失败，状态=${response.code}")
                    throw GaodeException("高德服务暂时不可用，请重试或手动填写地区")
                }
                parseResponseRoot(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            ensureActive()
            // IO 异常正文、URL 和响应体都可能包含原始 Key。
            Log.w(TAG, "连接高德服务失败，类型=${e.javaClass.simpleName}")
            throw GaodeException("无法连接高德，请检查网络后重试")
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    internal fun parseDistricts(body: String): GaodeQueryResult = parseResponseRoot(body).toQueryResult()

    private fun parseResponseRoot(body: String): JsonObject? {
        val root = try {
            JsonParser.parseString(body) as? JsonObject
        } catch (e: JsonParseException) {
            Log.w(TAG, "高德响应无法解析，类型=${e.javaClass.simpleName}")
            throw GaodeException(FORMAT_ERROR)
        } ?: throw GaodeException(FORMAT_ERROR)
        val status = root.get("status").readPrimitive() ?: throw GaodeException(FORMAT_ERROR)
        if (status != STATUS_SUCCESS) throw serviceError(root.get("infocode").readPrimitive())
        val first = root.districtArray()?.firstOrNull() ?: return null
        return first as? JsonObject ?: throw GaodeException(FORMAT_ERROR)
    }

    private fun JsonObject.districtArray(): JsonArray? {
        val districts = get("districts") ?: return null
        if (districts.isJsonNull) return null
        return districts as? JsonArray ?: throw GaodeException(FORMAT_ERROR)
    }

    private fun JsonObject.districtObjects(): List<JsonObject> =
        districtArray()?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject?.toQueryResult(): GaodeQueryResult {
        if (this == null) return GaodeQueryResult(null, emptyList())
        val top = toDistrictOrNull() ?: throw GaodeException(FORMAT_ERROR)
        val children = districtObjects().mapNotNull { it.toDistrictOrNull() }
            .filter { it.adcode.length == top.adcode.length }
            .distinctBy { it.pathKey }
        return GaodeQueryResult(top, children)
    }

    private fun serviceError(code: String?): GaodeException {
        val safeCode = code?.takeIf(ERROR_CODE::matches)
        Log.w(TAG, "高德行政区划接口返回业务错误，infocode=${safeCode ?: "unknown"}")
        val message = when (safeCode) {
            KEY_PLATFORM_MISMATCH -> "请使用高德 Web 服务类型的 Key"
            "10001", "10002", "10005", "10007" -> "高德 Key 无效或没有该服务的权限，请检查配置"
            "10003", "10004" -> "高德查询已达到服务限额，请稍后重试或手动填写地区"
            else -> "高德查询失败，请重试或手动填写地区"
        }
        return GaodeException(message)
    }

    private fun JsonElement?.readPrimitive(): String? = this?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.toDistrictOrNull(): GaodeDistrict? {
        val name = get("name").readPrimitive()?.takeIf { it.isNotBlank() && '\n' !in it && '\r' !in it } ?: return null
        val level = get("level").readPrimitive() ?: return null
        val adcode = get("adcode").readPrimitive()?.takeIf { ADCODE.matches(it) } ?: return null
        return GaodeDistrict(name, adcode, level)
    }

    companion object {
        private const val DISTRICT_URL = "https://restapi.amap.com/v3/config/district"
        private const val GLOBAL_DISTRICT_URL = "https://sg-restapi.opnavi.com/v5/district/global"
        private const val GLOBAL_ADCODE_LENGTH = 9
        private const val SAVED_PATH_DEPTH = 3
        private const val KEY_PLATFORM_MISMATCH = "10009"
        private const val STATUS_SUCCESS = "1"
        private const val STREET_LEVEL = "street"
        private const val QUERY_TIMEOUT_SECONDS = 15L
        private const val COUNTRY_NOT_FOUND = "高德没有返回该国家或地区的区划，请手动填写"
        private const val FORMAT_ERROR = "高德返回数据格式异常，请稍后重试或手动填写地区"
        private const val TAG = "GaodeRegionRepositoryTester"
        private val ERROR_CODE = Regex("[0-9]{5}")
        private val ADCODE = Regex("[0-9]{6}([0-9]{3})?")
    }
}
