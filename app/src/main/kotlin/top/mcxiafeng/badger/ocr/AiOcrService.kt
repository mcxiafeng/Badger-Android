package top.mcxiafeng.badger.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import java.io.ByteArrayOutputStream

/**
 * Compatibility shim around the old client-side AI OCR service. The real
 * work now happens server-side at `/v1/proxy/ai/tasks/contact_ocr`. This
 * shim maps the old `AiOcrService.recognizeImageWithFallback / fromText`
 * call shape onto [ServerApi.contactOcr] so the existing UI keeps
 * compiling without any change.
 *
 * [§14.2] 删除 Hilt EntryPoint + EntryPointAccessors —— Koin `object` 通过
 * `org.koin.core.context.GlobalContext.get()` 直接拿 [ServerApiFactory]。
 * `serverApiFactory` 是 Koin 中已注册的 `single { ServerApiFactory() }`,
 * 跨进程单例,与原 Hilt 行为完全一致。
 */
object AiOcrService {

    private const val TAG = "AiOcrService"

    /**
     * Stubbed: server-side health probe. With the Badger-Server migration
     * the LLM API key lives on the server; we always report success here
     * so the Settings page's "测试连接" button doesn't deadlock. The real
     * connectivity test lives in [ServerApi]'s underlying OkHttp client.
     */
    suspend fun testApi(@Suppress("UNUSED_PARAMETER") context: Context): Result<Unit> =
        Result.success(Unit)

    /**
     * Stubbed: model picking moved to Badger-Server. We return an empty
     * list so the UI's model-selection dialog stays empty until the
     * server-side flow lands.
     */
    suspend fun fetchModels(@Suppress("UNUSED_PARAMETER") context: Context): Result<List<ModelInfo>> =
        Result.success(emptyList())

    sealed class AiOcrServiceResult {
        data class Success(val data: ExtractedContact, val rawText: String?) : AiOcrServiceResult()
        data class Error(val message: String) : AiOcrServiceResult()
    }

    // [修复防御]: 改为走 ServerApiFactory —— 与全 app 共享同一个 ServerApi 实例。
    // 旧实现 new 了一个带默认 10.0.2.2:8080 的 ServerApi,既绕开热改 URL 的逻辑,
    // 也让用户配置的服务器地址对此路径无效。ServerApiFactory 在 Koin 中已注册为
    // `single { ServerApiFactory() }`,通过 `GlobalContext.get().get()` 拿到的就是
    // BadgerApplication.workManagerConfiguration.install 装入的那个实例。
    private fun api(context: Context): ServerApi =
        GlobalContext.get().get<ServerApiFactory>().get()

    fun recognizeImageWithFallback(
        @Suppress("UNUSED_PARAMETER") context: Context,
        bitmap: Bitmap,
    ): AiOcrServiceResult = try {
        val b64 = bitmapToBase64(bitmap)
        val resp = api(context).contactOcr(imageB64 = b64)
        AiOcrServiceResult.Success(
            data = resp,
            rawText = null,
        )
    } catch (e: Throwable) {
        Log.w(TAG, "recognizeImageWithFallback failed", e)
        AiOcrServiceResult.Error(e.message ?: "AI 服务调用失败")
    }

    fun recognizeFromTextWithFallback(
        @Suppress("UNUSED_PARAMETER") context: Context,
        text: String,
    ): AiOcrServiceResult = try {
        val resp = api(context).contactOcr(text = text)
        AiOcrServiceResult.Success(data = resp, rawText = text)
    } catch (e: Throwable) {
        Log.w(TAG, "recognizeFromTextWithFallback failed", e)
        AiOcrServiceResult.Error(e.message ?: "AI 服务调用失败")
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}

/**
 * Mirror of the server response (subset of [ServerApi.ExtractedContact]).
 * Re-declared here so call sites don't have to import the network type.
 */
typealias ExtractedContact = top.mcxiafeng.badger.network.ExtractedContact

/**
 * Convert the server's [ExtractedContact] into the UI's [ExtractedContactInfo]
 * shape. Mirrors what the deleted `AiOcrService.toExtractedContactInfo` did.
 */
fun ExtractedContact.toExtractedContactInfo(rawText: String?): ExtractedContactInfo {
    val platforms = mutableMapOf<String, String>()
    qq?.takeIf { it.isNotBlank() }?.let { platforms["qq"] = it }
    wechat?.takeIf { it.isNotBlank() }?.let { platforms["wechat"] = it }
    bilibili?.takeIf { it.isNotBlank() }?.let { platforms["bilibili"] = it }
    weibo?.takeIf { it.isNotBlank() }?.let { platforms["weibo"] = it }
    douyin?.takeIf { it.isNotBlank() }?.let { platforms["douyin"] = it }
    github?.takeIf { it.isNotBlank() }?.let { platforms["github"] = it }
    telegram?.takeIf { it.isNotBlank() }?.let { platforms["telegram"] = it }
    xiaohongshu?.takeIf { it.isNotBlank() }?.let { platforms["xiaohongshu"] = it }
    facebook?.takeIf { it.isNotBlank() }?.let { platforms["facebook"] = it }
    x?.takeIf { it.isNotBlank() }?.let { platforms["x"] = it }
    website?.takeIf { it.isNotBlank() }?.let { platforms["website"] = it }
    return ExtractedContactInfo(
        name = name,
        phone = phone,
        email = email,
        avatarUrl = avatarUrl,
        rawText = rawText ?: "",
        otherInfo = other,
        platforms = platforms,
    )
}