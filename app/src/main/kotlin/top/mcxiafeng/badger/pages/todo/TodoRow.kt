package top.mcxiafeng.badger.pages.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import top.mcxiafeng.badger.data.todo.TodoBackgroundScale
import top.mcxiafeng.badger.data.todo.TodoPriority
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodoRow(
    row: TodoRowState, enabled: Boolean, viewModel: TodoViewModel, onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragActions: List<CustomAccessibilityAction>? = null,
) {
    val item = row.item
    val background = remember(item.backgroundId, viewModel) { viewModel.backgroundFile(item.backgroundId) }
    Card(modifier = modifier.fillMaxWidth(), cornerRadius = BadgerRadius.surface, insideMargin = PaddingValues()) {
        Box(Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget).aspectRatio(TODO_CARD_ASPECT_RATIO)) {
            if (background != null) TodoCardBackground(background, item.backgroundScale ?: TodoBackgroundScale.FILL,
                Modifier.matchParentSize())
            val rowModifier = if (dragActions == null) Modifier.padding(end = BadgerSpacing.md) else Modifier
            Row(Modifier.fillMaxSize().then(rowModifier), verticalAlignment = Alignment.CenterVertically) {
                TodoCompletionButton(row, enabled) { viewModel.toggle(item.id) }
                TodoRowText(row, Modifier.weight(1f).heightIn(min = BadgerSize.minimumTouchTarget)
                    .clickable(enabled = enabled, onClickLabel = "编辑待办") { onEdit(item.id) })
                if (dragActions != null) TodoDragHandle(item.title, dragActions)
            }
        }
    }
}

@Composable
private fun TodoDragHandle(title: String, actions: List<CustomAccessibilityAction>) {
    Box(Modifier.size(BadgerSize.minimumTouchTarget).semantics(mergeDescendants = true) {
        contentDescription = "拖动排序 $title"
        customActions = actions
    }, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun TodoCompletionButton(row: TodoRowState, enabled: Boolean, onToggle: () -> Unit) {
    val completed = row.item.completed
    IconButton(onClick = onToggle, enabled = enabled,
        minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget) {
        Icon(if (completed) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
            if (completed) "重新打开待办" else "完成待办",
            tint = row.type?.let { Color(it.color) } ?: MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun TodoRowText(row: TodoRowState, modifier: Modifier) {
    val priority = row.item.priority
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(modifier.semantics {
        if (priority != TodoPriority.LOW) stateDescription = "${priority.label}优先级"
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(priority.marker + row.title, style = MiuixTheme.textStyles.title4.copy(
                textDecoration = if (row.item.completed) TextDecoration.LineThrough else TextDecoration.None),
                color = if (row.item.completed) secondary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.summary.isNotBlank()) Text(row.summary, style = MiuixTheme.textStyles.footnote1, color = secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        row.avatars.forEach { TodoContactAvatar(it, MINI_AVATAR_SIZE) }
        if (row.hiddenContacts > 0) Text("…", color = secondary, style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.semantics { contentDescription = "另有 ${row.hiddenContacts} 位关联联系人" })
    }
}

private const val MINI_AVATAR_SIZE = 20
