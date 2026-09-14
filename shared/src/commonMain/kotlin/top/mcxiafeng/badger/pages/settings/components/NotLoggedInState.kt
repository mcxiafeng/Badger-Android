package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import top.mcxiafeng.badger.ui.components.BadgerEmptyState

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
