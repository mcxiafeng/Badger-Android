package top.mcxiafeng.badger.pages.scanner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.platform.CameraMode
import top.mcxiafeng.badger.platform.CameraPreviewSlot
import top.mcxiafeng.badger.platform.PhotoTextRecognizer
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.QrCodeDetector
import top.mcxiafeng.badger.platform.PlatformPermissions
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.platform.buildBitmapToComposeMapper
import top.mcxiafeng.badger.platform.notifyScannerDialogDismissed
import top.mcxiafeng.badger.utils.SafeLog
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

private const val TAG = "ScannerPage"

@Composable
fun ScannerPage(
    onBack: () -> Unit = {},
    onImportToProfile: ((List<Pair<String, ExtractedContactInfo>>) -> Unit)? = null,
    targetCollectionId: Long? = null,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToCreateContact: () -> Unit = {}
) {
    
    var hasCameraPermission by remember { mutableStateOf(PlatformPermissions.isCameraGranted()) }
    
    var cameraRequestAttempted by remember { mutableStateOf(false) }

    val viewModel: ScannerViewModel = koinViewModel()
    val contactRepository = viewModel.contactReadRepository()
    val fieldRepository = viewModel.fieldReadRepository()
    val tagRepository = viewModel.tagReadRepository()
    val scope = rememberCoroutineScope()

    
    val qrDetector = remember { QrCodeDetector() }
    val photoTextRecognizer = remember { PhotoTextRecognizer() }
    DisposableEffect(photoTextRecognizer) {
        onDispose { photoTextRecognizer.close() }
    }

    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            hasCameraPermission = PlatformPermissions.requestCamera()
            cameraRequestAttempted = true
        }
    }

    
    var selectedMode by remember { mutableIntStateOf(0) }  
    var isFlashOn by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<PlatformImage?>(null) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var ocrExtractedInfo by remember { mutableStateOf<ExtractedContactInfo?>(null) }
    var aiOcrError by remember { mutableStateOf<String?>(null) }
    var qrCodeContents by remember { mutableStateOf<List<String>>(emptyList()) }
    var takePhotoTrigger by remember { mutableIntStateOf(0) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var photoNoResult by remember { mutableStateOf(false) }

    
    var qrDetectionState by remember { mutableStateOf(QrDetectionState()) }
    var previewViewSize by remember { mutableStateOf(Size.Zero) }
    var previewSurfaceSize by remember { mutableStateOf(Size.Zero) }
    val bboxSmoother = remember { BoundingBoxSmoother() }

    
    var isOcrCapturePending by remember { mutableStateOf(false) }

    
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
        notifyScannerDialogDismissed()
    }
    BackHandler(enabled = !hasResultDialog) {
        onBack()
    }

    
    var aiOcrEnabled by remember { mutableStateOf(AiOcrConfig.isAiOcrEnabled()) }
    val snackbarHostState = remember { SnackbarHostState() }

    
    LaunchedEffect(selectedMode) {
        qrDetectionState = QrDetectionState()
        bboxSmoother.clear()
    }

    
    DisposableEffect(Unit) {
        onDispose {
            capturedImage?.close()
            capturedImage = null
        }
    }

    
    val photoPickerLauncher = rememberImagePickerLauncher { bytes ->
        bytes?.let {
            scope.launch(BadgerDispatchers.io) {
                val newImage = loadOrientedImage(it)
                if (newImage != null) {
                    val oldImage = capturedImage
                    capturedImage = newImage
                    oldImage?.close()
                    isProcessingPhoto = true
                    aiOcrError = null
                    photoNoResult = false
                    processPhotoBitmap(qrDetector, photoTextRecognizer, newImage, aiOcrEnabled) { codes, info, error ->
                        isProcessingPhoto = false
                        qrCodeContents = codes
                        ocrExtractedInfo = info
                        aiOcrError = error
                        if (codes.isEmpty() && info == null && error == null) {
                            photoNoResult = true
                        }
                        if (error != null) {
                            showToast("AI 识别失败：$error")
                        }
                    }
                }
            }
        }
    }

    
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSwipe by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe_animated"
    )

    Scaffold {
        
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
            notifyScannerDialogDismissed()
        }

        Box(modifier = Modifier.fillMaxSize()) {
        
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
                CameraPreviewSlot(
                    modifier = Modifier.fillMaxSize(),
                    isFlashOn = isFlashOn,
                    isScanningPaused = showResultDialog,
                    onImageCaptured = { bitmap ->
                        if (isOcrCapturePending) {
                            
                            BadgerLog.d("ScannerPage", "多码模式OCR拍照回调: 开始OCR处理")
                            isProcessingPhoto = true
                            aiOcrError = null
                            val oldImage = capturedImage
                            capturedImage = bitmap
                            oldImage?.close()
                            scope.launch(BadgerDispatchers.io) {
                                processBitmapOcrOnly(qrDetector, photoTextRecognizer, bitmap) { info, error ->
                                    isProcessingPhoto = false
                                    ocrExtractedInfo = info
                                    aiOcrError = error
                                    qrCodeContents = qrDetectionState.accumulatedContents.toList()
                                    if (qrCodeContents.isEmpty() && info == null && error == null) {
                                        photoNoResult = true
                                    }
                                    if (error != null) {
                                        showToast("AI 识别失败：$error")
                                    }
                                    isOcrCapturePending = false
                                }
                            }
                        } else {
                            
                            val oldImage = capturedImage
                            capturedImage = bitmap
                            oldImage?.close()
                            isProcessingPhoto = true
                            aiOcrError = null
                            photoNoResult = false
                            scope.launch(BadgerDispatchers.io) {
                                processPhotoBitmap(qrDetector, photoTextRecognizer, bitmap, aiOcrEnabled) { codes, info, error ->
                                    isProcessingPhoto = false
                                    qrCodeContents = codes
                                    ocrExtractedInfo = info
                                    aiOcrError = error
                                    if (codes.isEmpty() && info == null && error == null) {
                                        photoNoResult = true
                                    }
                                    if (error != null) {
                                        showToast("AI 识别失败：$error")
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
                        val now = nowMs()
                        val currentContents = detections.map { it.content }.toSet()
                        val bitmapSize = Size(bmpW.toFloat(), bmpH.toFloat())
                        
                        
                        
                        
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewViewSize)
                        val rawBoxes = detections.map { detection ->
                            val mappedCorners = detection.corners.map { corner -> mapper(Offset(corner.x, corner.y)) }
                            
                            
                            
                            
                            QrBoundingBox(detection.content, mappedCorners, isVisible = true)
                        }
                        val smoothedBoxes = bboxSmoother.smoothQrBoxes(rawBoxes)
                        
                        val updatedLastSeen = qrDetectionState.contentLastSeen.toMutableMap()
                        currentContents.forEach { updatedLastSeen[it] = now }
                        val expireThreshold = now - QrDetectionState.EXPIRE_MS
                        val expired = updatedLastSeen.entries.filter { it.value < expireThreshold }.map { it.key }
                        expired.forEach { updatedLastSeen.remove(it) }

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
                            val mappedCorners = block.corners.map { corner -> mapper(Offset(corner.x, corner.y)) }
                            QrBoundingBox("", mappedCorners, isVisible = true)
                        }
                        val smoothedTextBoxes = bboxSmoother.smoothTextBoxes(rawTextBoxes)
                        qrDetectionState = qrDetectionState.copy(
                            visibleTextBoundingBoxes = smoothedTextBoxes,
                            textBlockCount = textBlocks.size
                        )
                    },
                    onPreviewSizeChanged = { viewW, viewH, surfaceW, surfaceH ->
                        previewViewSize = Size(viewW.toFloat(), viewH.toFloat())
                        previewSurfaceSize = Size(surfaceW.toFloat(), surfaceH.toFloat())
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        if (cameraRequestAttempted) {
                            Text("相机权限被拒绝", color = Color.White, style = MiuixTheme.textStyles.body1)
                            Text(
                                "点此去系统设置开启相机权限",
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.body1,
                                modifier = Modifier
                                    .clickable {
                                        BadgerLog.d(TAG, "denied hint tapped → openAppSettings")
                                        PlatformPermissions.openAppSettings()
                                    }
                                    .padding(top = 12.dp),
                            )
                        } else {
                            Text("请求相机权限中...", color = Color.White)
                        }
                    }
                }
            }

            
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
                if (aiOcrEnabled && AiOcrConfig.isConfigured()) {
                    isOcrCapturePending = true
                    takePhotoTrigger++
                } else if (qrDetectionState.accumulatedContents.isNotEmpty()) {
                    qrCodeContents = qrDetectionState.accumulatedContents.toList()
                    ocrExtractedInfo = null
                    aiOcrError = null
                }
            },
            onPhotoPickerClick = { photoPickerLauncher.launch() },
        )

        
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
                                BadgerLog.e(TAG, "onConfirm failed: ${result.reason}")
                                showToast("保存失败：${result.reason}")
                            }
                            CommitResult.NotFound -> {
                                showToast("未找到要合并的联系人")
                            }
                        }
                    }
                },
                onAttachToExisting = { contact, info, markerConfig ->
                    BadgerLog.d(TAG, "onAttachToExisting: contact=${SafeLog.unknown(contact.name)} platforms=${info.platforms.keys}")
                    viewModel.attachScanAndThen(contact, info, markerConfig, targetCollectionId) { result ->
                        when (result) {
                            is CommitResult.Written, CommitResult.SentSuccess -> {
                                val msg = if (markerConfig.enabled && markerConfig.tagId != null) {
                                    "已添加到 ${contact.name}\n已自动添加标签：${markerConfig.tagName}"
                                } else {
                                    "已成功附加到 ${contact.name}"
                                }
                                showToast(msg)
                                resetScannerState()
                            }
                            is CommitResult.SentFailed -> {
                                BadgerLog.e(TAG, "onAttachToExisting failed: ${result.reason}")
                                showToast("附加失败：${result.reason}")
                            }
                            CommitResult.NotFound -> {
                                showToast("未找到该联系人")
                            }
                        }
                    }
                }
            )
        }

        }

    }
}