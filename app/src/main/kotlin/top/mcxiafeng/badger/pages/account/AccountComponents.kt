package top.mcxiafeng.badger.pages.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import top.mcxiafeng.badger.data.sync.ConflictDifference
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.data.sync.LocalImportItem
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AccountOperationError(message: String, isImport: Boolean, busy: Boolean, rebuild: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(message, color = MiuixTheme.colorScheme.error)
        if (isImport) TextButton(text = "重新建立预览", onClick = rebuild, enabled = !busy)
    }
}

@Composable
internal fun ImportScopeInfo(name: String, address: String) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text("导入到：$name", style = MiuixTheme.textStyles.title4)
        Text(address, style = MiuixTheme.textStyles.body2)
        Text("包括备注、扫描来源及图片，原件保留。有分歧的内容会等待你选择；名片夹只复制所选联系人的关联。", style = MiuixTheme.textStyles.body2)
    }
}

@Composable
internal fun LocalImportRow(item: LocalImportItem, enabled: Boolean, onSelect: (String, String) -> Unit) {
    Card(insideMargin = PaddingValues(BadgerSpacing.lg)) {
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            Text(item.title, style = MiuixTheme.textStyles.title4)
            Text(item.summary, style = MiuixTheme.textStyles.body2)
            item.options.forEach { option ->
                AccountSelectionRow(option.label, item.selection == option.key, enabled) { onSelect(item.key, option.key) }
            }
        }
    }
}

@Composable
internal fun DifferenceCard(item: ConflictDifference) {
    Card(insideMargin = PaddingValues(BadgerSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Text(item.field, style = MiuixTheme.textStyles.title4)
            DifferenceSide("本机", item.local, item.localImage)
            DifferenceSide("云端", item.remote, item.remoteImage)
        }
    }
}

@Composable
private fun DifferenceSide(title: String, value: String, image: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(title, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.primary)
        Text(value, style = MiuixTheme.textStyles.body1)
        if (image != null) AsyncImage(model = image, contentDescription = "$title 图片",
            modifier = Modifier.fillMaxWidth().height(BadgerSize.coverHeight).clip(RoundedCornerShape(BadgerRadius.inner)))
    }
}

@Composable
internal fun ConflictResolution(item: ConflictItem, busy: Boolean, onResolve: (Boolean, Boolean) -> Unit) {
    var choice by remember(item.key, item.differences, item.canAdoptLocal, item.notice) { mutableStateOf("") }
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        item.notice?.let { Text(it, style = MiuixTheme.textStyles.body2) }
        Text("选择分歧内容的处理方式，其他已合并的内容会保留。", style = MiuixTheme.textStyles.body2)
        AccountSelectionRow("分歧项采用本机内容", choice == "local", !busy && item.canAdoptLocal) { choice = "local" }
        AccountSelectionRow("分歧项采用云端内容", choice == "remote", !busy) { choice = "remote" }
        if (item.canKeepBoth) AccountSelectionRow("保留两位联系人", choice == "both", !busy) { choice = "both" }
        Button(onClick = { onResolve(choice != "remote", choice == "both") },
            enabled = choice.isNotBlank() && !busy && (choice != "local" || item.canAdoptLocal),
            modifier = Modifier.fillMaxWidth()) { Text("确认选择") }
    }
}

@Composable
private fun AccountSelectionRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(vertical = BadgerSpacing.xs), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MiuixTheme.colorScheme.primary,
                unselectedColor = MiuixTheme.colorScheme.onBackgroundVariant))
        Text(label, style = MiuixTheme.textStyles.body1, modifier = Modifier.weight(1f))
    }
}
