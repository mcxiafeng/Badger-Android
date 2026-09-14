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

actual class PhotoTextRecognizer {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    

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
