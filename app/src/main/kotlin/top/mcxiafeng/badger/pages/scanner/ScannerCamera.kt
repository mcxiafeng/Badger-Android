package top.mcxiafeng.badger.pages.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

private const val TAG = "ScannerCamera"

/** 扫码模式：单码识别节流间隔（毫秒） */
private const val SCAN_DEBOUNCE_MS = 500L
/** 扫码模式：关闭对话框后冷却时间（毫秒），防止立即重新识别 */
private const val SCAN_DISMISS_COOLDOWN_MS = 2000L
/** 多码模式：帧分析节流间隔（毫秒） */
private const val MULTI_SCAN_THROTTLE_MS = 200L
/** 对焦动画显示时长（毫秒） */
private const val FOCUS_ANIMATION_DURATION_MS = 800L

/**
 * 相机预览组件
 *
 * 使用 CameraX 实现相机预览，功能包括：
 * - 实时预览画面
 * - 拍照（通过 takePhotoTrigger 触发）
 * - 扫码模式：实时单码识别
 * - 多码模式：实时多码识别 + 框选坐标
 * - 点击对焦
 * - 闪光灯控制
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
internal fun CameraPreview(
    modifier: Modifier = Modifier,
    isFlashOn: Boolean,
    isScanningPaused: Boolean = false,
    onImageCaptured: (Bitmap) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onQrCodesWithBounds: (List<QrCodeWithBounds>, Int, Int) -> Unit = { _, _, _ -> },
    onTextBlocksDetected: (List<TextBoundingBox>, Int, Int) -> Unit = { _, _, _ -> },
    onPreviewSizeChanged: (Size, Size) -> Unit = { _, _ -> },
    mode: CameraMode,
    aiOcrEnabled: Boolean = false,
    takePhotoTrigger: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val currentIsScanningPaused by rememberUpdatedState(isScanningPaused)
    val currentOnQrCodesWithBounds by rememberUpdatedState(onQrCodesWithBounds)
    val currentOnTextBlocksDetected by rememberUpdatedState(onTextBlocksDetected)
    val currentAiOcrEnabled by rememberUpdatedState(aiOcrEnabled)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val currentCamera by rememberUpdatedState(camera)

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val photoExecutor = remember { Executors.newSingleThreadExecutor() }

    val textRecognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                currentCamera?.cameraControl?.enableTorch(false)
            } catch (e: Exception) {
                Log.w(TAG, "关闭闪光灯失败", e)
            }
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "CameraPreview onDispose: unbindAll 失败", e)
            }
            analyzerExecutor.shutdown()
            photoExecutor.shutdown()
            try {
                textRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "CameraPreview onDispose: close TextRecognizer 失败", e)
            }
        }
    }

    DisposableEffect(previewView) {
        fun reportSizes() {
            val viewSize = Size(previewView.width.toFloat(), previewView.height.toFloat())
            val surfaceSize = previewView.getSurfaceSize()
            onPreviewSizeChanged(viewSize, surfaceSize)
        }

        val viewListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reportSizes() }
        previewView.addOnLayoutChangeListener(viewListener)

        val hierarchyListener = object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                child.addOnLayoutChangeListener { _, _, _, _, _, _, _, _ -> reportSizes() }
                child.post { reportSizes() }
            }
            override fun onChildViewRemoved(parent: View, child: View) {}
        }
        (previewView as? ViewGroup)?.setOnHierarchyChangeListener(hierarchyListener)

        reportSizes()

        onDispose {
            previewView.removeOnLayoutChangeListener(viewListener)
            (previewView as? ViewGroup)?.setOnHierarchyChangeListener(null)
        }
    }

    LaunchedEffect(cameraProviderFuture, isFlashOn, mode) {
        val cameraProvider = cameraProviderFuture.get()

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder().build()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    if (currentIsScanningPaused) {
                        imageProxy.close()
                    } else if (mode == CameraMode.SCAN) {
                        processImageForQR(imageProxy) { content ->
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                onQrCodeDetected(content)
                            }
                        }
                    } else if (mode == CameraMode.PHOTO) {
                        analyzePhotoFrame(
                            imageProxy = imageProxy,
                            onQrCodesWithBounds = { detections, width, height ->
                                coroutineScope.launch(Dispatchers.Main.immediate) {
                                    currentOnQrCodesWithBounds(detections, width, height)
                                }
                            },
                            onTextBlocksDetected = currentOnTextBlocksDetected,
                            aiOcrEnabled = currentAiOcrEnabled,
                            textRecognizer = textRecognizer,
                            scope = coroutineScope,
                        )
                    } else {
                        imageProxy.close()
                    }
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalyzer
            )
            camera?.cameraControl?.enableTorch(isFlashOn)
        } catch (e: Exception) {
            Log.e(TAG, "相机绑定失败", e)
        }
    }

    LaunchedEffect(takePhotoTrigger) {
        if (takePhotoTrigger == 0 || imageCapture == null) return@LaunchedEffect
        val capture = imageCapture!!
        val outputFile = File.createTempFile("photo", ".jpg", context.cacheDir)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        capture.takePicture(
            outputFileOptions,
            photoExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val filePath = outputFile.absolutePath
                    var bitmap: Bitmap? = null
                    var delivered = false
                    try {
                        val decodedBitmap = BitmapFactory.decodeFile(filePath)
                        if (decodedBitmap != null) {
                            bitmap = QrImagePreprocessor.rotateFromExifFile(decodedBitmap, filePath)
                            val capturedBitmap = bitmap ?: return
                            val deliveryJob = coroutineScope.launch(Dispatchers.Main.immediate) {
                                onImageCaptured(capturedBitmap)
                            }
                            deliveryJob.invokeOnCompletion { cause ->
                                if (cause != null && !capturedBitmap.isRecycled) {
                                    Log.w(TAG, "拍照结果未成功交给 UI，回收 Bitmap", cause)
                                    capturedBitmap.recycle()
                                }
                            }
                            delivered = true
                            bitmap = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "拍照保存回调异常", e)
                    } finally {
                        if (!delivered && bitmap != null && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        if (!outputFile.delete() && outputFile.exists()) {
                            Log.w(TAG, "删除拍照临时文件失败: ${outputFile.absolutePath}")
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照保存失败", exc)
                    if (!outputFile.delete() && outputFile.exists()) {
                        Log.w(TAG, "删除拍照临时文件失败: ${outputFile.absolutePath}")
                    }
                }
            }
        )
    }

    var focusOffset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusOffset = offset
                        val display = context.display
                        val cameraInfo = camera?.cameraInfo
                        if (display != null && cameraInfo != null) {
                            val factory = DisplayOrientedMeteringPointFactory(
                                display,
                                cameraInfo,
                                previewView.width.toFloat(),
                                previewView.height.toFloat()
                            )
                            val point = factory.createPoint(offset.x, offset.y)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).build()
                            )
                        }
                    }
                }
        )
        if (focusOffset != null) {
            val offset = focusOffset!!
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { offset.x.toDp() } - 30.dp,
                        y = with(density) { offset.y.toDp() } - 30.dp
                    )
                    .size(60.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
        }
    }

    LaunchedEffect(focusOffset) {
        if (focusOffset != null) {
            delay(FOCUS_ANIMATION_DURATION_MS)
            focusOffset = null
        }
    }
}

internal val lastScanTime = AtomicLong(0L)
internal val lastDismissTime = AtomicLong(0L)
internal val lastMultiScanTime = AtomicLong(0L)

internal fun processImageForQR(
    imageProxy: ImageProxy,
    onQrCodeDetected: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    if (now - lastScanTime.get() < SCAN_DEBOUNCE_MS) {
        imageProxy.close()
        return
    }
    if (now - lastDismissTime.get() < SCAN_DISMISS_COOLDOWN_MS) {
        imageProxy.close()
        return
    }
    lastScanTime.set(now)

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    var bitmap: Bitmap? = null
    var rotatedBitmap: Bitmap? = null
    try {
        bitmap = imageProxy.toBitmap()
        rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
            QrImagePreprocessor.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        } else {
            bitmap
        }

        val results = WeChatQRCodeDetector.detectAndDecode(rotatedBitmap)
        for (text in results) {
            if (text.isNotEmpty()) onQrCodeDetected(text)
        }
    } catch (e: Exception) {
        Log.e(TAG, "WeChatQRCode检测失败", e)
    } finally {
        rotatedBitmap?.let { if (it !== bitmap) it.recycle() }
        bitmap?.recycle()
        imageProxy.close()
    }
}

internal fun analyzePhotoFrame(
    imageProxy: ImageProxy,
    onQrCodesWithBounds: (List<QrCodeWithBounds>, Int, Int) -> Unit,
    onTextBlocksDetected: (List<TextBoundingBox>, Int, Int) -> Unit,
    aiOcrEnabled: Boolean,
    textRecognizer: TextRecognizer,
    scope: CoroutineScope,
) {
    val now = System.currentTimeMillis()
    if (now - lastMultiScanTime.get() < MULTI_SCAN_THROTTLE_MS) {
        imageProxy.close()
        return
    }
    lastMultiScanTime.set(now)

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    var bitmap: Bitmap? = null
    var rotatedBitmap: Bitmap? = null
    try {
        bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        rotatedBitmap = if (rotation != 0) {
            QrImagePreprocessor.rotateBitmap(bitmap, rotation)
        } else {
            bitmap
        }

        val detections = detectQrCodesWithBounds(rotatedBitmap)
        onQrCodesWithBounds(detections, rotatedBitmap.width, rotatedBitmap.height)

        if (aiOcrEnabled) {
            val ocrSource = rotatedBitmap.copy(
                rotatedBitmap.config ?: Bitmap.Config.ARGB_8888,
                false
            )
            val ocrWidth = rotatedBitmap.width
            val ocrHeight = rotatedBitmap.height
            scope.launch(Dispatchers.Default) {
                try {
                    val textBlocks = detectTextBlocksFromBitmap(ocrSource, textRecognizer)
                    withContext(Dispatchers.Main) {
                        onTextBlocksDetected(textBlocks, ocrWidth, ocrHeight)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "文字区域检测异常", e)
                } finally {
                    ocrSource.recycle()
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "PhotoFrame分析失败", e)
    } finally {
        rotatedBitmap?.let { if (it !== bitmap) it.recycle() }
        bitmap?.recycle()
        imageProxy.close()
    }
}

internal suspend fun detectTextBlocksFromBitmap(
    bitmap: Bitmap,
    recognizer: TextRecognizer
): List<TextBoundingBox> {
    return try {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
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
            TextBoundingBox(
                corners = listOf(
                    Offset(rect.left.toFloat(), rect.top.toFloat()),
                    Offset(rect.right.toFloat(), rect.top.toFloat()),
                    Offset(rect.right.toFloat(), rect.bottom.toFloat()),
                    Offset(rect.left.toFloat(), rect.bottom.toFloat())
                )
            )
        } ?: emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "文字区域检测异常", e)
        emptyList()
    }
}