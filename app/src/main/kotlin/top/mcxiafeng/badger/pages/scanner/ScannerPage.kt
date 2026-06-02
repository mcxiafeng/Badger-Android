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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.pages.scanner.ScannerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.AiOcrService
import top.mcxiafeng.badger.ocr.AiOcrServiceResult
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.pages.scanner.detectQrCodesFromBitmap
import top.mcxiafeng.badger.utils.extractDominantColor
import top.mcxiafeng.badger.utils.getPlatformBrandColor
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

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
    val repository = viewModel.repository
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
                capturedImage = bitmap
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
                            capturedImage = bitmap
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
                            capturedImage = bitmap
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
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewSurfaceSize, previewViewSize)
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
                        val mapper = buildBitmapToComposeMapper(bitmapSize, previewSurfaceSize, previewViewSize)
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
            if (selectedMode == 1) {
                // 扫码模式：固定框 + 扫描线
                val scanOverlayAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 250),
                    label = "scan_overlay_alpha"
                )
                ScanLineOverlay(modifier = Modifier.graphicsLayer { alpha = scanOverlayAlpha })
            } else {
                // 多码模式：全屏扫描线 + 动态框选 + 计数徽章
                MultiQrScanOverlay(
                    boundingBoxes = qrDetectionState.visibleBoundingBoxes,
                    accumulatedCount = qrDetectionState.accumulatedContents.size,
                    textBoundingBoxes = qrDetectionState.visibleTextBoundingBoxes,
                    textBlockCount = qrDetectionState.textBlockCount,
                    aiOcrEnabled = aiOcrEnabled
                )
            }
        }

        // ========== 顶部 UI 层 ==========
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(vertical = 8.dp, horizontal = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val indicatorFraction = (selectedMode.toFloat() - animatedSwipe.coerceIn(-1f, 1f))
                .coerceIn(0f, 1f)
            SwipeableModeTab(
                indicatorFraction = indicatorFraction,
                onModeClick = { mode -> selectedMode = mode }
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

        // 手动输入按钮（两种模式都可见）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable {
                    Log.d("Tester", "手动输入按钮点击")
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

        // ========== 底部：闪光灯 / 确认 / 相册 ==========
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
                onClick = { isFlashOn = !isFlashOn },
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
                // 多码模式：确认按钮（收集累积的QR码或OCR文字）
                val hasAccumulated = qrDetectionState.accumulatedContents.isNotEmpty()
                val hasTextBlocks = aiOcrEnabled && qrDetectionState.textBlockCount > 0
                val canCollect = hasAccumulated || hasTextBlocks
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (canCollect) Color.White else Color.White.copy(alpha = 0.5f))
                        .clickable(enabled = !showResultDialog && canCollect) {
                            if (aiOcrEnabled && AiOcrConfig.isConfigured(context)) {
                                // AI OCR开启：拍照做OCR，QR码用累积的
                                Log.d("ScannerPage", "多码模式确认: 开启OCR，触发拍照")
                                isOcrCapturePending = true
                                takePhotoTrigger++
                            } else if (hasAccumulated) {
                                // AI OCR关闭：直接用累积的QR码
                                Log.d("ScannerPage", "多码模式确认: 无OCR，直接使用累积QR码, 数量=${qrDetectionState.accumulatedContents.size}")
                                qrCodeContents = qrDetectionState.accumulatedContents.toList()
                                ocrExtractedInfo = null
                                aiOcrError = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 纯白圆形按钮，无数字
                }
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
                onClick = { photoPickerLauncher.launch("image/*") },
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

        // 显示扫描结果对话框
        if (showResultDialog) {
            ResultDialog(
                repository = repository,
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
                            val entries = buildMergeEntries(repository, existingContact.id, firstInfo)
                            val newName = if (firstInfo.name != null && firstInfo.name != existingContact.name) firstInfo.name else null
                            val resolvedEntries = entries.map { entry ->
                                val resolution = conflictResolutions[entry.fieldKey]
                                if (resolution != null) entry.copy(selectedValue = resolution) else entry
                            }
                            val duplicateKeys = entries.filter { it.existingValue != null && it.existingValue == it.newValue }.map { it.fieldKey }.toSet()
                            mergeFieldsToContact(
                                repository = repository,
                                existingContact = existingContact,
                                newInfo = firstInfo,
                                mergeEntries = resolvedEntries,
                                collectionId = ensureCollectionId(repository, targetCollectionId),
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
                                saveScannedContact(repository, contact, info, sourceType, qrContent, null, targetCollectionId, itemStyleColor)
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
                                repository = repository,
                                existingContact = contact,
                                info = info,
                                selectedFields = fieldKeys,
                                customFields = emptyMap(),
                                networkResult = null,
                                styleColor = styleColor
                            )
                        } else {
                            addStyleOnly(
                                repository = repository,
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

/**
 * 处理拍照/相册图片：二维码检测 + ML Kit OCR + AI 解析
 *
 * 用于相册选图流程，保持原有逻辑不变。
 */
private suspend fun processPhotoBitmap(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    aiOcrEnabled: Boolean,
    onResult: (List<String>, ExtractedContactInfo?, String?) -> Unit
) {
    try {
        Log.d("Tester", "processPhotoBitmap: 开始处理, bitmap=${bitmap.width}x${bitmap.height}, aiOcrEnabled=$aiOcrEnabled")

        if (!aiOcrEnabled) {
            val detectedQrCodes = withContext(Dispatchers.IO) { detectQrCodesFromBitmap(context, bitmap) }
            Log.d("Tester", "processPhotoBitmap: AI未启用，二维码检测完成, 数量=${detectedQrCodes.size}")
            withContext(Dispatchers.Main) { onResult(detectedQrCodes, null, null) }
            return
        }

        // 先检测 QR 码（含坐标），遮盖 QR 区域后再做 OCR，避免 QR 像素污染文字识别
        val (detectedQrCodes, ocrText) = withContext(Dispatchers.IO) {
            val qrBounds = detectQrCodesWithBounds(bitmap)
            val codes = qrBounds.map { it.content }
            Log.d("Tester", "processPhotoBitmap: 二维码检测完成, 数量=${codes.size}, 内容=${codes.map { it.take(50) }}")

            val maskedBitmap = maskQrRegions(bitmap, qrBounds)
            val needRecycleMasked = maskedBitmap !== bitmap
            val text = recognizeTextFromBitmap(maskedBitmap)
            if (needRecycleMasked) maskedBitmap.recycle()
            Log.d("Tester", "processPhotoBitmap: ML Kit OCR 完成(已遮盖QR区域), 文字长度=${text.length}, 前200字=${text.take(200)}")
            codes to text
        }

        val hasVision = AiOcrConfig.hasVisionModel(context)
        val supportsVision = AiOcrConfig.supportsVision(context)
        val model = AiOcrConfig.getModel(context)
        Log.d("Tester", "processPhotoBitmap: AI已启用, hasVision=$hasVision, supportsVision=$supportsVision, model=$model")

        val aiResult = if (hasVision) {
            Log.d("Tester", "processPhotoBitmap: 使用 Vision 模式（发图片到AI）")
            AiOcrService.recognizeImageWithFallback(context, bitmap)
        } else {
            if (ocrText.isNotBlank()) {
                Log.d("Tester", "processPhotoBitmap: 使用纯文本模式（发OCR文字给AI）")
                AiOcrService.recognizeFromTextWithFallback(context, ocrText)
            } else {
                Log.w("Tester", "processPhotoBitmap: 纯文本模式但OCR文字为空，跳过AI")
                AiOcrServiceResult.Error("未识别到文字")
            }
        }
        Log.d("Tester", "processPhotoBitmap: AI结果类型=${aiResult.javaClass.simpleName}")
        withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                    Log.d("Tester", "processPhotoBitmap: AI成功, name=${info.name}, phone=${info.phone}, email=${info.email}, platforms=${info.platforms}, otherInfo=${info.otherInfo}")
                    onResult(detectedQrCodes, info, null)
                }
                is AiOcrServiceResult.Error -> {
                    Log.e("Tester", "processPhotoBitmap: AI失败, error=${aiResult.message}")
                    onResult(detectedQrCodes, null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e("Tester", "processPhotoBitmap: 异常", e)
        withContext(Dispatchers.Main) {
            onResult(emptyList(), null, null)
        }
    }
}

/**
 * 多码模式确认时：只做OCR + AI，不做QR检测（QR码已从实时扫描累积）
 *
 * OCR处理逻辑与 processPhotoBitmap 完全一致，只是跳过QR检测。
 */
private suspend fun processBitmapOcrOnly(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    onResult: (ExtractedContactInfo?, String?) -> Unit
) {
    try {
        Log.d("Tester", "processBitmapOcrOnly: 开始OCR处理, bitmap=${bitmap.width}x${bitmap.height}")

        // 先检测 QR 区域并遮盖，避免 QR 像素污染文字识别
        val ocrText = withContext(Dispatchers.IO) {
            val qrBounds = detectQrCodesWithBounds(bitmap)
            val maskedBitmap = maskQrRegions(bitmap, qrBounds)
            val needRecycleMasked = maskedBitmap !== bitmap
            val text = recognizeTextFromBitmap(maskedBitmap)
            if (needRecycleMasked) maskedBitmap.recycle()
            Log.d("Tester", "processBitmapOcrOnly: ML Kit OCR 完成(已遮盖QR区域), 文字长度=${text.length}, 前200字=${text.take(200)}")
            text
        }

        val hasVision = AiOcrConfig.hasVisionModel(context)
        Log.d("Tester", "processBitmapOcrOnly: hasVision=$hasVision")

        val aiResult = if (hasVision) {
            Log.d("Tester", "processBitmapOcrOnly: 使用 Vision 模式（发图片到AI）")
            AiOcrService.recognizeImageWithFallback(context, bitmap)
        } else {
            if (ocrText.isNotBlank()) {
                Log.d("Tester", "processBitmapOcrOnly: 使用纯文本模式（发OCR文字给AI）")
                AiOcrService.recognizeFromTextWithFallback(context, ocrText)
            } else {
                Log.w("Tester", "processBitmapOcrOnly: 纯文本模式但OCR文字为空，跳过AI")
                AiOcrServiceResult.Error("未识别到文字")
            }
        }
        Log.d("Tester", "processBitmapOcrOnly: AI结果类型=${aiResult.javaClass.simpleName}")
        withContext(Dispatchers.Main) {
            when (aiResult) {
                is AiOcrServiceResult.Success -> {
                    val info = aiResult.data.toExtractedContactInfo(aiResult.rawText)
                    Log.d("Tester", "processBitmapOcrOnly: AI成功, name=${info.name}, phone=${info.phone}, email=${info.email}, platforms=${info.platforms}")
                    onResult(info, null)
                }
                is AiOcrServiceResult.Error -> {
                    Log.e("Tester", "processBitmapOcrOnly: AI失败, error=${aiResult.message}")
                    onResult(null, aiResult.message)
                }
            }
        }
    } catch (e: Throwable) {
        Log.e("Tester", "processBitmapOcrOnly: 异常", e)
        withContext(Dispatchers.Main) {
            onResult(null, null)
        }
    }
}

/**
 * 使用 ML Kit 中文 OCR 从 Bitmap 中提取文字
 */
private suspend fun recognizeTextFromBitmap(bitmap: android.graphics.Bitmap): String =
    withContext(Dispatchers.IO) {
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition
                .getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            val visionText = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        Log.d("Tester", "ML Kit OCR 成功: 文字长度=${result.text.length}, 文本块数=${result.textBlocks.size}, 前200字=${result.text.take(200)}")
                        cont.resume(result.text) {}
                    }
                    .addOnFailureListener { e ->
                        Log.e("Tester", "ML Kit OCR 失败: ${e.message}", e)
                        cont.resume("") {}
                    }
            }
            recognizer.close()
            visionText
        } catch (e: Exception) {
            Log.e("Tester", "ML Kit OCR 初始化失败: ${e.message}", e)
            ""
        }
    }