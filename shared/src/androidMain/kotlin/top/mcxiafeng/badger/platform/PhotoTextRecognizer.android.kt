package top.mcxiafeng.badger.platform

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "PhotoTextRecognizer"

/**
 * [KMP K10] 照片文字识别引擎 Android actual：ML Kit 中文识别。
 *
 * - 识别器实例级别复用（每帧/每图新建开销 50-100ms），页面级持有 + [close] 释放。
 * - 异步 API 经 suspendCancellableCoroutine 包装，禁止 Tasks.await() 阻塞调用线程。
 *
 * 逻辑自原 ScannerCamera.detectTextBlocksFromBitmap / ScannerComponents.recognizeTextFromBitmap
 * 原样迁移（后者的每次新建+close 改为实例复用，行为等价且更省）。
 */
actual class PhotoTextRecognizer {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 整图 OCR：返回拼接后的全部文字（原 ScannerComponents.recognizeTextFromBitmap）。
     */
    actual suspend fun recognizeText(image: PlatformImage): String =
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromBitmap(image.bitmap, 0)
                val visionText = suspendCancellableCoroutine { cont ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            cont.resume(result) {}
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "ML Kit OCR 失败: ${e.message}", e)
                            cont.resume(null) {}
                        }
                }
                visionText?.text ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit OCR 初始化失败: ${e.message}", e)
                ""
            }
        }

    /**
     * 仅取文字块包围框（像素空间），不取文字内容
     * （原 ScannerCamera.detectTextBlocksFromBitmap）。
     */
    actual suspend fun detectTextBlocks(image: PlatformImage): List<TextBlockBox> {
        return try {
            val inputImage = InputImage.fromBitmap(image.bitmap, 0)
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        cont.resume(result)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit 文字区域检测失败", e)
                        cont.resume(null)
                    }
            }
            visionText?.textBlocks?.mapNotNull { block ->
                val rect = block.boundingBox ?: return@mapNotNull null
                TextBlockBox(
                    corners = listOf(
                        QrPoint(rect.left.toFloat(), rect.top.toFloat()),
                        QrPoint(rect.right.toFloat(), rect.top.toFloat()),
                        QrPoint(rect.right.toFloat(), rect.bottom.toFloat()),
                        QrPoint(rect.left.toFloat(), rect.bottom.toFloat())
                    )
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "文字区域检测异常", e)
            emptyList()
        }
    }

    actual fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "close TextRecognizer 失败", e)
        }
    }
}
