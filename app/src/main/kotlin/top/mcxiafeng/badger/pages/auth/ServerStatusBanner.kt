package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val STATUS_ICON_SIZE = 18.dp
private const val PROBING_BG_ALPHA = 0.55f
private const val VERIFIED_BG_ALPHA = 0.6f
private const val UNVERIFIED_BG_ALPHA = 0.5f

/**
 * 服务器连接状态条 —— 三态常驻，点击均可打开修改对话框（移植自 dev 分支 AuthChrome）：
 * - 探测中：中性底 + 转圈（进页面自动探测）；
 * - 已连接：primaryContainer 蓝调展示当前地址（仍可点击修改）；
 * - 未验证：errorContainer 警示（探测失败或未探测）。
 */
@Composable
internal fun ServerStatusBanner(
    url: String,
    probing: Boolean,
    verified: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val (bgColor, fgColor) = when {
        probing -> colorScheme.surfaceVariant.copy(alpha = PROBING_BG_ALPHA) to colorScheme.onSurfaceVariantSummary
        verified -> colorScheme.primaryContainer.copy(alpha = VERIFIED_BG_ALPHA) to colorScheme.onPrimaryContainer
        else -> colorScheme.errorContainer.copy(alpha = UNVERIFIED_BG_ALPHA) to colorScheme.onErrorContainer
    }
    // Card 自带 onClick（项目规范：不在 modifier 上叠 clickable）
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.md),
        cornerRadius = BadgerRadius.card,
        colors = CardDefaults.defaultColors(color = bgColor, contentColor = fgColor),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                probing -> {
                    CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Text(text = "正在连接服务器…", style = MiuixTheme.textStyles.body2)
                }
                verified -> {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(STATUS_ICON_SIZE),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "已连接服务器", style = MiuixTheme.textStyles.body2)
                        Text(
                            text = url,
                            style = MiuixTheme.textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "修改",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.primary,
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(STATUS_ICON_SIZE),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                    Text(
                        text = "连接地址尚未验证，点此检查或修改",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
}
