package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一错误状态组件
 *
 * 提供图标 + 错误信息 + 重试按钮的完整错误展示。
 * 替代散落在各页面中的内联错误文本。
 *
 * @param icon 错误图标（默认为通用错误图标）
 * @param title 错误标题
 * @param message 错误详细信息
 * @param retryLabel 重试按钮文字（null 则不显示重试按钮）
 * @param onRetry 重试按钮点击回调
 * @param modifier Modifier
 */
@Composable
fun BadgerErrorState(
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    title: String = "出错了",
    message: String,
    retryLabel: String? = "重试",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.error,
            modifier = Modifier.size(BadgerSpacing.xxxl),
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        if (retryLabel != null && onRetry != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.xl))
            Button(
                onClick = onRetry,
            ) {
                Text(text = retryLabel)
            }
        }
    }
}

/**
 * 网络错误状态组件
 *
 * 专门用于网络连接失败的错误展示。
 *
 * @param message 错误信息（默认为网络错误提示）
 * @param retryLabel 重试按钮文字
 * @param onRetry 重试回调
 * @param modifier Modifier
 */
@Composable
fun BadgerNetworkErrorState(
    message: String = "网络连接失败，请检查网络设置",
    retryLabel: String = "重试",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BadgerErrorState(
        icon = Icons.Outlined.WifiOff,
        title = "网络错误",
        message = message,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
    )
}

/**
 * 紧凑错误状态组件
 *
 * 适用于内联错误展示，如列表项加载失败。
 *
 * @param message 错误信息
 * @param retryLabel 重试按钮文字（null 则不显示）
 * @param onRetry 重试回调
 * @param modifier Modifier
 */
@Composable
fun BadgerErrorStateCompact(
    message: String,
    retryLabel: String? = "重试",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (retryLabel != null && onRetry != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            TextButton(
                text = retryLabel,
                onClick = onRetry,
                modifier = Modifier.padding(BadgerSpacing.xs),
            )
        }
    }
}
