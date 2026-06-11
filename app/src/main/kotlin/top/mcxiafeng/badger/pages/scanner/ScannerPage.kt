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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.utils.extractDominantColor
import top.mcxiafeng.badger.utils.getPlatformBrandColor
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text

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
@androidx.compose.ui.tooling.preview.Preview
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

    val viewModel: ScannerViewModel = hiltViewModel()
    val contactRepository = viewModel.contactRepository
    val fieldRepository = viewModel.fieldRepository
    val collectionRepository = viewModel.collectionRepository
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
            capturedImage?.recycle()
            Log.d("Tester", "ScannerPage: DisposableEffect 退出, 已回收 capturedImage")
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
                Log.d("Tester", "ScannerPage: capturedImage 更新(相册), 已回收旧Bitmap")
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

        Box(modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp))) {
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
                            Log.d("Tester", "ScannerPage: capturedImage 更新(OCR拍照), 已回收旧Bitmap")
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
                            Log.d("Tester", "ScannerPage: capturedImage 更新(拍照流程), 已回收旧Bitmap")
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
                        Log.d("ScannerPage", "onQrCodesWithBounds: bitmap=$bmpW×$bmpH, preview=${previewViewSize.width}×${previewViewSize.height}, surface=${previewSurfaceSize.width}×${previewSurfaceSize.height}, detections=${detections.size}")
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewViewSize)
                        val rawBoxes = detections.map { detection ->
                            val mappedCorners = detection.corners.map { corner -> mapper(corner) }
                            if (detection.corners.isNotEmpty()) {
                                Log.d("ScannerPage", "  QR[${detection.content.take(20)}] raw=${detection.corners.first()} → mapped=${mappedCorners.first()}")
                            }
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
                onDismiss = resetScannerState,
                onConfirm = { selectedItems, existingContact, conflictResolutions ->
                    if (onImportToProfile != null) {
                        onImportToProfile(selectedItems)
                        resetScannerState()
                        return@ResultDialog
                    }
                    scope.launch(Dispatchers.IO) {
                        val firstInfo = selectedItems.firstOrNull()?.second ?: return@launch
                        val sourceType = if (qrCodeContents.isNotEmpty()) "scan" else "photo"

                        // 提取样式颜色
                        val styleColor = if (qrCodeContents.isNotEmpty()) {
                            getPlatformBrandColor(qrCodeContents.first())
                        } else {
                            val img = capturedImage
                            if (img != null) extractDominantColor(img)?.themeColor else null
                        }
                        Log.d("Tester", "ScannerPage onConfirm: styleColor=$styleColor, sourceType=$sourceType")

                        if (existingContact != null) {
                            Log.d("Tester", "ScannerPage: 合并信息到已有联系人, conflictResolutions=$conflictResolutions")
                            val entries = buildMergeEntries(contactRepository, fieldRepository, existingContact.id, firstInfo)
                            val newName = if (firstInfo.name != null && firstInfo.name != existingContact.name) firstInfo.name else null
                            val resolvedEntries = entries.map { entry ->
                                val resolution = conflictResolutions[entry.fieldKey]
                                if (resolution != null) entry.copy(selectedValue = resolution) else entry
                            }
                            val duplicateKeys = entries.filter { it.existingValue != null && it.existingValue == it.newValue }.map { it.fieldKey }.toSet()
                            mergeFieldsToContact(
                                contactRepository = contactRepository,
                                fieldRepository = fieldRepository,
                                collectionRepository = collectionRepository,
                                existingContact = existingContact,
                                newInfo = firstInfo,
                                mergeEntries = resolvedEntries,
                                collectionId = ensureCollectionId(collectionRepository, targetCollectionId),
                                sourceType = sourceType,
                                qrCodeContent = selectedItems.firstOrNull()?.first,
                                ocrResult = null,
                                chosenName = newName,
                                duplicateFieldKeys = duplicateKeys,
                                styleColor = styleColor
                            )
                        } else {
                            selectedItems.forEach { (qrContent, info) ->
                                val itemStyleColor = if (qrContent.isNotBlank()) {
                                    getPlatformBrandColor(qrContent)
                                } else styleColor
                                val contact = Contact(
                                    name = info.name ?: "未知联系人",
                                    avatarUrl = info.avatarUrl
                                )
                                saveScannedContact(contactRepository, fieldRepository, collectionRepository, contact, info, sourceType, qrContent, null, targetCollectionId, itemStyleColor)
                            }
                        }
                    }
                    resetScannerState()
                },
                onAddStyle = { contact, info ->
                    scope.launch(Dispatchers.IO) {
                        // 提取样式颜色
                        val styleColor = if (qrCodeContents.isNotEmpty()) {
                            getPlatformBrandColor(qrCodeContents.first())
                        } else {
                            val img = capturedImage
                            if (img != null) extractDominantColor(img)?.themeColor else null
                        }

                        if (info.platforms.isNotEmpty() || info.phone != null || info.email != null) {
                            val fieldKeys = info.toFieldValues().keys.toList()
                            Log.d("ScannerPage", "onAddStyle: 附加字段到已有联系人 contact=${contact.name}, fieldKeys=$fieldKeys, platforms=${info.platforms}, styleColor=$styleColor")
                            attachToExistingContact(
                                contactRepository = contactRepository,
                                fieldRepository = fieldRepository,
                                collectionRepository = collectionRepository,
                                existingContact = contact,
                                info = info,
                                selectedFields = fieldKeys,
                                customFields = emptyMap(),
                                networkResult = null,
                                styleColor = styleColor
                            )
                        } else {
                            addStyleOnly(
                                contactRepository = contactRepository,
                                collectionRepository = collectionRepository,
                                existingContact = contact,
                                newInfo = info,
                                collectionId = targetCollectionId,
                                sourceType = if (qrCodeContents.isNotEmpty()) "scan" else "photo",
                                qrCodeContent = qrCodeContents.firstOrNull(),
                                ocrResult = null,
                                styleColor = styleColor
                            )
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已成功附加到 ${contact.name}", Toast.LENGTH_SHORT).show()
                            resetScannerState()
                        }
                    }
                }
            )
        }

        }

    }
}