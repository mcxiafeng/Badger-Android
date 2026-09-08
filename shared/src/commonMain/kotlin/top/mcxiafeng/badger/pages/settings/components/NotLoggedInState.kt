package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import top.mcxiafeng.badger.ui.components.BadgerEmptyState

/**
 * 设置域未登录空态（统一模板）。
 *
 * Devices / Notifications / ServerShortLinks / Dashboard / UserSettings 等需登录的页面共用。
 * 复用 [BadgerEmptyState]，主操作按钮"去登录"由调用方接 [onLogin]。
 */
@Composable
internal fun NotLoggedInState(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "未登录",
    subtitle: String = "登录后即可使用此功能",
) {
    BadgerEmptyState(
        icon = Lucide.UserRound,
        title = title,
        subtitle = subtitle,
        actionLabel = "去登录",
        onAction = onLogin,
        modifier = modifier,
    )
}
