package top.mcxiafeng.badger.pages.scanner

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单个二维码的解析状态
 *
 * @property qrContent 原始二维码内容
 * @property networkResult 网络解析结果（null 表示未加载或加载中）
 * @property extractedInfo 本地解析结果
 * @property isLoading 是否正在加载网络信息
 * @property loadFailed 网络加载是否失败
 */
@Immutable
data class QrResolveState(
    val qrContent: String,
    val networkResult: NetworkResolveResult? = null,
    val extractedInfo: ExtractedContactInfo? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
) {
    /** 显示名称：网络昵称 > 本地解析名 > 截断的原始内容 */
    val displayName: String
        get() = networkResult?.nickname?.ifBlank { null }
            ?: extractedInfo?.name?.ifBlank { null }
            ?: qrContent.take(30).let { if (qrContent.length > 30) "$it..." else it }

    /** 平台标签和颜色 */
    val platformInfo: Pair<String, Color>?
        get() = networkResult?.type?.let { type ->
            PlatformAdapterRegistry.getTagInfo(type)?.let { (label, colorArgb) ->
                label to Color(colorArgb)
            }
        }

    /** 平台 ID 文本（格式：ID · xxx，平台名由标签展示） */
    val platformIdText: String?
        get() = networkResult?.contactMap?.let { map ->
            when (networkResult.type) {
                ContactType.QQ -> map["qq"]?.let { "ID · $it" }
                ContactType.QQGroup -> map["qqGroup"]?.let { "群号 · $it" }
                ContactType.Bilibili -> map["bilibili"]?.let { "ID · $it" }
                ContactType.WeChat -> map["wechat"]?.let { "ID · $it" }
                ContactType.TikTok -> map["douyin"]?.let { "ID · $it" }
                ContactType.Weibo -> map["weibo"]?.let { "ID · $it" }
                ContactType.GitHub -> map["github"]?.let { "ID · $it" }
                ContactType.Telegram -> map["telegram"]?.let { "ID \u00b7 @$it" }
                ContactType.TelegramGroup -> {
                    val v = map["telegramGroup"] ?: return@let null
                    if (v.startsWith("+") || v.startsWith("joinchat/")) "InviteCode \u00b7 $v" else "ID \u00b7 @$v"
                }
                ContactType.Xiaohongshu -> map["xiaohongshu"]?.let { "ID · $it" }
                ContactType.Facebook -> map["facebook"]?.let { "ID · $it" }
                ContactType.X -> map["x"]?.let { "ID · @$it" }
                ContactType.Website -> map["website"]?.let { it }
                ContactType.None -> null
            }
        }

    /** 头像 URL */
    val avatarUrl: String? get() = networkResult?.avatarUrl

    /** 是否已完成加载（成功或失败） */
    val isLoaded: Boolean get() = networkResult != null || loadFailed
}

/**
 * 可选字段（拍照模式合并展示用）
 */
@Immutable
data class SelectableField(
    val key: String,
    val label: String,
    val value: String
)

/**
 * 平台标签组件（旧版，ScanModeDialog 仍在使用）
 */
@Composable
internal fun PlatformTag(label: String, color: Color) {
    Text(
        text = label,
        color = Color.White,
        style = MiuixTheme.textStyles.footnote2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 重复标记标签（warning 样式：主题色字 + 半透明背景） */
@Composable
internal fun DuplicateTag() {
    Text(
        text = "重复",
        color = MiuixTheme.colorScheme.onSurface,
        style = MiuixTheme.textStyles.footnote2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 冲突标记标签（error 样式：白色字 + 主题 error 背景） */
@Composable
internal fun ConflictTag() {
    Text(
        text = "冲突",
        color = Color.White,
        style = MiuixTheme.textStyles.footnote2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MiuixTheme.colorScheme.error)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 冲突字段信息（同 key 不同值） */
@Immutable
data class ConflictFieldInfo(
    val existingValue: String,
    val newValue: String
)

/** 一个QR码在Compose UI坐标系中的包围框 */
@Immutable
data class QrBoundingBox(
    val content: String,
    val corners: List<Offset>,
    val isVisible: Boolean = true
)

/** 多码模式的全局检测状态 */
@Immutable
data class QrDetectionState(
    /** 每个码最近一次被检测到的时间戳，用于过期淘汰 */
    val contentLastSeen: Map<String, Long> = emptyMap(),
    val visibleBoundingBoxes: List<QrBoundingBox> = emptyList(),
    val visibleTextBoundingBoxes: List<QrBoundingBox> = emptyList(),
    val bitmapSize: Size = Size.Zero,
    val lastDetectionTime: Long = 0L
) {
    /** 当前有效的累积码集合（未过期的） */
    val accumulatedContents: Set<String> get() = contentLastSeen.keys

    companion object {
        /** 码离开画面后的保留时间（毫秒），超过后从累积集合移除 */
        const val EXPIRE_MS = 3000L
    }
}

/** 带坐标的QR码检测结果 */
@Immutable
data class QrCodeWithBounds(
    val content: String,
    val corners: List<Offset>
)

/** ML Kit 检测到的文字区域 */
@Immutable
data class TextBoundingBox(
    val corners: List<Offset>
)