package top.mcxiafeng.badger.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import java.io.ByteArrayOutputStream

/**
 * AI OCR 服务封装。实际工作由服务端 `/api/proxy/ai/tasks/contact_ocr` 完成。
 *
 * 提供类型安全的错误处理（[AiOcrServiceResult] sealed class）和 bitmap→base64 转换。
 */
object AiOcrService {

    private const val TAG = "AiOcrService"

    sealed class AiOcrServiceResult {
        data class Success(val data: ExtractedContact, val rawText: String?) : AiOcrServiceResult()
        data class Error(val message: String) : AiOcrServiceResult()
    }

    private fun api() = GlobalContext.get().get<ServerApiFactory>().get()

    fun recognizeImageWithFallback(bitmap: Bitmap): AiOcrServiceResult = try {
        val b64 = bitmapToBase64(bitmap)
        val resp = api().contactOcr(imageB64 = b64)
        AiOcrServiceResult.Success(data = resp, rawText = null)
    } catch (e: Throwable) {
        Log.w(TAG, "recognizeImageWithFallback failed", e)
        AiOcrServiceResult.Error(e.message ?: "AI 服务调用失败")
    }

    fun recognizeFromTextWithFallback(text: String): AiOcrServiceResult = try {
        val resp = api().contactOcr(text = text)
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
 * Mirror of the server response (subset of [ExtractedContact]).
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