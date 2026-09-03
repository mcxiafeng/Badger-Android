package top.mcxiafeng.badger.pages.person.contact.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 头像大图预览 Dialog — 把状态(高清下载、Bitmap 回收、显示位图选择)封装在这里,
 * 让 [ContactDetailPage] 主 Composable 不再被 100+ 行细节淹没。
 *
 * [§15 #2] 抽出 AvatarCrop / AiTag / PlatformSync 三个 HoistedState 的第一步。
 */
@Composable
internal fun AvatarPreviewDialog(
    contactId: Long,
    avatarUrl: String?,
    fallbackBitmap: Bitmap?,
    show: Boolean,
    onDismiss: () -> Unit,
    onSaveOriginal: (Bitmap?) -> Unit,
    onPickNewAvatar: () -> Unit,
) {
    val hasOriginal = !avatarUrl.isNullOrBlank()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    // 辅助函数：每次覆盖/清空 previewBitmap 之前先把旧图回收,避免 URL 变化时旧图泄漏。
    // [P0 Bitmap 修复]: LaunchedEffect 之前是直接赋值,导致切换 URL 时旧 bitmap 永远不释放 → native heap 累积。
    fun releaseBitmap(bmp: Bitmap?) {
        bmp?.takeIf { !it.isRecycled }?.recycle()
    }
    // [P0 Bitmap 防御] 详情页离开 composition 时回收 previewBitmap,
    // 避免 dialog 打开期间用户切走页面导致 native heap 残留
    DisposableEffect(Unit) {
        onDispose {
            releaseBitmap(previewBitmap)
            previewBitmap = null
        }
    }
    LaunchedEffect(show, avatarUrl) {
        if (show) {
            val url = avatarUrl?.takeIf { it.isNotBlank() }
            if (url != null) {
                val hdUrl = upgradeAvatarUrlToHd(url)
                val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                    BILIBILI_HEADERS else null
                var bmp = HttpUtil.downloadBitmap(hdUrl, headers = headers, timeoutMs = 8000)
                if (bmp == null && hdUrl != url) {
                    bmp = HttpUtil.downloadBitmap(url, headers = headers, timeoutMs = 8000)
                }
                // 先回收旧 bitmap 再覆盖 state 引用 —— 否则切 URL 时旧图永远不被回收
                releaseBitmap(previewBitmap)
                previewBitmap = bmp
                previewUrl = url
            } else {
                releaseBitmap(previewBitmap)
                previewBitmap = null
                previewUrl = null
            }
        } else {
            releaseBitmap(previewBitmap)
            previewBitmap = null
            previewUrl = null
        }
    }
    val displayBitmap = previewBitmap ?: fallbackBitmap
    if (show && displayBitmap != null) {
        WindowDialog(
            show = true,
            onDismissRequest = {
                releaseBitmap(previewBitmap)
                previewBitmap = null
                previewUrl = null
                onDismiss()
            },
            backgroundColor = MiuixTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BadgerRadius.xl)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BadgerSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    displayBitmap.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "头像大图",
                            modifier = Modifier.size(320.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                ) {
                    if (hasOriginal) {
                        TextButton(
                            text = "保存原图",
                            onClick = { onSaveOriginal(previewBitmap) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        text = "更换头像",
                        onClick = onPickNewAvatar,
                        modifier = Modifier.weight(if (hasOriginal) 1f else 2f),
                    )
                }
            }
        }
    }
}

/**
 * 把平台头像 URL 升级到高清接口。
 *
 * 列表项拉 100×100 缩略图;只有预览/保存时才调此函数取高清。
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
