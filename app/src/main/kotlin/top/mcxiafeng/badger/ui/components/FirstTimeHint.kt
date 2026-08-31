package top.mcxiafeng.badger.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.em
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val HINT_PREFS = "badger_hints"

/**
 * 首次使用提示组件
 *
 * 在指定位置显示一次性提示文字，引导用户发现隐藏功能。
 * 首次显示后自动标记为已读，后续不再显示。
 *
 * @param text 提示文字内容
 * @param hintKey SharedPreferences 中的唯一 key，如 "long_press_card"
 */
@Composable
fun FirstTimeHint(
    text: String,
    hintKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var shown by remember(hintKey) {
        mutableStateOf(
            context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
                .getBoolean("hint_shown_$hintKey", false)
        )
    }

    if (!shown) {
        Row(
            modifier = modifier
                .clickable(
                    onClickLabel = "关闭提示",
                    role = Role.Button,
                ) {
                    shown = true
                    context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean("hint_shown_$hintKey", true).apply()
                }
                .padding(vertical = BadgerSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(BadgerSize.iconXs)
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.xs))
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 1.33.em
            )
        }
    }
}
