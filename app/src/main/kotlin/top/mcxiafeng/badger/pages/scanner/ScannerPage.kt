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
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.ai.AiTagException
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

    val viewModel: ScannerViewModel = koinViewModel()
    val contactRepository = viewModel.contactRepository
    val fieldRepository = viewModel.fieldRepository
    val collectionRepository = viewModel.collectionRepository
    val tagRepository = viewModel.tagRepository
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
                    scope.launch(Dispatchers.IO) {
                        val firstInfo = selectedItems.firstOrNull()?.second ?: return@launch
                        val sourceType = if (qrCodeContents.isNotEmpty()) "scan" else "photo"

                        // 收集本次扫描涉及的所有联系人 id,后续统一打标记 Tag + 后台 AI
                        val savedContactIds = mutableListOf<Long>()
                        val isNewContactBatch = existingContact == null

                        if (existingContact != null) {
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
                                duplicateFieldKeys = duplicateKeys
                            )
                            savedContactIds += existingContact.id
                        } else {
                            selectedItems.forEach { (qrContent, info) ->
                                val now = System.currentTimeMillis()
                                val contact = Contact(
                                    id = 0L,
                                    name = info.name ?: "未知联系人",
                                    avatarUrl = info.avatarUrl,
                                    createTime = now,
                                    updateTime = now,
                                )
                                val newId = saveScannedContact(
                                    contactRepository, fieldRepository, collectionRepository,
                                    contact, info, sourceType, qrContent, null, targetCollectionId
                                )
                                savedContactIds += newId
                            }
                        }

                        // 1) 应用本次扫描标记 Tag(用户开关开启 + 已选 Tag 时)
                        if (markerConfig.enabled && markerConfig.tagId != null) {
                            savedContactIds.forEach { cid ->
                                try {
                                    viewModel.tagRepository.addTagToContact(cid, markerConfig.tagId)
                                    Log.d(TAG, "onConfirm: 应用本次扫描标记 tagId=${markerConfig.tagId} -> contactId=$cid")
                                } catch (e: Exception) {
                                    Log.e(TAG, "onConfirm: 应用标记 Tag 失败 cid=$cid", e)
                                }
                            }
                        }

                        // 2) 全新的联系人:后台异步跑 AI 贴标签
                        if (isNewContactBatch) {
                            val tagRepo = viewModel.tagRepository
                            val aiGen = viewModel.aiTagGenerator
                            savedContactIds.forEach { cid ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val bio = contactRepository.getContactById(cid)?.bio
                                        if (bio.isNullOrBlank()) {
                                            Log.d(TAG, "后台 AI 打标跳过: contactId=$cid 无 bio")
                                            return@launch
                                        }
                                        val existingTags = tagRepo.getAllTagsOnce()
                                        val candidates = try {
                                            aiGen.suggest(bio, existingTags)
                                        } catch (e: AiTagException) {
                                            Log.w(TAG, "后台 AI 失败,降级 fallbackLocal: cid=$cid, ${e.message}")
                                            aiGen.fallbackLocal(bio, existingTags)
                                        }
                                        candidates.forEach { c ->
                                            val tagId = if (c.matchedExisting && c.existingTagId != null) {
                                                c.existingTagId
                                            } else {
                                                tagRepo.upsertTag(c.name, c.color, source = "ai")
                                            }
                                            tagRepo.addTagToContact(cid, tagId)
                                        }
                                        Log.d(TAG, "后台 AI 打标完成: contactId=$cid, candidates=${candidates.size}")
                                    } catch (e: Exception) {
                                        // [修复防御]: 后台静默失败,不打扰用户
                                        Log.e(TAG, "后台 AI 打标失败: contactId=$cid", e)
                                    }
                                }
                            }
                        }
                    }
                    resetScannerState()
                },
                onAttachToExisting = { contact, info, markerConfig ->
                    scope.launch(Dispatchers.IO) {
                        if (info.platforms.isNotEmpty() || info.phone != null || info.email != null) {
                            val fieldKeys = info.toFieldValues().keys.toList()
                            Log.d("ScannerPage", "onAttachToExisting: 附加字段到已有联系人 contact=${SafeLog.unknown(contact.name)}, fieldKeys=$fieldKeys, platforms=${info.platforms.keys}")
                            attachToExistingContact(
                                contactRepository = contactRepository,
                                fieldRepository = fieldRepository,
                                collectionRepository = collectionRepository,
                                existingContact = contact,
                                info = info,
                                selectedFields = fieldKeys,
                                customFields = emptyMap(),
                                networkResult = null
                            )
                        } else {
                            // 没平台字段可附加:仅更新 contact.updateTime(原 addStyleOnly 的副作用)
                            contactRepository.updateContact(
                                (contactRepository.getContactById(contact.id) ?: contact)
                                    .copy(updateTime = System.currentTimeMillis())
                            )
                        }
                        // 应用本次扫描标记 Tag
                        if (markerConfig.enabled && markerConfig.tagId != null) {
                            try {
                                viewModel.tagRepository.addTagToContact(contact.id, markerConfig.tagId)
                                Log.d(TAG, "onAttachToExisting: 应用本次扫描标记 tagId=${markerConfig.tagId} -> contactId=${contact.id}")
                            } catch (e: Exception) {
                                Log.e(TAG, "onAttachToExisting: 应用标记 Tag 失败 cid=${contact.id}", e)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            val msg = if (markerConfig.enabled && markerConfig.tagId != null) {
                                "已添加到 ${contact.name}\n已自动添加标签：${markerConfig.tagName}"
                            } else {
                                "已成功附加到 ${contact.name}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            resetScannerState()
                        }
                    }
                }
            )
        }

        }

    }
}