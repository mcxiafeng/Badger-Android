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

    // 提取 Executor 为 remember，便于 onDispose 统一 shutdown（避免每次重组新建线程池导致泄漏）
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val photoExecutor = remember { Executors.newSingleThreadExecutor() }

    // ML Kit TextRecognizer 页面级复用，避免每帧重复创建（~50-100ms开销）
    val textRecognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    // 退出页面时统一释放资源（AnimatedContent 在 exit transition 完成前不会 dispose，
    // 必须主动 unbind 相机，否则 CameraX 会继续推帧 + setAnalyzer 持续回调）
    DisposableEffect(Unit) {
        onDispose {
            // 1) 关闪光灯
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (e: Exception) {
                Log.w(TAG, "关闭闪光灯失败", e)
            }
            // 2) 主动解绑所有相机用例（不等 lifecycle ON_STOP，避免 AnimatedContent 期间相机继续推帧）
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "CameraPreview onDispose: unbindAll 失败", e)
            }
            // 3) 关闭 Executor（之前 newSingleThreadExecutor 没 shutdown 会泄漏线程池）
            analyzerExecutor.shutdown()
            photoExecutor.shutdown()
            // 4) 释放 TextRecognizer
            try {
                textRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "CameraPreview onDispose: close TextRecognizer 失败", e)
            }
        }
    }

    // PreviewView 尺寸追踪（viewSize + surfaceSize）
    // 同时监听 PreviewView 和其内部子 View 的布局变化
    DisposableEffect(previewView) {
        fun reportSizes() {
            val viewSize = Size(previewView.width.toFloat(), previewView.height.toFloat())
            val surfaceSize = previewView.getSurfaceSize()
            onPreviewSizeChanged(viewSize, surfaceSize)
        }

        val viewListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reportSizes() }
        previewView.addOnLayoutChangeListener(viewListener)

        // PreviewView 绑定相机后内部子 View 才会 layout，用 OnHierarchyChangeListener 监听
        val hierarchyListener = object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                child.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reportSizes() }
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

    // 绑定相机用例
    LaunchedEffect(cameraProviderFuture, isFlashOn, mode) {
        val cameraProvider = cameraProviderFuture.get()

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder().build()

        val imageAnalyzer = ImageAnalysis.Builder()
            // 锁定 1280×720，避免部分机型自动协商到过低分析分辨率。
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    if (currentIsScanningPaused) {
                        imageProxy.close()
                    } else if (mode == CameraMode.SCAN) {
                        processImageForQR(imageProxy) { content ->
                            // CameraX analyzer 在 analyzerExecutor 上执行；UI 状态只能由主线程更新。
                            coroutineScope.launch(Dispatchers.Main.immediate) {
                                onQrCodeDetected(content)
                            }
                        }
                    } else if (mode == CameraMode.PHOTO) {
                        analyzePhotoFrame(
                            imageProxy = imageProxy,
                            onQrCodesWithBounds = { detections, width, height ->
                                // analyzePhotoFrame 从 analyzerExecutor 回调；显式切回主线程，
                                // 避免 Compose mutableState 在后台线程被修改。
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

    // 监听拍照触发器
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
                        bitmap = BitmapFactory.decodeFile(filePath)
                        bitmap?.let {
                            bitmap = QrImagePreprocessor.rotateFromExifFile(it, filePath)
                            val capturedBitmap = bitmap
                            // CameraX 的拍照回调运行在 photoExecutor 线程；Compose 状态只能在主线程更新。
                            // 同时把 Bitmap 所有权交给主线程回调，若页面在投递前被销毁则主动回收。
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
                        if (!delivered && bitmap != null) {
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

    // 对焦动画状态
    var focusOffset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    // 相机预览视图（点击对焦）+ 对焦动画
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
        // 对焦圆圈
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

    // 自动隐藏对焦动画
    LaunchedEffect(focusOffset) {
        if (focusOffset != null) {
            delay(FOCUS_ANIMATION_DURATION_MS)
            focusOffset = null
        }
    }
}

/** 上次成功扫描的时间戳（扫码模式节流） */
internal val lastScanTime = AtomicLong(0L)
/** 上次关闭 dialog 的时间戳（扫码模式冷却） */
internal val lastDismissTime = AtomicLong(0L)
/** 上次多码扫描的时间戳（多码模式节流） */
internal val lastMultiScanTime = AtomicLong(0L)

/**
 * 扫码模式：单码识别，500ms节流 + 2s冷却
 */
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

/**
 * 多码模式帧分析：QR 检测 + 可选 OCR 文字区域检测
 *
 * QR 检测同步执行（WeChatQRCode native，<30ms/帧），不阻塞 analyzer 线程。
 * OCR 文字区域检测派发到 [scope] 的 Default 调度器异步执行。
 */
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

        // QR 码检测（始终执行，同步快路径）
        val detections = detectQrCodesWithBounds(rotatedBitmap)
        // 调用方负责主线程派发；这里保持分析器线程不阻塞。
        onQrCodesWithBounds(detections, rotatedBitmap.width, rotatedBitmap.height)

        // OCR 文字区域检测派发到协程，避免 ML Kit 阻塞 analyzer 线程。
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

/**
 * 使用 ML Kit 中文 OCR 从 Bitmap 中检测文字区域坐标（仅取 boundingBox，不取文字内容）
 *
 * 使用 suspendCancellableCoroutine 包装异步 API，避免 Tasks.await() 同步阻塞。
 */
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