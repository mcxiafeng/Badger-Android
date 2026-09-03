package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 字段上行 label(Text) —— 与 miuix TextField label placeholder 区分,用于语义标题("用户名" / "邮箱")。
 * 改为句首小写、字号一致,与 body2 区分靠字重。
 */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 字段级错误提示 —— 紧贴相关 TextField 下方内嵌,替换原模式内浮在按钮上方的错误信息。
 * [修复防御]: 与按钮点击解耦,避免「点登录 → 错误出现在按钮下 → 与按钮状态割裂」体验。
 */
@Composable
internal fun FieldError(hint: String) {
    Text(
        text = hint,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.error,
    )
}

/**
 * 主按钮内容 —— loading 时内嵌 [CircularProgressIndicator] + 提示文字,非 loading 只显示文字。
 * 集中此渲染以避免在 LoginContent/RegisterContent/ForgotPasswordContent 重复同一段 Row+Spacer+Text。
 */
@Composable
internal fun PrimaryButtonContent(isLoading: Boolean, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                size = 16.dp,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        }
        Text(text = if (isLoading) "处理中…" else label)
    }
}
