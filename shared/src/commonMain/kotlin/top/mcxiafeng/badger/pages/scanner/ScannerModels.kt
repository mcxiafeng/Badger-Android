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
import top.mcxiafeng.badger.network.PlatformAdapterRegistry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
data class QrResolveState(
    val qrContent: String,
    val networkResult: NetworkResolveResult? = null,
    val extractedInfo: ExtractedContactInfo? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
) {
    
    val displayName: String
        get() = networkResult?.nickname?.ifBlank { null }
            ?: extractedInfo?.name?.ifBlank { null }
            ?: qrContent.take(30).let { if (qrContent.length > 30) "$it..." else it }

    
    val platformInfo: Pair<String, Color>?
        get() = networkResult?.type?.let { type ->
            PlatformAdapterRegistry.getTagInfo(type)?.let { ti ->
                ti.label to Color(ti.color)
            }
        }

    
    val platformIdText: String?
        get() = networkResult?.contactMap?.let { map ->
            when (networkResult.type) {
                ContactType.QQ -> map["qq"]?.let { "ID · $it" }
                ContactType.QQGroup -> map["qqGroup"]?.let { "群号 · $it" }
                ContactType.Bilibili -> map["bilibili"]?.let { "ID · $it" }
                ContactType.WeChat -> map["wechat"]?.let { "ID · $it" }
                ContactType.Douyin -> map["douyin"]?.let { "ID · $it" }
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

    
    val avatarUrl: String? get() = networkResult?.avatarUrl

    
    val isLoaded: Boolean get() = networkResult != null || loadFailed
}

@Immutable
data class SelectableField(
    val key: String,
    val label: String,
    val value: String
)

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

@Composable
internal fun DuplicateTag() {
    Text(
        text = "重复",
        color = Color(0xFF7A5900),
        style = MiuixTheme.textStyles.footnote2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFB300).copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

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

@Immutable
data class ConflictFieldInfo(
    val existingValue: String,
    val newValue: String
)

@Immutable
data class QrBoundingBox(
    val content: String,
    val corners: List<Offset>,
    val isVisible: Boolean = true
)

@Immutable
data class QrDetectionState(
    
    val contentLastSeen: Map<String, Long> = emptyMap(),
    val visibleBoundingBoxes: List<QrBoundingBox> = emptyList(),
    val visibleTextBoundingBoxes: List<QrBoundingBox> = emptyList(),
    val bitmapSize: Size = Size.Zero,
    val lastDetectionTime: Long = 0L,
    
    val textBlockCount: Int = 0
) {
    
    val accumulatedContents: Set<String> get() = contentLastSeen.keys

    companion object {
        
        const val EXPIRE_MS = 3000L
    }
}

@Immutable
data class QrCodeWithBounds(
    val content: String,
    val corners: List<Offset>
)

@Immutable
data class TextBoundingBox(
    val corners: List<Offset>
)