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
data class GaodeDistrict(val name: String, val adcode: String, val level: String)

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
        val result = request(domesticCode ?: country, global = domesticCode == null)
        val top = result.top ?: throw GaodeException(COUNTRY_NOT_FOUND)
        val matches = if (domesticCode != null) {
            top.adcode == domesticCode
        } else {
            top.level == "country" && top.name == country && top.adcode.length == GLOBAL_ADCODE_LENGTH
        }
        if (!matches) throw GaodeException(COUNTRY_NOT_FOUND)
        return result.copy(children = result.children.filter { it.adcode.length == top.adcode.length })
    }

    suspend fun queryChildren(district: GaodeDistrict): List<GaodeDistrict> {
        if (district.level == STREET_LEVEL) return emptyList()
        if (!ADCODE.matches(district.adcode)) throw GaodeException(FORMAT_ERROR)
        val global = district.adcode.length == GLOBAL_ADCODE_LENGTH
        val result = request(district.adcode, global)
        if (result.top?.adcode != district.adcode) {
            Log.w(TAG, "下级查询返回的区划编码与请求不符")
            throw GaodeException("高德没有返回所选地区的区划，请重试或手动填写")
        }
        return result.children.filter { it.adcode.length == district.adcode.length }
    }

    private suspend fun request(keywords: String, global: Boolean): GaodeQueryResult = withContext(Dispatchers.IO) {
        val key = config.getApiKey()
        if (key.isBlank()) throw GaodeException("请先在设置中配置高德 Key，或手动填写地区")
        val endpoint = if (global) GLOBAL_DISTRICT_URL else DISTRICT_URL
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("keywords", keywords)
            .addQueryParameter("subdistrict", "1")
            .apply { if (global) addQueryParameter("langCode", "zh") }
            .build()
        Log.d(TAG, "开始查询行政区划，海外接口=$global")
        try {
            gaodeClient.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "行政区划 HTTP 请求失败，状态=${response.code}")
                    throw GaodeException("高德服务暂时不可用，请重试或手动填写地区")
                }
                parseDistricts(response.body?.string().orEmpty())
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

    internal fun parseDistricts(body: String): GaodeQueryResult {
        val root = try {
            JsonParser.parseString(body) as? JsonObject
        } catch (e: JsonParseException) {
            Log.w(TAG, "高德响应无法解析，类型=${e.javaClass.simpleName}")
            throw GaodeException(FORMAT_ERROR)
        } ?: throw GaodeException(FORMAT_ERROR)
        val status = root.get("status").readPrimitive() ?: throw GaodeException(FORMAT_ERROR)
        if (status != STATUS_SUCCESS) throw serviceError(root.get("infocode").readPrimitive())
        val districts = root.get("districts")
        if (districts == null || districts.isJsonNull) return GaodeQueryResult(null, emptyList())
        val nodes = districts as? JsonArray ?: throw GaodeException(FORMAT_ERROR)
        val firstNode = nodes.firstOrNull() ?: return GaodeQueryResult(null, emptyList())
        val topObject = firstNode as? JsonObject ?: throw GaodeException(FORMAT_ERROR)
        val top = topObject.toDistrictOrNull() ?: throw GaodeException(FORMAT_ERROR)
        val children = (topObject.get("districts") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.toDistrictOrNull() }
            ?.distinctBy { it.adcode to it.name }
            .orEmpty()
        Log.d(TAG, "已收到行政区划，下级数量=${children.size}")
        return GaodeQueryResult(top, children)
    }

    private fun serviceError(code: String?): GaodeException {
        Log.w(TAG, "高德行政区划接口返回业务错误")
        val message = when (code) {
            "10001", "10002", "10005", "10007" -> "高德 Key 无效或没有该服务的权限，请检查配置"
            "10003", "10004" -> "高德查询已达到服务限额，请稍后重试或手动填写地区"
            else -> "高德查询失败，请重试或手动填写地区"
        }
        return GaodeException(message)
    }

    private fun JsonElement?.readPrimitive(): String? = this?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.toDistrictOrNull(): GaodeDistrict? {
        val name = get("name").readPrimitive()?.takeIf { it.isNotBlank() } ?: return null
        val level = get("level").readPrimitive() ?: return null
        val adcode = get("adcode").readPrimitive()?.takeIf { ADCODE.matches(it) } ?: return null
        return GaodeDistrict(name, adcode, level)
    }

    companion object {
        private const val DISTRICT_URL = "https://restapi.amap.com/v3/config/district"
        private const val GLOBAL_DISTRICT_URL = "https://sg-restapi.opnavi.com/v5/district/global"
        private const val GLOBAL_ADCODE_LENGTH = 9
        private const val STATUS_SUCCESS = "1"
        private const val STREET_LEVEL = "street"
        private const val QUERY_TIMEOUT_SECONDS = 15L
        private const val COUNTRY_NOT_FOUND = "高德没有返回该国家或地区的区划，请手动填写"
        private const val FORMAT_ERROR = "高德返回数据格式异常，请稍后重试或手动填写地区"
        private const val TAG = "GaodeRegionRepositoryTester"
        private val ADCODE = Regex("[0-9]{6}([0-9]{3})?")
    }
}
