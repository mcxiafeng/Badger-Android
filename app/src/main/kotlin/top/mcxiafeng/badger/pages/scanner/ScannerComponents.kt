package top.mcxiafeng.badger.pages.scanner

import android.graphics.Bitmap
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.AiOcrService
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.ocr.toExtractedContactInfo
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

private const val TAG = "ScannerComponents"

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .background(Color.Black.copy(alpha = 0.5f))
            .statusBarsPadding()
            .padding(vertical = BadgerSpacing.sm, horizontal = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SwipeableModeTab(
            indicatorFraction = (selectedMode.toFloat() - animatedSwipe.coerceIn(-1f, 1f)).coerceIn(0f, 1f),
            onModeClick = onModeClick
        )
    }

    IconButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(top = BadgerSpacing.sm, start = BadgerSpacing.lg),
        backgroundColor = Color.White.copy(alpha = 0.2f)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "返回",
            tint = Color.White
        )
    }

    IconButton(
        onClick = onNavigateToCreateContact,
        enabled = !showResultDialog,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = BadgerSpacing.sm, end = BadgerSpacing.lg),
        backgroundColor = Color.White.copy(alpha = 0.2f)
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "手动输入",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }

    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(vertical = BadgerSpacing.xl),
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
            val hasAccumulated = qrDetectionState.accumulatedContents.isNotEmpty()
            val hasTextBlocks = aiOcrEnabled && qrDetectionState.textBlockCount > 0
            val canCollect = hasAccumulated || hasTextBlocks
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (canCollect) Color.White else Color.White.copy(alpha = 0.5f))
                    .clickable(enabled = !showResultDialog && canCollect) { onCaptureClick() }
                    .semantics {
                        contentDescription = if (canCollect) "确认收集" else "尚未识别到可收集内容"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = if (canCollect) 0.72f else 0.35f),
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "扫描中" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
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

private fun Bitmap.recycleSafely() {
    if (!isRecycled) recycle()
}

private suspend fun processOcrAndAi(
    context: android.content.Context,
    bitmap: Bitmap,
): AiOcrService.AiOcrServiceResult {
    val ocrText = withContext(Dispatchers.IO) {
        val qrBounds = detectQrCodesWithBounds(bitmap)
        val maskedBitmap = maskQrRegions(bitmap, qrBounds)
        val needRecycleMasked = maskedBitmap !== bitmap
        try {
            recognizeTextFromBitmap(maskedBitmap)
        } finally {
            if (needRecycleMasked) maskedBitmap.recycleSafely()
        }
    }

    val hasVision = AiOcrConfig.hasVisionModel(context)

    return if (hasVision) {
        AiOcrService.recognizeImageWithFallback(bitmap)
    } else if (ocrText.isNotBlank()) {
        AiOcrService.recognizeFromTextWithFallback(ocrText)
    } else {
        Log.w(TAG, "processOcrAndAi: 纯文本模式但OCR文字为空，跳过AI")
        AiOcrService.AiOcrServiceResult.Error("未识别到文字")
    }
}

internal suspend fun processPhotoBitmap(
    context: android.content.Context,
    bitmap: Bitmap,
    aiOcrEnabled: Boolean,
    onResult: (List<String>, ExtractedContactInfo?, String?) -> Unit
) {
    try {
        if (!aiOcrEnabled) {
            val detectedQrCodes = withContext(Dispatchers.IO) { detectQrCodesFromBitmap(context, bitmap) }
            withContext(Dispatchers.Main) { onResult(detectedQrCodes, null, null) }
            return
        }

        val detectedQrCodes = withContext(Dispatchers.IO) {
            detectQrCodesWithBounds(bitmap).map { it.content }
        }
        val aiResult = processOcrAndAi(context, bitmap)

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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "processPhotoBitmap: 异常", e)
        withContext(Dispatchers.Main) { onResult(emptyList(), null, null) }
    } finally {
        bitmap.recycleSafely()
    }
}

internal suspend fun processBitmapOcrOnly(
    context: android.content.Context,
    bitmap: Bitmap,
    onResult: (ExtractedContactInfo?, String?) -> Unit
) {
    try {
        val aiResult = processOcrAndAi(context, bitmap)
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "processBitmapOcrOnly: 异常", e)
        withContext(Dispatchers.Main) { onResult(null, null) }
    } finally {
        bitmap.recycleSafely()
    }
}

internal suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
    withContext(Dispatchers.IO) {
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition
                .getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            try {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { result -> cont.resume(result.text) {} }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "ML Kit OCR 失败: ${e.message}", e)
                            cont.resume("") {}
                        }
                }
            } finally {
                recognizer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR 初始化失败: ${e.message}", e)
            ""
        }
    }
