package top.mcxiafeng.badger.pages.scanner

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
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
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.AiOcrService
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.toExtractedContactInfo
import top.mcxiafeng.badger.platform.PhotoTextRecognizer
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.QR_MASK_PADDING_PX
import top.mcxiafeng.badger.platform.QrCodeDetector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

private const val TAG = "ScannerComponents"

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
 * [修复防御]: 提取公共的 OCR + AI 处理逻辑，消除 processPhotoBitmap 和 processBitmapOcrOnly 的重复代码
 *
 * @param context 上下文
 * @param bitmap 待处理的图片
 * @param onOcrResult OCR 处理完成后的回调，返回 OCR 文字
 * @return AI 解析结果
 */
private suspend fun processOcrAndAi(
    context: android.content.Context,
    qrDetector: QrCodeDetector,
    photoTextRecognizer: PhotoTextRecognizer,
    image: PlatformImage,
    onOcrResult: ((String) -> Unit)? = null,
): AiOcrService.AiOcrServiceResult {
    val ocrText = withContext(Dispatchers.IO) {
        val qrBounds = qrDetector.detectWithBounds(image)
        val maskedImage = qrDetector.maskQrRegions(image, qrBounds, QR_MASK_PADDING_PX)
        val needRecycleMasked = maskedImage !== image
        val text = photoTextRecognizer.recognizeText(maskedImage)
        if (needRecycleMasked) maskedImage.close()
        text
    }

    onOcrResult?.invoke(ocrText)

    val hasVision = AiOcrConfig.hasVisionModel(context)

    return if (hasVision) {
        AiOcrService.recognizeImageWithFallback(image.bitmap)
    } else {
        if (ocrText.isNotBlank()) {
            AiOcrService.recognizeFromTextWithFallback(ocrText)
        } else {
            Log.w(TAG, "processOcrAndAi: 纯文本模式但OCR文字为空，跳过AI")
            AiOcrService.AiOcrServiceResult.Error("未识别到文字")
        }
    }
}

/**
 * 处理拍照/相册图片：二维码检测 + ML Kit OCR + AI 解析
 */
internal suspend fun processPhotoBitmap(
    context: android.content.Context,
    qrDetector: QrCodeDetector,
    photoTextRecognizer: PhotoTextRecognizer,
    image: PlatformImage,
    aiOcrEnabled: Boolean,
    onResult: (List<String>, ExtractedContactInfo?, String?) -> Unit
) {
    try {

        if (!aiOcrEnabled) {
            val detectedQrCodes = withContext(Dispatchers.IO) { qrDetector.detectContents(image) }
                        withContext(Dispatchers.Main) { onResult(detectedQrCodes, null, null) }
            return
        }

        val detectedQrCodes = withContext(Dispatchers.IO) {
            qrDetector.detectWithBounds(image).map { it.content }
        }

        val aiResult = processOcrAndAi(context, qrDetector, photoTextRecognizer, image)

                withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrService.AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                                        onResult(detectedQrCodes, info, null)
                }
                is AiOcrService.AiOcrServiceResult.Error -> {
                    Log.e(TAG, "processPhotoBitmap: AI失败, error=${aiResult.message}")
                    onResult(detectedQrCodes, null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "processPhotoBitmap: 异常", e)
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
    qrDetector: QrCodeDetector,
    photoTextRecognizer: PhotoTextRecognizer,
    image: PlatformImage,
    onResult: (ExtractedContactInfo?, String?) -> Unit
) {
    try {

        val aiResult = processOcrAndAi(context, qrDetector, photoTextRecognizer, image)

                withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrService.AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                                        onResult(info, null)
                }
                is AiOcrService.AiOcrServiceResult.Error -> {
                    Log.e(TAG, "processBitmapOcrOnly: AI失败, error=${aiResult.message}")
                    onResult(null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "processBitmapOcrOnly: 异常", e)
        withContext(Dispatchers.Main) {
            onResult(null, null)
        }
    }
}

