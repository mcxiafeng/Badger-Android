package top.mcxiafeng.badger.pages.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ai.AiTagException
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.ensureCollectionId
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.utils.SafeLog
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

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

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedMode by remember { mutableIntStateOf(0) }
    var isFlashOn by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var ocrExtractedInfo by remember { mutableStateOf<ExtractedContactInfo?>(null) }
    var aiOcrError by remember { mutableStateOf<String?>(null) }
    var qrCodeContents by remember { mutableStateOf<List<String>>(emptyList()) }
    var takePhotoTrigger by remember { mutableIntStateOf(0) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var photoNoResult by remember { mutableStateOf(false) }
    var isSavingResult by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var qrDetectionState by remember { mutableStateOf(QrDetectionState()) }
    var previewViewSize by remember { mutableStateOf(Size.Zero) }
    var previewSurfaceSize by remember { mutableStateOf(Size.Zero) }
    val bboxSmoother = remember { BoundingBoxSmoother() }

    var isOcrCapturePending by remember { mutableStateOf(false) }
    var ocrJob by remember { mutableStateOf<Job?>(null) }

    val releaseCapturedImage: () -> Unit = {
        val image = capturedImage
        capturedImage = null
        if (image != null && !image.isRecycled) {
            Log.d(TAG, "释放扫描结果 Bitmap: ${image.width}x${image.height}")
            image.recycle()
        }
    }

    val hasResultDialog =
        scanResult != null ||
            ocrExtractedInfo != null ||
            qrCodeContents.isNotEmpty() ||
            isProcessingPhoto ||
            aiOcrError != null ||
            photoNoResult

    val busy = isProcessingPhoto || isSavingResult || saveError != null

    val cancelOcrWork: () -> Unit = {
        ocrJob?.cancel()
        ocrJob = null
    }

    val launchOcrWork: (suspend () -> Unit) -> Unit = { block ->
        cancelOcrWork()
        ocrJob = scope.launch(Dispatchers.IO) {
            block()
        }
    }

    val resetScannerState: () -> Unit = {
        cancelOcrWork()
        scanResult = null
        ocrExtractedInfo = null
        qrCodeContents = emptyList()
        releaseCapturedImage()
        isProcessingPhoto = false
        aiOcrError = null
        photoNoResult = false
        isOcrCapturePending = false
        isSavingResult = false
        saveError = null
        lastDismissTime.set(System.currentTimeMillis())
    }

    BackHandler(enabled = isSavingResult) { /* 保存中禁止返回 */ }
    BackHandler(enabled = hasResultDialog && !isSavingResult && saveError == null) {
        resetScannerState()
    }
    BackHandler(enabled = !hasResultDialog && !isSavingResult && saveError == null) {
        onBack()
    }

    var aiOcrEnabled by remember { mutableStateOf(AiOcrConfig.isAiOcrEnabled(context)) }

    LaunchedEffect(selectedMode) {
        qrDetectionState = QrDetectionState()
        bboxSmoother.clear()
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelOcrWork()
            releaseCapturedImage()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            var bitmap = BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(it)
            )
            if (bitmap != null) {
                bitmap = QrImagePreprocessor.rotateFromExifStream(bitmap) {
                    context.contentResolver.openInputStream(uri)
                }
                val oldImage = capturedImage
                capturedImage = bitmap
                oldImage?.recycle()
                isProcessingPhoto = true
                aiOcrError = null
                photoNoResult = false
                val workBitmap = createWorkBitmapCopy(bitmap)
                if (workBitmap == null) {
                    isProcessingPhoto = false
                    photoNoResult = true
                    Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                } else {
                    launchOcrWork {
                        processPhotoBitmap(context, workBitmap, aiOcrEnabled) { codes, info, error ->
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
    }

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSwipe by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe_animated"
    )

    Scaffold {
        val showResultDialog = hasResultDialog

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
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
                        isScanningPaused = showResultDialog || isSavingResult || saveError != null,
                        onImageCaptured = { bitmap ->
                            if (isOcrCapturePending) {
                                Log.d(TAG, "多码模式OCR拍照回调: 开始OCR处理")
                                isProcessingPhoto = true
                                aiOcrError = null
                                val oldImage = capturedImage
                                capturedImage = bitmap
                                oldImage?.recycle()
                                val workBitmap = createWorkBitmapCopy(bitmap)
                                if (workBitmap == null) {
                                    isProcessingPhoto = false
                                    isOcrCapturePending = false
                                    photoNoResult = true
                                    Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                                } else {
                                    launchOcrWork {
                                        processBitmapOcrOnly(context, workBitmap) { info, error ->
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
                                }
                            } else {
                                val oldImage = capturedImage
                                capturedImage = bitmap
                                oldImage?.recycle()
                                isProcessingPhoto = true
                                aiOcrError = null
                                photoNoResult = false
                                val workBitmap = createWorkBitmapCopy(bitmap)
                                if (workBitmap == null) {
                                    isProcessingPhoto = false
                                    photoNoResult = true
                                    Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                                } else {
                                    launchOcrWork {
                                        processPhotoBitmap(context, workBitmap, aiOcrEnabled) { codes, info, error ->
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
                        },
                        onQrCodeDetected = { content ->
                            qrCodeContents = listOf(content)
                            scanResult = content
                        },
                        onQrCodesWithBounds = { detections, bmpW, bmpH ->
                            val now = System.currentTimeMillis()
                            val currentContents = detections.map { it.content }.toSet()
                            val bitmapSize = Size(bmpW.toFloat(), bmpH.toFloat())
                            val mapper = buildBitmapToComposeMapper(bitmapSize, previewViewSize)
                            val rawBoxes = detections.map { detection ->
                                val mappedCorners = detection.corners.map { corner -> mapper(corner) }
                                QrBoundingBox(detection.content, mappedCorners, isVisible = true)
                            }
                            val smoothedBoxes = bboxSmoother.smoothQrBoxes(rawBoxes)
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

                ScannerOverlays(selectedMode, qrDetectionState, aiOcrEnabled)
            }

            ScannerControls(
                selectedMode = selectedMode,
                isFlashOn = isFlashOn,
                showResultDialog = showResultDialog || isSavingResult || saveError != null,
                animatedSwipe = animatedSwipe,
                qrDetectionState = qrDetectionState,
                aiOcrEnabled = aiOcrEnabled,
                onBack = onBack,
                onNavigateToCreateContact = onNavigateToCreateContact,
                onModeClick = { mode -> selectedMode = mode },
                onFlashToggle = { isFlashOn = !isFlashOn },
                onCaptureClick = {
                    if (busy) return@ScannerControls
                    if (aiOcrEnabled && AiOcrConfig.isConfigured(context)) {
                        isOcrCapturePending = true
                        takePhotoTrigger++
                    } else if (qrDetectionState.accumulatedContents.isNotEmpty()) {
                        qrCodeContents = qrDetectionState.accumulatedContents.toList()
                        ocrExtractedInfo = null
                        aiOcrError = null
                    }
                },
                onPhotoPickerClick = {
                    if (busy) return@ScannerControls
                    photoPickerLauncher.launch("image/*")
                },
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
                        if (isSavingResult || saveError != null) return@ResultDialog
                        if (onImportToProfile != null) {
                            onImportToProfile(selectedItems)
                            resetScannerState()
                            return@ResultDialog
                        }
                        val firstInfo = selectedItems.firstOrNull()?.second
                        if (firstInfo == null) {
                            Toast.makeText(context, "没有可保存的扫描结果", Toast.LENGTH_SHORT).show()
                            return@ResultDialog
                        }

                        isSavingResult = true
                        saveError = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val sourceType = if (qrCodeContents.isNotEmpty()) "scan" else "photo"
                                val savedContactIds = mutableListOf<Long>()
                                val isNewContactBatch = existingContact == null

                                if (existingContact != null) {
                                    val entries = buildMergeEntries(contactRepository, fieldRepository, existingContact.id, firstInfo)
                                    val newName = if (firstInfo.name != null && firstInfo.name != existingContact.name) firstInfo.name else null
                                    val resolvedEntries = entries.map { entry ->
                                        val resolution = conflictResolutions[entry.fieldKey]
                                        if (resolution != null) entry.copy(selectedValue = resolution) else entry
                                    }
                                    val duplicateKeys = entries
                                        .filter { it.existingValue != null && it.existingValue == it.newValue }
                                        .map { it.fieldKey }
                                        .toSet()
                                    mergeFieldsToContact(
                                        contactRepository = contactRepository,
                                        fieldRepository = fieldRepository,
                                        collectionRepository = collectionRepository,
                                        existingContact = existingContact,
                                        newInfo = firstInfo,
                                        mergeEntries = resolvedEntries,
                                        collectionId = ensureCollectionId(collectionRepository, targetCollectionId),
                                        sourceType = sourceType,
                                        chosenName = newName,
                                        duplicateFieldKeys = duplicateKeys
                                    )
                                    savedContactIds += existingContact.id
                                } else {
                                    selectedItems.forEach { (_, info) ->
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
                                            contact, info, sourceType, targetCollectionId
                                        )
                                        savedContactIds += newId
                                    }
                                }

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
                                                Log.e(TAG, "后台 AI 打标失败: contactId=$cid", e)
                                            }
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    isSavingResult = false
                                    resetScannerState()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "扫描结果保存失败", e)
                                withContext(Dispatchers.Main) {
                                    isSavingResult = false
                                    saveError = "保存扫描结果失败：${e.message ?: "未知错误"}"
                                    Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onAttachToExisting = { contact, info, markerConfig ->
                        if (isSavingResult || saveError != null) return@ResultDialog
                        isSavingResult = true
                        saveError = null
                        scope.launch(Dispatchers.IO) {
                            try {
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
                                    contactRepository.updateContact(
                                        (contactRepository.getContactById(contact.id) ?: contact)
                                            .copy(updateTime = System.currentTimeMillis())
                                    )
                                }
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
                                    isSavingResult = false
                                    resetScannerState()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "附加扫描结果失败", e)
                                withContext(Dispatchers.Main) {
                                    isSavingResult = false
                                    saveError = "附加联系人失败：${e.message ?: "未知错误"}"
                                    Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            }

            if (isSavingResult) {
                WindowDialog(
                    show = true,
                    title = "正在保存",
                    onDismissRequest = {}
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(size = 28.dp, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "正在保存扫描结果，请稍候…",
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }

            saveError?.let { message ->
                WindowDialog(
                    show = true,
                    title = "保存失败",
                    onDismissRequest = {}
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "本次扫描结果尚未自动重试，请先关闭提示并检查联系人列表。",
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TextButton(
                            text = "关闭",
                            onClick = resetScannerState,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}