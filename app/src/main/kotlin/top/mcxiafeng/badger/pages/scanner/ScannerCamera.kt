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
import androidx.compose.foundation.BorderStroke
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
import com.king.wechat.qrcode.WeChatQRCodeDetector
import top.mcxiafeng.badger.pages.scanner.QrImagePreprocessor
import top.mcxiafeng.badger.pages.scanner.detectQrCodesWithBounds
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

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

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val currentIsScanningPaused by rememberUpdatedState(isScanningPaused)
    val currentOnQrCodesWithBounds by rememberUpdatedState(onQrCodesWithBounds)
    val currentOnTextBlocksDetected by rememberUpdatedState(onTextBlocksDetected)
    val currentAiOcrEnabled by rememberUpdatedState(aiOcrEnabled)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // 退出界面时关闭闪光灯
    DisposableEffect(Unit) {
        onDispose {
            camera?.cameraControl?.enableTorch(false)
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
            .setResolutionSelector(
                androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        androidx.camera.core.resolutionselector.AspectRatioStrategy(
                            androidx.camera.core.AspectRatio.RATIO_16_9,
                            androidx.camera.core.resolutionselector.AspectRatioStrategy.FALLBACK_RULE_AUTO
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (currentIsScanningPaused) {
                        imageProxy.close()
                    } else if (mode == CameraMode.SCAN) {
                        processImageForQR(imageProxy, onQrCodeDetected)
                    } else if (mode == CameraMode.PHOTO) {
                        analyzePhotoFrame(imageProxy, currentOnQrCodesWithBounds, currentOnTextBlocksDetected, currentAiOcrEnabled)
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
            e.printStackTrace()
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
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val filePath = output.savedUri?.path
                    var bitmap = BitmapFactory.decodeFile(filePath)
                    if (bitmap != null && filePath != null) {
                        bitmap = QrImagePreprocessor.rotateFromExifFile(bitmap, filePath)
                        onImageCaptured(bitmap)
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    exc.printStackTrace()
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
            delay(800)
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
    if (now - lastScanTime.get() < 500L) {
        imageProxy.close()
        return
    }
    if (now - lastDismissTime.get() < 2000L) {
        imageProxy.close()
        return
    }
    lastScanTime.set(now)

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    try {
        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
            QrImagePreprocessor.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        } else {
            bitmap
        }

        val results = WeChatQRCodeDetector.detectAndDecode(rotatedBitmap)
        for (text in results) {
            if (text.isNotEmpty()) onQrCodeDetected(text)
        }

        if (rotatedBitmap !== bitmap) rotatedBitmap.recycle()
        bitmap.recycle()
    } catch (e: Exception) {
        Log.d("ScannerCamera", "WeChatQRCode detection failed: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

/**
 * 多码模式帧分析：QR 检测 + 可选 OCR 文字区域检测
 *
 * 先做 QR 码检测（始终执行），再按需做 ML Kit 文字检测，
 * 两者复用同一个 bitmap，避免重复转码开销。
 */
internal fun analyzePhotoFrame(
    imageProxy: ImageProxy,
    onQrCodesWithBounds: (List<QrCodeWithBounds>, Int, Int) -> Unit,
    onTextBlocksDetected: (List<TextBoundingBox>, Int, Int) -> Unit,
    aiOcrEnabled: Boolean
) {
    val now = System.currentTimeMillis()
    if (now - lastMultiScanTime.get() < 200L) {
        imageProxy.close()
        return
    }
    lastMultiScanTime.set(now)

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    try {
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotatedBitmap = if (rotation != 0) {
            QrImagePreprocessor.rotateBitmap(bitmap, rotation)
        } else {
            bitmap
        }

        Log.d("ScannerCamera", "ImageAnalysis: sensor=${bitmap.width}x${bitmap.height}, rotation=$rotation, rotated=${rotatedBitmap.width}x${rotatedBitmap.height}, cropRect=${imageProxy.cropRect}")

        // QR 码检测（始终执行）
        val detections = detectQrCodesWithBounds(rotatedBitmap)
        onQrCodesWithBounds(detections, rotatedBitmap.width, rotatedBitmap.height)

        // OCR 文字区域检测（仅 aiOcrEnabled 时执行）
        if (aiOcrEnabled) {
            val textBlocks = detectTextBlocksFromBitmap(rotatedBitmap)
            onTextBlocksDetected(textBlocks, rotatedBitmap.width, rotatedBitmap.height)
        }

        if (rotatedBitmap !== bitmap) rotatedBitmap.recycle()
        bitmap.recycle()
    } catch (e: Exception) {
        Log.d("ScannerCamera", "Photo frame analysis failed: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

/**
 * 使用 ML Kit 中文 OCR 从 Bitmap 中检测文字区域坐标（仅取 boundingBox，不取文字内容）
 *
 * 在 analyzer 线程上同步等待 ML Kit 结果。
 */
internal fun detectTextBlocksFromBitmap(bitmap: Bitmap): List<TextBoundingBox> {
    return try {
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition
            .getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())

        val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(inputImage))
        recognizer.close()

        visionText.textBlocks.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            TextBoundingBox(
                corners = listOf(
                    Offset(rect.left.toFloat(), rect.top.toFloat()),
                    Offset(rect.right.toFloat(), rect.top.toFloat()),
                    Offset(rect.right.toFloat(), rect.bottom.toFloat()),
                    Offset(rect.left.toFloat(), rect.bottom.toFloat())
                )
            )
        }
    } catch (e: Exception) {
        Log.d("ScannerCamera", "Text block detection failed: ${e.message}")
        emptyList()
    }
}