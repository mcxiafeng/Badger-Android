package top.mcxiafeng.badger.pages.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.utils.SafeLog
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text

private const val TAG = "ScannerPage"

/**
 * 扫描页面
 *
 * 核心功能页面，支持两种模式：
 * - **多码模式** (selectedMode=0)：实时多码框选 + 确认收集
 * - **扫码模式** (selectedMode=1)：实时扫描单个二维码/条形码，自动弹出结果
 *
 * @param onBack 返回回调
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun ScannerPage(
    onBack: () -> Unit = {},
    onImportToProfile: ((List<Pair<String, ExtractedContactInfo>>) -> Unit)? = null,
    targetCollectionId: Long? = null,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToCreateContact: () -> Unit = {}
) {
    val context = LocalContext.current

    // 相机权限状态
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限请求启动器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val viewModel: ScannerViewModel = koinViewModel()
    val contactRepository = viewModel.contactReadRepository()
    val fieldRepository = viewModel.fieldReadRepository()
    val tagRepository = viewModel.tagReadRepository()
    val scope = rememberCoroutineScope()

    // 首次进入时请求相机权限
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // ========== 状态变量 ==========
    var selectedMode by remember { mutableIntStateOf(0) }  // 0=多码, 1=扫码，默认扫码
    var isFlashOn by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var ocrExtractedInfo by remember { mutableStateOf<ExtractedContactInfo?>(null) }
    var aiOcrError by remember { mutableStateOf<String?>(null) }
    var qrCodeContents by remember { mutableStateOf<List<String>>(emptyList()) }
    var takePhotoTrigger by remember { mutableIntStateOf(0) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var photoNoResult by remember { mutableStateOf(false) }

    // 多码模式状态
    var qrDetectionState by remember { mutableStateOf(QrDetectionState()) }
    var previewViewSize by remember { mutableStateOf(Size.Zero) }
    var previewSurfaceSize by remember { mutableStateOf(Size.Zero) }
    val bboxSmoother = remember { BoundingBoxSmoother() }

    // 多码模式下是否正在通过拍照做OCR（区分相册选图的拍照）
    var isOcrCapturePending by remember { mutableStateOf(false) }

    // 系统返回键拦截
    val hasResultDialog = scanResult != null || ocrExtractedInfo != null || qrCodeContents.isNotEmpty() || isProcessingPhoto || aiOcrError != null || photoNoResult
    BackHandler(enabled = hasResultDialog) {
        scanResult = null
        ocrExtractedInfo = null
        qrCodeContents = emptyList()
        capturedImage = null
        isProcessingPhoto = false
        aiOcrError = null
        photoNoResult = false
        isOcrCapturePending = false
        lastDismissTime.set(System.currentTimeMillis())
    }
    BackHandler(enabled = !hasResultDialog) {
        onBack()
    }

    // AI 文字识别功能状态
    var aiOcrEnabled by remember { mutableStateOf(AiOcrConfig.isAiOcrEnabled(context)) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 模式切换时重置多码状态
    LaunchedEffect(selectedMode) {
        qrDetectionState = QrDetectionState()
        bboxSmoother.clear()
    }

    // 退出页面时回收 capturedImage
    DisposableEffect(Unit) {
        onDispose {
            capturedImage?.takeIf { !it.isRecycled }?.recycle()
            capturedImage = null
        }
    }

    // 相册选取器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            var bitmap = BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(it)
            )
            if (bitmap != null) {
                bitmap = top.mcxiafeng.badger.pages.scanner.QrImagePreprocessor.rotateFromExifStream(bitmap) {
                    context.contentResolver.openInputStream(uri)
                }
                val oldImage = capturedImage
                capturedImage = bitmap
                oldImage?.recycle()
                                isProcessingPhoto = true
                aiOcrError = null
                photoNoResult = false
                scope.launch(Dispatchers.IO) {
                    processPhotoBitmap(context, bitmap, aiOcrEnabled) { codes, info, error ->
                        isProcessingPhoto = false
                        qrCodeContents = codes
                        ocrExtractedInfo = info
                        aiOcrError = error
                        if (codes.isEmpty() && info == null && error == null) {
                            photoNoResult = true
                        }
                        if (error != null) {
                            Toast.makeText(context, "AI 识别失败：$error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // ========== 滑动切换模式 ==========
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSwipe by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe_animated"
    )

    Scaffold {
        // ========== 扫描结果处理状态 ==========
        val showResultDialog = hasResultDialog

        val resetScannerState: () -> Unit = {
            scanResult = null
            ocrExtractedInfo = null
            qrCodeContents = emptyList()
            capturedImage = null
            isProcessingPhoto = false
            aiOcrError = null
            photoNoResult = false
            isOcrCapturePending = false
            lastDismissTime.set(System.currentTimeMillis())
        }

        Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览容器
        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedMode) {
                detectDragGestures(
                    onDragEnd = {
                        if (swipeOffset < -0.12f && selectedMode == 0) {
                            selectedMode = 1
                        } else if (swipeOffset > 0.12f && selectedMode == 1) {
                            selectedMode = 0
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = {
                        swipeOffset = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val screenWidth = size.width
                    if (screenWidth > 0) {
                        val raw = (swipeOffset + dragAmount.x / screenWidth)
                        swipeOffset = raw.coerceIn(-1f, 1f)
                    }
                }
            }
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isFlashOn = isFlashOn,
                    isScanningPaused = showResultDialog,
                    onImageCaptured = { bitmap ->
                        if (isOcrCapturePending) {
                            // 多码模式确认按钮触发：拍照做OCR，QR码用累积的
                            Log.d("ScannerPage", "多码模式OCR拍照回调: 开始OCR处理")
                            isProcessingPhoto = true
                            aiOcrError = null
                            val oldImage = capturedImage
                            capturedImage = bitmap
                            oldImage?.recycle()
                                                        scope.launch(Dispatchers.IO) {
                                processBitmapOcrOnly(context, bitmap) { info, error ->
                                    isProcessingPhoto = false
                                    ocrExtractedInfo = info
                                    aiOcrError = error
                                    qrCodeContents = qrDetectionState.accumulatedContents.toList()
                                    if (qrCodeContents.isEmpty() && info == null && error == null) {
                                        photoNoResult = true
                                    }
                                    if (error != null) {
                                        Toast.makeText(context, "AI 识别失败：$error", Toast.LENGTH_LONG).show()
                                    }
                                    isOcrCapturePending = false
                                }
                            }
                        } else {
                            // 相册选图流程（不应走到这里，相册走 processPhotoBitmap）
                            val oldImage = capturedImage
                            capturedImage = bitmap
                            oldImage?.recycle()
                                                        isProcessingPhoto = true
                            aiOcrError = null
                            photoNoResult = false
                            scope.launch(Dispatchers.IO) {
                                processPhotoBitmap(context, bitmap, aiOcrEnabled) { codes, info, error ->
                                    isProcessingPhoto = false
                                    qrCodeContents = codes
                                    ocrExtractedInfo = info
                                    aiOcrError = error
                                    if (codes.isEmpty() && info == null && error == null) {
                                        photoNoResult = true
                                    }
                                    if (error != null) {
                                        Toast.makeText(context, "AI 识别失败：$error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    onQrCodeDetected = { content ->
                        qrCodeContents = listOf(content)
                        scanResult = content
                    },
                    onQrCodesWithBounds = { detections, bmpW, bmpH ->
                        val now = System.currentTimeMillis()
                        val currentContents = detections.map { it.content }.toSet()
                        val bitmapSize = Size(bmpW.toFloat(), bmpH.toFloat())
                        // [修复防御]: 帧级日志已注释 —— CameraX ImageAnalysis 默认按 60fps
                        // 推帧,这里每帧必打,logcat 直接刷屏。调试 QR 定位问题时临时打开,
                        // 排查完立刻注释掉,别留在生产代码里。
                        // Log.d("ScannerPage", "onQrCodesWithBounds: bitmap=$bmpW×$bmpH, preview=${previewViewSize.width}×${previewViewSize.height}, surface=${previewSurfaceSize.width}×${previewSurfaceSize.height}, detections=${detections.size}")
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewViewSize)
                        val rawBoxes = detections.map { detection ->
                            val mappedCorners = detection.corners.map { corner -> mapper(corner) }
                            // [修复防御]: 同上,逐 QR 框日志会按 N×fps 刷屏,注释掉。
                            // if (detection.corners.isNotEmpty()) {
                            //     Log.d("ScannerPage", "  QR[${detection.content.take(20)}] raw=${detection.corners.first()} → mapped=${mappedCorners.first()}")
                            // }
                            QrBoundingBox(detection.content, mappedCorners, isVisible = true)
                        }
                        val smoothedBoxes = bboxSmoother.smoothQrBoxes(rawBoxes)
                        // 更新当前帧检测到的码的时间戳，淘汰超时的码
                        val updatedLastSeen = qrDetectionState.contentLastSeen.toMutableMap()
                        currentContents.forEach { updatedLastSeen[it] = now }
                        val expireThreshold = now - QrDetectionState.EXPIRE_MS
                        updatedLastSeen.entries.removeIf { it.value < expireThreshold }

                        qrDetectionState = qrDetectionState.copy(
                            contentLastSeen = updatedLastSeen,
                            visibleBoundingBoxes = smoothedBoxes,
                            bitmapSize = bitmapSize,
                            lastDetectionTime = now
                        )
                    },
                    onTextBlocksDetected = { textBlocks, bmpW, bmpH ->
                        val bitmapSize = Size(bmpW.toFloat(), bmpH.toFloat())
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewViewSize)
                        val rawTextBoxes = textBlocks.map { block ->
                            val mappedCorners = block.corners.map { corner -> mapper(corner) }
                            QrBoundingBox("", mappedCorners, isVisible = true)
                        }
                        val smoothedTextBoxes = bboxSmoother.smoothTextBoxes(rawTextBoxes)
                        qrDetectionState = qrDetectionState.copy(
                            visibleTextBoundingBoxes = smoothedTextBoxes,
                            textBlockCount = textBlocks.size
                        )
                    },
                    onPreviewSizeChanged = { viewSize, surfaceSize ->
                        previewViewSize = viewSize
                        previewSurfaceSize = surfaceSize
                    },
                    mode = if (selectedMode == 0) CameraMode.PHOTO else CameraMode.SCAN,
                    aiOcrEnabled = aiOcrEnabled && selectedMode == 0,
                    takePhotoTrigger = takePhotoTrigger
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请求相机权限中...", color = Color.White)
                }
            }

            // 覆盖层：根据模式切换
            ScannerOverlays(selectedMode, qrDetectionState, aiOcrEnabled)
        }

        ScannerControls(
            selectedMode = selectedMode,
            isFlashOn = isFlashOn,
            showResultDialog = showResultDialog,
            animatedSwipe = animatedSwipe,
            qrDetectionState = qrDetectionState,
            aiOcrEnabled = aiOcrEnabled,
            onBack = onBack,
            onNavigateToCreateContact = onNavigateToCreateContact,
            onModeClick = { mode -> selectedMode = mode },
            onFlashToggle = { isFlashOn = !isFlashOn },
            onCaptureClick = {
                if (aiOcrEnabled && AiOcrConfig.isConfigured(context)) {
                    isOcrCapturePending = true
                    takePhotoTrigger++
                } else if (qrDetectionState.accumulatedContents.isNotEmpty()) {
                    qrCodeContents = qrDetectionState.accumulatedContents.toList()
                    ocrExtractedInfo = null
                    aiOcrError = null
                }
            },
            onPhotoPickerClick = { photoPickerLauncher.launch("image/*") },
        )

        // 显示扫描结果对话框
        if (showResultDialog) {
            ResultDialog(
                repository = contactRepository,
                fieldRepository = fieldRepository,
                show = showResultDialog,
                qrCodeContents = qrCodeContents,
                ocrExtractedInfo = ocrExtractedInfo,
                isPhotoMode = selectedMode == 0,
                isProcessingPhoto = isProcessingPhoto,
                aiOcrError = aiOcrError,
                photoNoResult = photoNoResult,
                isImportToProfile = onImportToProfile != null,
                // [修复防御]: 透传 tagRepository 让 ResultDialog 顶部显示「本次扫描标记 Tag」配置行
                tagRepository = tagRepository,
                onDismiss = resetScannerState,
                onConfirm = { selectedItems, existingContact, conflictResolutions, markerConfig ->
                    if (onImportToProfile != null) {
                        onImportToProfile(selectedItems)
                        resetScannerState()
                        return@ResultDialog
                    }
                    val sourceType = if (qrCodeContents.isNotEmpty()) "scan" else "photo"
                    viewModel.confirmScanAndThen(
                        selectedItems, existingContact, conflictResolutions,
                        markerConfig, targetCollectionId, sourceType,
                    ) { result ->
                        when (result) {
                            is CommitResult.Written, CommitResult.SentSuccess -> resetScannerState()
                            is CommitResult.SentFailed -> {
                                Log.e(TAG, "onConfirm failed: ${result.reason}")
                                Toast.makeText(context, "保存失败：${result.reason}", Toast.LENGTH_LONG).show()
                            }
                            CommitResult.NotFound -> {
                                Toast.makeText(context, "未找到要合并的联系人", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onAttachToExisting = { contact, info, markerConfig ->
                    Log.d(TAG, "onAttachToExisting: contact=${SafeLog.unknown(contact.name)} platforms=${info.platforms.keys}")
                    viewModel.attachScanAndThen(contact, info, markerConfig, targetCollectionId) { result ->
                        when (result) {
                            is CommitResult.Written, CommitResult.SentSuccess -> {
                                val msg = if (markerConfig.enabled && markerConfig.tagId != null) {
                                    "已添加到 ${contact.name}\n已自动添加标签：${markerConfig.tagName}"
                                } else {
                                    "已成功附加到 ${contact.name}"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                resetScannerState()
                            }
                            is CommitResult.SentFailed -> {
                                Log.e(TAG, "onAttachToExisting failed: ${result.reason}")
                                Toast.makeText(context, "附加失败：${result.reason}", Toast.LENGTH_LONG).show()
                            }
                            CommitResult.NotFound -> {
                                Toast.makeText(context, "未找到该联系人", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        }

    }
}