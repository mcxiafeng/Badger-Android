package top.mcxiafeng.badger.platform

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import top.mcxiafeng.badger.shared.util.nowMs
import androidx.compose.ui.geometry.Size

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
 * [KMP K10] 相机扫码面 Android actual：CameraX 实现（原 `pages/scanner/ScannerCamera.kt`
 * 逻辑不动迁入 shared androidMain，回调类型换成本平台无关契约）。
 *
 * 既有约束全部保持：
 * - ML Kit TextRecognizer 页面级复用实例（[PhotoTextRecognizer] remember + onDispose close）
 * - 分析帧 close 纪律（暂停/节流/无图均立即 close，不阻塞 analyzer 线程）
 * - 手电筒 onDispose 强制关闭 + unbindAll + Executor shutdown（AnimatedContent exit 期间不推帧）
 * - 拍照 Bitmap 仅在未交付时回收（try/finally）
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
actual fun CameraPreviewSlot(
    modifier: Modifier,
    isFlashOn: Boolean,
    isScanningPaused: Boolean,
    onImageCaptured: (PlatformImage) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onQrCodesWithBounds: (List<QrDetection>, Int, Int) -> Unit,
    onTextBlocksDetected: (List<TextBlockBox>, Int, Int) -> Unit,
    onPreviewSizeChanged: (viewWidth: Int, viewHeight: Int, surfaceWidth: Int, surfaceHeight: Int) -> Unit,
    mode: CameraMode,
    aiOcrEnabled: Boolean,
    takePhotoTrigger: Int
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

    // 引擎：QR 检测 + 文字识别，均为无状态/页面级实例
    val qrDetector = remember { QrCodeDetector() }
    val photoTextRecognizer = remember { PhotoTextRecognizer() }

    // 提取 Executor 为 remember，便于 onDispose 统一 shutdown（避免每次重组新建线程池导致泄漏）
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val photoExecutor = remember { Executors.newSingleThreadExecutor() }

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
            photoTextRecognizer.close()
        }
    }

    // PreviewView 尺寸追踪（viewSize + surfaceSize）
    // 同时监听 PreviewView 和其内部子 View 的布局变化
    DisposableEffect(previewView) {
        fun reportSizes() {
            val viewSize = previewView.width
            val viewHeight = previewView.height
            val surface = Size(previewView.width.toFloat(), previewView.height.toFloat())
            onPreviewSizeChanged(viewSize, viewHeight, surface.width.toInt(), surface.height.toInt())
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
            // [修复防御]: 锁定 1280×720 HD 分辨率喂给 WeChatQRCode。
            // 原 ResolutionSelector(16:9 + FALLBACK_RULE_AUTO) 在某些机型会让 CameraX
            // 选最低可用分辨率（640×480 甚至更低），二维码识别率显著下降。
            // 1280×720 是 Android CameraX 在主流机型上稳定支持的分辨率,WeChatQRCode
            // 处理这个尺寸既能识别准确又不会 OOM。
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    if (currentIsScanningPaused) {
                        imageProxy.close()
                    } else if (mode == CameraMode.SCAN) {
                        processImageForQR(imageProxy, onQrCodeDetected)
                    } else if (mode == CameraMode.PHOTO) {
                        analyzePhotoFrame(
                            imageProxy = imageProxy,
                            qrDetector = qrDetector,
                            photoTextRecognizer = photoTextRecognizer,
                            onQrCodesWithBounds = currentOnQrCodesWithBounds,
                            onTextBlocksDetected = currentOnTextBlocksDetected,
                            aiOcrEnabled = currentAiOcrEnabled,
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
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File.createTempFile("photo", ".jpg", context.cacheDir)
        ).build()
        capture.takePicture(outputFileOptions,
            photoExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val filePath = output.savedUri?.path
                    var bitmap: Bitmap? = null
                    var delivered = false
                    try {
                        bitmap = BitmapFactory.decodeFile(filePath)
                        if (bitmap != null && filePath != null) {
                            bitmap = QrImagePreprocessor.rotateFromExifFile(bitmap, filePath)
                            onImageCaptured(PlatformImage(bitmap))
                            delivered = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "拍照保存回调异常", e)
                    } finally {
                        if (!delivered && bitmap != null) {
                            bitmap.recycle()
                        }
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照保存失败", exc)
                }
            })
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
                                display, cameraInfo,
                                previewView.width.toFloat(), previewView.height.toFloat()
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
                    .offset(x = with(density) { offset.x.toDp() } - 30.dp,
                        y = with(density) { offset.y.toDp() } - 30.dp)
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
 * 扫码页对话框关闭时调用：重置单码识别冷却窗口（防止关闭对话框后立即重新弹结果）。
 * 原 ScannerPage 直接操作 lastDismissTime，随边界收敛为公开入口。
 */
internal fun notifyScannerDialogDismissedAndroid() {
    lastDismissTime.set(nowMs())
}

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
 * OCR 文字区域检测 ML Kit 一帧 50-100ms+，派发到 [scope] 的 Default 调度器
 * 异步执行，主线程回调 onTextBlocksDetected；analyzer 立即释放，下一帧及时进入。
 * OCR 用独立 bitmap copy，主帧 bitmap 在 finally 立即回收，copy 在协程内部回收。
 */
internal fun analyzePhotoFrame(
    imageProxy: ImageProxy,
    qrDetector: QrCodeDetector,
    photoTextRecognizer: PhotoTextRecognizer,
    onQrCodesWithBounds: (List<QrDetection>, Int, Int) -> Unit,
    onTextBlocksDetected: (List<TextBlockBox>, Int, Int) -> Unit,
    aiOcrEnabled: Boolean,
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

        // QR 码检测（始终执行，同步快路径）；坐标已还原到原图像素空间
        val detections = qrDetector.detectWithBounds(PlatformImage(rotatedBitmap))
        onQrCodesWithBounds(detections, rotatedBitmap.width, rotatedBitmap.height)

        // [修复防御]: OCR 文字区域检测派发到协程，避免 ML Kit 阻塞 analyzer 线程。
        // copy 一份 bitmap 让 OCR 协程独立持有所有权；主帧 bitmap 在 finally 立刻回收，
        // 不与协程生命周期耦合。
        if (aiOcrEnabled) {
            val ocrSource = rotatedBitmap.copy(
                rotatedBitmap.config ?: Bitmap.Config.ARGB_8888,
                false
            )
            val ocrWidth = rotatedBitmap.width
            val ocrHeight = rotatedBitmap.height
            scope.launch(Dispatchers.Default) {
                try {
                    val textBlocks = photoTextRecognizer.detectTextBlocks(PlatformImage(ocrSource))
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
