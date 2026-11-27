package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import top.mcxiafeng.badger.ui.glass.itemPressFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    selected: Boolean? = null, onSelect: () -> Unit = {},
) {
    val item = row.item
    val interaction = remember { MutableInteractionSource() }
    val selectionModifier = if (selected == null) Modifier else Modifier
        .toggleable(value = selected, enabled = enabled, role = Role.Checkbox,
            interactionSource = interaction, indication = null, onValueChange = {
                Log.d("TodoRowTester", "Task row selection requested; selected=${!selected}")
                onSelect()
            }).semantics { contentDescription = "选择待办 ${item.title}" }
    Card(modifier = modifier.fillMaxWidth().itemPressFeedback(enabled, BadgerRadius.surface, motionEnabled = false),
        cornerRadius = BadgerRadius.surface, insideMargin = PaddingValues()) {
        val rowModifier = if (dragActions == null) Modifier.padding(end = BadgerSpacing.md) else Modifier
        Row(Modifier.fillMaxWidth().heightIn(min = TODO_ROW_MIN_HEIGHT).then(rowModifier).then(selectionModifier),
            verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) TodoSelectionMark(selected) else TodoCompletionButton(row, enabled) {
                Log.d("TodoRowTester", "Completion requested; completed=${item.completed}")
                viewModel.toggle(item.id)
            }
            val editModifier = if (selected != null) Modifier else Modifier
                .clickable(enabled = enabled, onClickLabel = "编辑待办", indication = null,
                    interactionSource = interaction) {
                    Log.d("TodoRowTester", "Edit requested")
                    onEdit(item.id)
                }
            TodoRowText(row, Modifier.weight(1f).heightIn(min = BadgerSize.minimumTouchTarget).then(editModifier))
            if (dragActions != null) TodoDragHandle(item.title, dragActions)
        }
    }
}

@Composable
private fun TodoSelectionMark(selected: Boolean) {
    Box(Modifier.size(BadgerSize.minimumTouchTarget), contentAlignment = Alignment.Center) {
        Icon(if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null, tint = MiuixTheme.colorScheme.primary)
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
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
        // Fill the free space so the contact avatars always hug the card's trailing edge.
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MiuixTheme.textStyles.title4.copy(
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

/**
 * Restores the previous card height (the old 1080×230 cover aspect ratio ≈ 76.7dp at this width)
 * without depending on the screen width, so rows stay consistent across devices.
 */
private val TODO_ROW_MIN_HEIGHT = 76.dp
