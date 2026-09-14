package top.mcxiafeng.badger.pages.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.QAuxvConflictAction
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.miuixShape
import top.mcxiafeng.badger.utils.BadgerLog

@Composable
fun QAuxvProgressDialog(
    title: String,
    summary: String,
    show: Boolean,
) {
    if (show) {
        WindowDialog(
            show = true,
            title = title,
            summary = summary,
            onDismissRequest = {  },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun QAuxvPreviewDialog(
    state: QAuxvImportState.Preview,
    show: Boolean,
    onToggleCheck: (Long, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: (List<QAuxvFriendEntry>) -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    val checkedCount = state.checkedUins.size
    val conflictCount = state.entries.count { it.uin in state.existingContactIdByUin }
    WindowDialog(
        show = true,
        title = "从 QAuxiliary 导入（预览）",
        
        summary = "共 ${state.entries.size} 条，其中 $conflictCount 条 QQ 号已存在",
        onDismissRequest = onCancel,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "全选",
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "全不选",
                    onClick = onDeselectAll,
                    modifier = Modifier.weight(1f),
                )
            }

            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.entries, key = { it.uin }) { entry ->
                    val isChecked = entry.uin in state.checkedUins
                    val isExisting = entry.uin in state.existingContactIdByUin
                    PreviewRow(
                        entry = entry,
                        isChecked = isChecked,
                        isExisting = isExisting,
                        onToggle = { onToggleCheck(entry.uin, !isChecked) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            
            Text(
                text = "已勾选 $checkedCount / ${state.entries.size} 条",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body1,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )

            DialogButtonRow(
                negativeText = "取消",
                positiveText = if (checkedCount == 0) "导入 (0)" else "导入 ($checkedCount)",
                onNegative = onCancel,
                onPositive = {
                    val selected = state.entries.filter { it.uin in state.checkedUins }
                    onConfirm(selected)
                },
                positiveEnabled = checkedCount > 0,
            )
        }
    }
}

@Composable
private fun PreviewRow(
    entry: QAuxvFriendEntry,
    isChecked: Boolean,
    isExisting: Boolean,
    onToggle: () -> Unit,
) {
    
    
    val avatarUrl = remember(entry.uin) {
        ContactRepositoryImpl.qqAvatarUrl(entry.uin)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(miuixShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            
            ContactAvatar(
                name = entry.displayName,
                avatarUrl = avatarUrl,
                size = 36,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "QQ ${entry.uin} · ${entry.statusLabel}${if (isExisting) " · 已存在" else ""}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (isExisting) MiuixTheme.colorScheme.error
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Checkbox(
                state = if (isChecked) ToggleableState.On else ToggleableState.Off,
                onClick = onToggle,
            )
        }
    }
}

@Composable
fun QAuxvConflictDialog(
    show: Boolean,
    selectedEntries: List<QAuxvFriendEntry>,
    existingContactIdByUin: Map<Long, Long>,
    onResolve: (List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>) -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    
    val conflicts = selectedEntries.filter { it.uin in existingContactIdByUin }
    val conflictCount = conflicts.size
    
    val actions = remember(conflictCount) { mutableStateMapOf<Long, QAuxvConflictAction>() }
    conflicts.forEach { if (actions[it.uin] == null) actions[it.uin] = QAuxvConflictAction.Skip }

    WindowDialog(
        show = true,
        title = "处理重复 QQ（$conflictCount 条）",
        summary = if (conflictCount == 0) "无冲突项，可直接导入。" else "已勾选条目中有 $conflictCount 条 QQ 号已在 Badger 中存在，请选择处理方式。",
        onDismissRequest = onCancel,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (conflictCount == 0) {
                DialogButtonRow(
                    positiveText = "导入",
                    onNegative = onCancel,
                    onPositive = {
                        
                        onResolve(
                            selectedEntries.map { entry ->
                                Triple(entry, null, QAuxvConflictAction.InsertAnyway)
                            }
                        )
                    },
                )
                return@Column
            }
            
            BatchActionsRow(
                onPick = { picked ->
                    actions.keys.forEach { actions[it] = picked }
                                    },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(conflicts, key = { it.uin }) { entry ->
                    val current = actions[entry.uin] ?: QAuxvConflictAction.Skip
                    ConflictRow(
                        entry = entry,
                        current = current,
                        onPick = { picked -> actions[entry.uin] = picked },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "应用",
                onNegative = onCancel,
                onPositive = {
                    
                    val decisions = selectedEntries.map { entry ->
                        val action = if (entry.uin in existingContactIdByUin) {
                            actions[entry.uin] ?: QAuxvConflictAction.Skip
                        } else QAuxvConflictAction.InsertAnyway
                        Triple(entry, existingContactIdByUin[entry.uin], action)
                    }
                    onResolve(decisions)
                },
            )
        }
    }
}

@Composable
private fun ConflictRow(
    entry: QAuxvFriendEntry,
    current: QAuxvConflictAction,
    onPick: (QAuxvConflictAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(miuixShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry.displayName,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "QQ ${entry.uin} · ${entry.statusLabel}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip(
                    label = "跳过",
                    selected = current == QAuxvConflictAction.Skip,
                    onClick = { onPick(QAuxvConflictAction.Skip) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceChip(
                    label = "替换",
                    selected = current == QAuxvConflictAction.Replace,
                    onClick = { onPick(QAuxvConflictAction.Replace) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceChip(
                    label = "仍新增",
                    selected = current == QAuxvConflictAction.InsertAnyway,
                    onClick = { onPick(QAuxvConflictAction.InsertAnyway) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        text = if (selected) "✓ $label" else label,
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.textButtonColorsPrimary()
        } else ButtonDefaults.textButtonColors(),
    )
}

@Composable
private fun BatchActionsRow(
    onPick: (QAuxvConflictAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            text = "全部跳过",
            onClick = { onPick(QAuxvConflictAction.Skip) },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "全部替换",
            onClick = { onPick(QAuxvConflictAction.Replace) },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "全部新增",
            onClick = { onPick(QAuxvConflictAction.InsertAnyway) },
            modifier = Modifier.weight(1f),
        )
    }
}