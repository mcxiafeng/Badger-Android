package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.AiOcrService
import top.mcxiafeng.badger.ocr.AiOcrServiceResult
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text

/**
 * 扫描页面控件覆盖层：顶部模式切换栏 + 返回按钮 + 手动输入 + 底部控制栏。
 * 所有控件均使用 BoxScope.align() 定位在相机预览上方。
 */
@Composable
internal fun BoxScope.ScannerControls(
    selectedMode: Int,
    isFlashOn: Boolean,
    showResultDialog: Boolean,
    animatedSwipe: Float,
    qrDetectionState: QrDetectionState,
    aiOcrEnabled: Boolean,
    onBack: () -> Unit,
    onNavigateToCreateContact: () -> Unit,
    onModeClick: (Int) -> Unit,
    onFlashToggle: () -> Unit,
    onCaptureClick: () -> Unit,
    onPhotoPickerClick: () -> Unit,
) {
    val context = LocalContext.current

    // ========== 顶部模式切换栏 ==========
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .background(Color.Black.copy(alpha = 0.5f))
            .statusBarsPadding()
            .padding(vertical = 8.dp, horizontal = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SwipeableModeTab(
            indicatorFraction = (selectedMode.toFloat() - animatedSwipe.coerceIn(-1f, 1f)).coerceIn(0f, 1f),
            onModeClick = onModeClick
        )
    }

    // 返回按钮
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp),
        backgroundColor = Color.White.copy(alpha = 0.2f)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "返回",
            tint = Color.White
        )
    }

    // 手动输入按钮
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 16.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable {
                Log.d("Tester", "手动输入按钮点击")
                onNavigateToCreateContact()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "手动输入",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }

    // ========== 底部控制栏：闪光灯 / 确认 / 相册 ==========
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onFlashToggle,
            enabled = !showResultDialog,
            backgroundColor = Color.White.copy(alpha = 0.2f)
        ) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "闪光灯",
                tint = Color.White
            )
        }

        if (selectedMode == 0) {
            // 多码模式：确认按钮
            val hasAccumulated = qrDetectionState.accumulatedContents.isNotEmpty()
            val hasTextBlocks = aiOcrEnabled && qrDetectionState.textBlockCount > 0
            val canCollect = hasAccumulated || hasTextBlocks
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (canCollect) Color.White else Color.White.copy(alpha = 0.5f))
                    .clickable(enabled = !showResultDialog && canCollect) { onCaptureClick() },
                contentAlignment = Alignment.Center
            ) { /* 纯白圆形按钮，无数字 */ }
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "扫描",
                    tint = Color.White
                )
            }
        }

        IconButton(
            onClick = onPhotoPickerClick,
            enabled = !showResultDialog,
            backgroundColor = Color.White.copy(alpha = 0.2f)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = "相册",
                tint = Color.White
            )
        }
    }
}

/**
 * 扫描覆盖层：根据当前模式切换扫码线框或多码动态框选。
 */
@Composable
internal fun BoxScope.ScannerOverlays(
    selectedMode: Int,
    qrDetectionState: QrDetectionState,
    aiOcrEnabled: Boolean,
) {
    if (selectedMode == 1) {
        val scanOverlayAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250),
            label = "scan_overlay_alpha"
        )
        ScanLineOverlay(modifier = Modifier.graphicsLayer { alpha = scanOverlayAlpha })
    } else {
        MultiQrScanOverlay(
            boundingBoxes = qrDetectionState.visibleBoundingBoxes,
            accumulatedCount = qrDetectionState.accumulatedContents.size,
            textBoundingBoxes = qrDetectionState.visibleTextBoundingBoxes,
            textBlockCount = qrDetectionState.textBlockCount,
            aiOcrEnabled = aiOcrEnabled
        )
    }
}

// ========== 图片处理辅助函数 ==========

/**
 * 处理拍照/相册图片：二维码检测 + ML Kit OCR + AI 解析
 */
internal suspend fun processPhotoBitmap(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    aiOcrEnabled: Boolean,
    onResult: (List<String>, ExtractedContactInfo?, String?) -> Unit
) {
    try {
        Log.d("Tester", "processPhotoBitmap: 开始处理, bitmap=${bitmap.width}x${bitmap.height}, aiOcrEnabled=$aiOcrEnabled")

        if (!aiOcrEnabled) {
            val detectedQrCodes = withContext(Dispatchers.IO) { detectQrCodesFromBitmap(context, bitmap) }
            Log.d("Tester", "processPhotoBitmap: AI未启用，二维码检测完成, 数量=${detectedQrCodes.size}")
            withContext(Dispatchers.Main) { onResult(detectedQrCodes, null, null) }
            return
        }

        val (detectedQrCodes, ocrText) = withContext(Dispatchers.IO) {
            val qrBounds = detectQrCodesWithBounds(bitmap)
            val codes = qrBounds.map { it.content }
            Log.d("Tester", "processPhotoBitmap: 二维码检测完成, 数量=${codes.size}, 内容=${codes.map { it.take(50) }}")

            val maskedBitmap = maskQrRegions(bitmap, qrBounds)
            val needRecycleMasked = maskedBitmap !== bitmap
            val text = recognizeTextFromBitmap(maskedBitmap)
            if (needRecycleMasked) maskedBitmap.recycle()
            Log.d("Tester", "processPhotoBitmap: ML Kit OCR 完成(已遮盖QR区域), 文字长度=${text.length}, 前200字=${text.take(200)}")
            codes to text
        }

        val hasVision = AiOcrConfig.hasVisionModel(context)
        val supportsVision = AiOcrConfig.supportsVision(context)
        val model = AiOcrConfig.getModel(context)
        Log.d("Tester", "processPhotoBitmap: AI已启用, hasVision=$hasVision, supportsVision=$supportsVision, model=$model")

        val aiResult = if (hasVision) {
            Log.d("Tester", "processPhotoBitmap: 使用 Vision 模式（发图片到AI）")
            AiOcrService.recognizeImageWithFallback(context, bitmap)
        } else {
            if (ocrText.isNotBlank()) {
                Log.d("Tester", "processPhotoBitmap: 使用纯文本模式（发OCR文字给AI）")
                AiOcrService.recognizeFromTextWithFallback(context, ocrText)
            } else {
                Log.w("Tester", "processPhotoBitmap: 纯文本模式但OCR文字为空，跳过AI")
                AiOcrServiceResult.Error("未识别到文字")
            }
        }
        Log.d("Tester", "processPhotoBitmap: AI结果类型=${aiResult.javaClass.simpleName}")
        withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                    Log.d("Tester", "processPhotoBitmap: AI成功, name=${info.name}, phone=${info.phone}, email=${info.email}, platforms=${info.platforms}, otherInfo=${info.otherInfo}")
                    onResult(detectedQrCodes, info, null)
                }
                is AiOcrServiceResult.Error -> {
                    Log.e("Tester", "processPhotoBitmap: AI失败, error=${aiResult.message}")
                    onResult(detectedQrCodes, null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e("Tester", "processPhotoBitmap: 异常", e)
        withContext(Dispatchers.Main) {
            onResult(emptyList(), null, null)
        }
    }
}

/**
 * 多码模式确认时：只做OCR + AI，不做QR检测（QR码已从实时扫描累积）
 */
internal suspend fun processBitmapOcrOnly(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    onResult: (ExtractedContactInfo?, String?) -> Unit
) {
    try {
        Log.d("Tester", "processBitmapOcrOnly: 开始OCR处理, bitmap=${bitmap.width}x${bitmap.height}")

        val ocrText = withContext(Dispatchers.IO) {
            val qrBounds = detectQrCodesWithBounds(bitmap)
            val maskedBitmap = maskQrRegions(bitmap, qrBounds)
            val needRecycleMasked = maskedBitmap !== bitmap
            val text = recognizeTextFromBitmap(maskedBitmap)
            if (needRecycleMasked) maskedBitmap.recycle()
            Log.d("Tester", "processBitmapOcrOnly: ML Kit OCR 完成(已遮盖QR区域), 文字长度=${text.length}, 前200字=${text.take(200)}")
            text
        }

        val hasVision = AiOcrConfig.hasVisionModel(context)
        Log.d("Tester", "processBitmapOcrOnly: hasVision=$hasVision")

        val aiResult = if (hasVision) {
            Log.d("Tester", "processBitmapOcrOnly: 使用 Vision 模式（发图片到AI）")
            AiOcrService.recognizeImageWithFallback(context, bitmap)
        } else {
            if (ocrText.isNotBlank()) {
                Log.d("Tester", "processBitmapOcrOnly: 使用纯文本模式（发OCR文字给AI）")
                AiOcrService.recognizeFromTextWithFallback(context, ocrText)
            } else {
                Log.w("Tester", "processBitmapOcrOnly: 纯文本模式但OCR文字为空，跳过AI")
                AiOcrServiceResult.Error("未识别到文字")
            }
        }
        Log.d("Tester", "processBitmapOcrOnly: AI结果类型=${aiResult.javaClass.simpleName}")
        withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                    Log.d("Tester", "processBitmapOcrOnly: AI成功, name=${info.name}, phone=${info.phone}, email=${info.email}, platforms=${info.platforms}")
                    onResult(info, null)
                }
                is AiOcrServiceResult.Error -> {
                    Log.e("Tester", "processBitmapOcrOnly: AI失败, error=${aiResult.message}")
                    onResult(null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e("Tester", "processBitmapOcrOnly: 异常", e)
        withContext(Dispatchers.Main) {
            onResult(null, null)
        }
    }
}

/**
 * 使用 ML Kit 中文 OCR 从 Bitmap 中提取文字
 */
internal suspend fun recognizeTextFromBitmap(bitmap: android.graphics.Bitmap): String =
    withContext(Dispatchers.IO) {
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition
                .getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            val visionText = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        Log.d("Tester", "ML Kit OCR 成功: 文字长度=${result.text.length}, 文本块数=${result.textBlocks.size}, 前200字=${result.text.take(200)}")
                        cont.resume(result.text) {}
                    }
                    .addOnFailureListener { e ->
                        Log.e("Tester", "ML Kit OCR 失败: ${e.message}", e)
                        cont.resume("") {}
                    }
            }
            recognizer.close()
            visionText
        } catch (e: Exception) {
            Log.e("Tester", "ML Kit OCR 初始化失败: ${e.message}", e)
            ""
        }
    }
