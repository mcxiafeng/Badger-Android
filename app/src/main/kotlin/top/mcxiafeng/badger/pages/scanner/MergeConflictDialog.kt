package top.mcxiafeng.badger.pages.scanner

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.FieldMergeEntry
import top.mcxiafeng.badger.data.MergeChoice
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 合并冲突对话框
 *
 * 当扫描到已有联系人时，展示冲突字段，让用户选择用旧的/用新的/都保留。
 */
@Composable
internal fun MergeConflictDialog(
    existingContact: Contact,
    mergeEntries: List<FieldMergeEntry>,
    newName: String?,
    matchFields: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (updatedEntries: List<FieldMergeEntry>, chosenName: String?) -> Unit
) {
    Log.d("Tester", "MergeConflictDialog: contactId=${existingContact.id}, entries=${mergeEntries.size}, newName=$newName, matchFields=$matchFields")
    var entries by remember(mergeEntries) { mutableStateOf(mergeEntries) }
    var chosenName by remember {
        mutableStateOf(
            if (newName != null && newName != existingContact.name) MergeChoice.REPLACE else MergeChoice.KEEP
        )
    }

    WindowDialog(
        show = true,
        title = "更新联系人信息",
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 头部：已有联系人信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ContactAvatar(name = existingContact.name, avatarUrl = existingContact.avatarUrl, size = 40)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = existingContact.name,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 匹配字段提示
            if (matchFields.isNotEmpty()) {
                Text(
                    text = "匹配：${matchFields.joinToString("、")}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }

            // 名字冲突行
            if (newName != null && newName != existingContact.name) {
                Spacer(modifier = Modifier.height(8.dp))
                MergeNameRow(
                    existingName = existingContact.name,
                    newName = newName,
                    selected = chosenName,
                    onSelected = { chosenName = it }
                )
            }

            // 冲突字段列表（只显示有冲突的）
            val conflictEntries = entries.filter { it.existingValue != null }
            if (conflictEntries.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "新增了以下信息（自动添加）",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(conflictEntries, key = { it.fieldKey }) { entry ->
                        val index = entries.indexOf(entry)
                        ConflictFieldRow(
                            entry = entry,
                            onChoiceChange = { choice ->
                                entries = entries.toMutableList().apply {
                                    this[index] = entry.copy(selectedValue = choice)
                                }
                            }
                        )
                    }
                }
            }

            // 新增字段汇总
            val newEntries = entries.filter { it.existingValue == null }
            if (newEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "还会添加：${newEntries.joinToString("、") { it.fieldName }}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部按钮（最多2个）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "更新",
                    onClick = {
                        val resolvedName = if (chosenName == MergeChoice.REPLACE) newName else null
                        Log.d("Tester", "MergeConflictDialog: 确认合并 entries=${entries.size}, resolvedName=$resolvedName")
                        onConfirm(entries, resolvedName)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun MergeNameRow(
    existingName: String,
    newName: String,
    selected: MergeChoice,
    onSelected: (MergeChoice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "名字",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = "已有的",
                value = existingName,
                selected = selected == MergeChoice.KEEP,
                onClick = { onSelected(MergeChoice.KEEP) },
                modifier = Modifier.weight(1f)
            )
            ChoiceChip(
                label = "新的",
                value = newName,
                selected = selected == MergeChoice.REPLACE,
                onClick = { onSelected(MergeChoice.REPLACE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConflictFieldRow(
    entry: FieldMergeEntry,
    onChoiceChange: (MergeChoice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = entry.fieldName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.existingValue ?: "",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionChip("用旧的", entry.selectedValue == MergeChoice.KEEP, { onChoiceChange(MergeChoice.KEEP) }, Modifier.weight(1f))
            ActionChip("用新的", entry.selectedValue == MergeChoice.REPLACE, { onChoiceChange(MergeChoice.REPLACE) }, Modifier.weight(1f))
        }
        if (entry.selectedValue != MergeChoice.APPEND) {
            Text(
                text = "都保留",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onChoiceChange(MergeChoice.APPEND) }.padding(top = 2.dp)
            )
        }
        // 追加时预览新值
        if (entry.selectedValue == MergeChoice.REPLACE || entry.selectedValue == MergeChoice.APPEND) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.newValue ?: "",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackgroundVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackgroundVariant,
            fontWeight = if (selected) MiuixTheme.textStyles.subtitle.fontWeight else MiuixTheme.textStyles.footnote2.fontWeight
        )
    }
}
