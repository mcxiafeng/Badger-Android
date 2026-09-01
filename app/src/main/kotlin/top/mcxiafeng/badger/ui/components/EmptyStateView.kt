package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Temporary compatibility wrapper retained until all legacy call sites are migrated. */
@Deprecated("Use BadgerEmptyState instead", level = DeprecationLevel.WARNING)
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) = BadgerEmptyState(icon, title, subtitle, actionLabel, onAction, modifier)
