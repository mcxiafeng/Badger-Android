package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Backward-compatible name for the standard empty-state component.
 *
 * Keep this shim while call sites are migrated to [BadgerEmptyState]; the
 * rendering implementation now has a single source of truth.
 */
@Deprecated(
    message = "Use BadgerEmptyState instead",
    replaceWith = ReplaceWith("BadgerEmptyState(icon, title, subtitle, actionLabel, onAction, modifier)"),
)
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BadgerEmptyState(
        icon = icon,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier,
    )
}
