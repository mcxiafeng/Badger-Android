package top.mcxiafeng.badger.pages.person

import android.util.Log
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

/**
 * Parsing / Importing 通用进度 Dialog。
 */
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
            onDismissRequest = { /* 禁止外部关闭 */ },
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

/**
 * 预览 Dialog：列出所有解析出的 entry，每行一个 Checkbox 让用户决定是否导入。
 *
 * @param onConfirm(selected) 用户点「导入选中」时回调，回调列表已按原顺序排好
 * @param onCancel 用户点「取消」时回调
 * @param onSelectAll / onDeselectAll 用户点「全选 / 全不选」时回调
 */
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
        // [修复防御]: 把"已勾选 N"和"已存在 M"从 summary 拆走，避免和标题一起挤在窄 Dialog 头部。
        summary = "共 ${state.entries.size} 条，其中 $conflictCount 条 QQ 号已存在",
        onDismissRequest = onCancel,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 全选 / 全不选 row
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

            // 列表：受控高度，超出可滚动
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

            // [修复防御]: 当前选中条数独立成行，居中加粗展示，与按钮错开避免排版挤压。
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
    // [修复防御]: 头像走 ContactAvatar(avatarUrl = q1.qlogo.cn)，预览阶段就可以看到真实 QQ 头像，
    // 不再退化为首字符圆形占位。
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
            // 头像：走远程 QQ 头像 URL，加载中/失败时由 ContactAvatar 自动回退首字符
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

/**
 * 冲突解决 Dialog：仅对 selected 中 QQ 号已存在的项目让用户选 Skip/Replace/InsertAnyway。
 *
 * @param selectedEntries 用户在预览 Dialog 中勾选要导入的 entry 列表
 * @param existingContactIdByUin 来自 Preview state，uin → contactId
 * @param onResolve(decisions) 用户点「应用」时回调，decisions 按 selectedEntries 顺序
 * @param onCancel 用户点「取消」时回调
 */
@Composable
fun QAuxvConflictDialog(
    show: Boolean,
    selectedEntries: List<QAuxvFriendEntry>,
    existingContactIdByUin: Map<Long, Long>,
    onResolve: (List<Triple<QAuxvFriendEntry, Long?, QAuxvConflictAction>>) -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    // 仅冲突项需要决定；非冲突项 → InsertAnyway（一次性新增）
    val conflicts = selectedEntries.filter { it.uin in existingContactIdByUin }
    val conflictCount = conflicts.size
    // 每个 conflict 默认动作 = Skip；用户在 row 上点 Replace / InsertAnyway 可覆盖
    val actions = remember(conflictCount) { mutableStateMapOf<Long, QAuxvConflictAction>() }
    conflicts.forEach { actions.putIfAbsent(it.uin, QAuxvConflictAction.Skip) }

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
                        // 非冲突，全部 InsertAnyway
                        onResolve(
                            selectedEntries.map { entry ->
                                Triple(entry, null, QAuxvConflictAction.InsertAnyway)
                            }
                        )
                    },
                )
                return@Column
            }
            // 顶部批量操作 row：一键把所有冲突项设为同一 action，再单独调整例外行
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
                    // conflicts 按用户选择；非冲突全部 InsertAnyway；保持 selectedEntries 原顺序
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

/**
 * 冲突 Dialog 顶部的一键批量操作行：把所有冲突项设为同一 action，
 * 用户可后续对个别行单独调整。和行内 chip 配合形成"先粗后细"的选择节奏。
 */
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