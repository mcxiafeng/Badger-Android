package top.mcxiafeng.badger.pages.scanner

import android.os.Build
import android.util.Log
import android.view.View
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
    onImageCaptured: (android.graphics.Bitmap) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onQrCodesWithBounds: (List<QrCodeWithBounds>, Int, Int) -> Unit = { _, _, _ -> },
    onPreviewSizeChanged: (Size) -> Unit = {},
    mode: CameraMode,
    takePhotoTrigger: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val currentIsScanningPaused by rememberUpdatedState(isScanningPaused)
    val currentOnQrCodesWithBounds by rememberUpdatedState(onQrCodesWithBounds)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // 退出界面时关闭闪光灯
    DisposableEffect(Unit) {
        onDispose {
            camera?.cameraControl?.enableTorch(false)
        }
    }

    // PreviewView 尺寸追踪
    DisposableEffect(previewView) {
        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            onPreviewSizeChanged(Size((right - left).toFloat(), (bottom - top).toFloat()))
        }
        previewView.addOnLayoutChangeListener(listener)
        onDispose { previewView.removeOnLayoutChangeListener(listener) }
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
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (mode == CameraMode.SCAN && !currentIsScanningPaused) {
                        processImageForQR(imageProxy, onQrCodeDetected)
                    } else if (mode == CameraMode.PHOTO) {
                        processImageForQRMulti(imageProxy, currentOnQrCodesWithBounds)
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
            java.io.File.createTempFile("photo", ".jpg", context.cacheDir)
        ).build()
        capture.takePicture(outputFileOptions,
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val filePath = output.savedUri?.path
                    var bitmap = android.graphics.BitmapFactory.decodeFile(filePath)
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
            kotlinx.coroutines.delay(800)
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
 * 多码模式：实时多码识别 + 框选坐标，200ms节流，无冷却
 */
internal fun processImageForQRMulti(
    imageProxy: ImageProxy,
    onQrCodesWithBounds: (List<QrCodeWithBounds>, Int, Int) -> Unit
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
        val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
            QrImagePreprocessor.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        } else {
            bitmap
        }

        val detections = detectQrCodesWithBounds(rotatedBitmap)
        onQrCodesWithBounds(detections, rotatedBitmap.width, rotatedBitmap.height)

        if (rotatedBitmap !== bitmap) rotatedBitmap.recycle()
        bitmap.recycle()
    } catch (e: Exception) {
        Log.d("ScannerCamera", "Multi-QR detection failed: ${e.message}")
    } finally {
        imageProxy.close()
    }
}