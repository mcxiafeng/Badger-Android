package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** 查看原图/保存原图时拉高清大图的下载超时 */
private const val AVATAR_HD_TIMEOUT_MS = 8000L

/** 缩放查看的下限（1x = 适配屏幕） */
private const val MIN_ZOOM_SCALE = 1f

/** 缩放查看的上限 */
private const val MAX_ZOOM_SCALE = 4f

/** 双击放大的目标倍数 */
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f

/**
 * 联系人头像页。
 *
 * 从联系人详情页点头像进入（替代原来的头像预览弹窗）：
 * 页面本身就是图片查看器——中央大图支持双指缩放、双击放大和拖动平移；
 * 底部提供操作——保存原图（仅有远程原图时）、编辑（选图裁剪更换）、清空（删除头像）。
 *
 * 展示源沿用原预览弹窗的优先级：本地 avatarPath 与远程 avatarUrl 并存时
 * 优先拉远程高清原图（缩放更清晰，QQ 域自动升级 640，见 [upgradeAvatarUrlToHd]），
 * 下载失败回落本地文件；本页内刚裁剪过自定义头像后优先显示本地新图，
 * 避免旧的远程 URL 盖过用户刚设置的图。
 *
 * @param contactId 联系人 ID（"我的名片"为 MY_CONTACT_ID，同样适用）
 * @param onBack 返回回调
 * @param onRefreshData 头像变更后的外部刷新回调（刷新列表与"我的名片"）
 */
@Composable
fun ContactAvatarPage(
    contactId: Long,
    onBack: () -> Unit,
    onRefreshData: (() -> Unit)? = null,
    viewModel: ContactAvatarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contact by viewModel.contact.collectAsState()

    LaunchedEffect(contactId) {
        Log.d("ContactAvatarPageTester", "进入头像页, contactId=$contactId")
        viewModel.loadContact(contactId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactAvatarEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ContactAvatarEvent.AvatarChanged -> onRefreshData?.invoke()
            }
        }
    }

    // 本地 avatarPath 位图（始终加载，供无远程 URL 与下载失败回落）
    var localBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 本次会话从远程 avatarUrl 拉到的高清原图（供缩放与"保存原图"复用）
    var hdBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 本页内刚裁剪过自定义头像 → 优先显示本地新图，不被旧远程 URL 覆盖
    var preferLocal by remember { mutableStateOf(false) }
    val localAvatarPath = contact?.avatarPath
    val remoteAvatarUrl = contact?.avatarUrl
    LaunchedEffect(localAvatarPath, remoteAvatarUrl) {
        hdBitmap = null
        localBitmap = if (!localAvatarPath.isNullOrBlank()) {
            Methods.loadAvatarBitmap(localAvatarPath, context)
        } else null
        val url = remoteAvatarUrl?.takeIf { it.isNotBlank() }
        if (url != null && !preferLocal) {
            val hdUrl = upgradeAvatarUrlToHd(url)
            Log.d("ContactAvatarPageTester", "拉取高清原图 original=$url, hd=$hdUrl")
            val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                BILIBILI_HEADERS else null
            // 优先拉高清；失败回退原始 URL（可能平台不支持高清参数）
            var bmp = HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = AVATAR_HD_TIMEOUT_MS)
            if (bmp == null && hdUrl != url) {
                Log.w("ContactAvatarPageTester", "hd download failed, fallback to original $url")
                bmp = HttpUtil.downloadBitmap(url, headers = headers, timeoutMs = AVATAR_HD_TIMEOUT_MS)
            }
            if (bmp != null) {
                Log.d("ContactAvatarPageTester", "downloaded ${bmp.width}x${bmp.height} from $hdUrl")
            } else {
                Log.w("ContactAvatarPageTester", "hd download failed for $url, fallback to local")
            }
            hdBitmap = bmp
        }
    }
    val displayBitmap = if (preferLocal) localBitmap ?: hdBitmap else hdBitmap ?: localBitmap

    // 编辑：选图 → 裁剪 → 保存
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Log.d("ContactAvatarPageTester", "已选图，打开裁剪: $uri")
            cropSourceUri = uri
            showCropDialog = true
        }
    }
    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        preferLocal = true
        scope.launch {
            try {
                val avatarFile =
                    Methods.saveBitmapAsAvatar(context, croppedBitmap, "contact_${contactId}_avatar.webp")
                viewModel.applyAvatarUpdate(contactId, avatarFile.absolutePath)
            } catch (e: Exception) {
                Log.e("ContactAvatarPageTester", "设置头像失败", e)
                Toast.makeText(context, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 清空确认 + 保存原图进行中
    var showClearDialog by remember { mutableStateOf(false) }
    var isSavingOriginal by remember { mutableStateOf(false) }
    // 有头像字段才显示清空（展示位图可能因网络失败暂时为空）
    val hasAvatar = !localAvatarPath.isNullOrBlank() || !remoteAvatarUrl.isNullOrBlank()
    val hasOriginalUrl = !remoteAvatarUrl.isNullOrBlank()

    // 保存原图：复用本页已拉的高清图；未拉到时现场拉一次
    val saveOriginalAction: () -> Unit = {
        val originalUrl = remoteAvatarUrl?.takeIf { it.isNotBlank() }?.trim()
        if (originalUrl == null) {
            Log.w("ContactAvatarPageTester", "保存原图: 无远程原图, 忽略")
        } else {
            Log.d("ContactAvatarPageTester", "保存原图到相册")
            isSavingOriginal = true
            scope.launch {
                try {
                    val original = hdBitmap
                        ?: withContext(Dispatchers.IO) {
                            val hdUrl = upgradeAvatarUrlToHd(originalUrl)
                            val headers =
                                if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else null
                            HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = AVATAR_HD_TIMEOUT_MS)
                                ?: if (hdUrl != originalUrl)
                                    HttpUtil.downloadBitmap(originalUrl, headers = headers, timeoutMs = AVATAR_HD_TIMEOUT_MS)
                                else null
                        }
                    if (original == null) {
                        Toast.makeText(context, "无法获取原图，请检查网络", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                    val ok = withContext(Dispatchers.IO) {
                        Methods.saveBitmapToGallery(
                            context,
                            original,
                            "badger_avatar_${contactId}_$stamp.png",
                        )
                    }
                    val msg = if (ok) "原图已保存到相册" else "保存失败"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ContactAvatarPageTester", "保存原图失败", e)
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                } finally {
                    isSavingOriginal = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            // 空标题顶栏：只保留返回，图片本身就是页面主角（与详情页顶栏一致）
            TopAppBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            // 中央大图：直接可缩放查看；无图显示姓名首字占位，加载中显示静默占位
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    displayBitmap != null -> ZoomableAvatarImage(
                        bitmap = displayBitmap,
                        modifier = Modifier.fillMaxSize(),
                    )
                    contact != null -> Box(
                        modifier = Modifier
                            .size(BadgerSize.avatarPageSize)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = contact!!.name.take(1),
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    else -> Box(
                        modifier = Modifier
                            .size(BadgerSize.avatarPageSize)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            // 底部操作条：与详情页悬浮工具栏同款"图标+文字"动作，
            // 主次用颜色表达——编辑（主，主题色）/ 保存原图（次，中性）/ 清空（危险，error 色）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BadgerSpacing.lg),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (hasOriginalUrl) {
                    ToolbarAction(
                        icon = Icons.Default.Download,
                        label = if (isSavingOriginal) "正在保存…" else "保存原图",
                        onClick = { if (!isSavingOriginal) saveOriginalAction() },
                    )
                }
                ToolbarAction(
                    icon = Icons.Default.Edit,
                    label = "编辑",
                    onClick = {
                        Log.d("ContactAvatarPageTester", "选图更换头像")
                        pickAvatarLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    tint = MiuixTheme.colorScheme.primary,
                )
                if (hasAvatar) {
                    ToolbarAction(
                        icon = Icons.Default.Delete,
                        label = "清空",
                        onClick = {
                            Log.d("ContactAvatarPageTester", "请求清空头像")
                            showClearDialog = true
                        },
                        tint = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // 编辑：裁剪对话框（与详情页更换头像共用）
    ContactDetailCropDialog(
        show = showCropDialog,
        cropSourceUri = cropSourceUri,
        onCropConfirm = onCropConfirm,
        onDismiss = {
            cropSourceUri = null
            showCropDialog = false
        },
    )

    // 清空确认：删除本地头像文件并恢复首字占位，平台资料不受影响
    if (showClearDialog) {
        WindowDialog(
            show = true,
            title = "清空头像",
            summary = "将删除本地头像文件，平台资料中的头像不受影响。",
            onDismissRequest = { showClearDialog = false },
        ) {
            DialogButtonRow(
                positiveText = "清空",
                onNegative = { showClearDialog = false },
                onPositive = {
                    showClearDialog = false
                    viewModel.clearAvatar(contactId)
                },
                isDestructive = true,
            )
        }
    }
}

/**
 * 可缩放的头像大图查看区。
 *
 * - 双指捏合缩放（[MIN_ZOOM_SCALE]–[MAX_ZOOM_SCALE]），以手势质心为锚点
 * - 放大后拖动平移，边界钳制不让图片拖出可视区
 * - 双击在 1x 与 [DOUBLE_TAP_ZOOM_SCALE] 之间切换，以点击位置为中心
 * - 位图更换（编辑/清空后）自动复位缩放
 */
@Composable
private fun ZoomableAvatarImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    // 钳制平移偏移：不让缩放后的图片拖出可视区（Fit 后方形内容边长取 min(w,h)）
    fun clampOffset(raw: Offset, targetScale: Float, areaWidth: Float, areaHeight: Float): Offset {
        val bound = min(areaWidth, areaHeight) * (targetScale - 1f) / 2f
        return Offset(
            raw.x.coerceIn(-bound, bound),
            raw.y.coerceIn(-bound, bound),
        )
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > MIN_ZOOM_SCALE) {
                            Log.d("ContactAvatarPageTester", "双击复位缩放")
                            scale = MIN_ZOOM_SCALE
                            offset = Offset.Zero
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // 以点击位置为中心放大：o1 = (tap - center) * (1 - s1)
                            val raw = (tap - center) * (1f - DOUBLE_TAP_ZOOM_SCALE)
                            scale = DOUBLE_TAP_ZOOM_SCALE
                            offset = clampOffset(raw, scale, size.width.toFloat(), size.height.toFloat())
                            Log.d("ContactAvatarPageTester", "双击放大到 ${DOUBLE_TAP_ZOOM_SCALE}x")
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // 以质心为锚点缩放（质心处的画面保持不动），再叠加拖动
                    val raw = offset + (centroid - center) * (oldScale - newScale) + pan
                    scale = newScale
                    offset = clampOffset(raw, newScale, size.width.toFloat(), size.height.toFloat())
                }
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "头像大图",
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 把平台头像 URL 升级到高清接口。（原实现位于 ContactDetailPage，随头像页一并迁入本页）
 *
 * 列表项拉 100×100 缩略图；只有查看/保存原图时才调此函数取高清。
 * - QQ 个人号 (q1.qlogo.cn / q.qlogo.cn) → `headimg_dl` 640 接口
 * - QQ 群 (p.qlogo.cn/gh/...) → 末尾加 `/640`
 * - 其它平台(B 站 / 微信等)→ 原样返回
 */
internal fun upgradeAvatarUrlToHd(url: String): String {
    return when {
        // QQ 个人号:g?b=qq&nk=xxx&s=100 → headimg_dl spec=640
        url.contains("qlogo.cn/g") && url.contains("b=qq") -> {
            val nk = Regex("[?&]nk=(\\d+)").find(url)?.groupValues?.get(1)
            if (nk != null) "http://q.qlogo.cn/headimg_dl?dst_uin=$nk&spec=640&img_type=jpg"
            else url
        }
        // QQ 个人号直链已经走 headimg_dl:把 spec 升到 640
        url.contains("q.qlogo.cn/headimg_dl") -> {
            if (url.contains("spec=")) url.replace(Regex("spec=\\d+"), "spec=640")
            else "$url&spec=640"
        }
        // QQ 群头像:https://p.qlogo.cn/gh/{g}/{g}/ 末尾加 /640
        url.contains("p.qlogo.cn/gh/") -> {
            when {
                Regex("/640$").containsMatchIn(url) -> url
                Regex("/\\d+$").containsMatchIn(url) -> "$url/640"
                else -> url
            }
        }
        else -> url
    }
}
