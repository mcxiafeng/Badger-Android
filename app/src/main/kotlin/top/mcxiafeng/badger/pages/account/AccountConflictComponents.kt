package top.mcxiafeng.badger.pages.account

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import coil3.compose.AsyncImage
import top.mcxiafeng.badger.data.sync.ConflictDifference
import top.mcxiafeng.badger.data.sync.ConflictItem
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AccountConflictTester"

@Composable
internal fun DifferenceCard(item: ConflictDifference) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(BadgerSpacing.cardPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            AccountSectionTitle(item.field)
            DifferenceSide("本机", item.local, item.localImage)
            DifferenceSide("云端", item.remote, item.remoteImage)
        }
    }
}

@Composable
private fun DifferenceSide(title: String, value: String, image: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        SelectionContainer { Text(value, style = MiuixTheme.textStyles.body1) }
        if (image != null) AsyncImage(model = image, contentDescription = "$title 图片",
            modifier = Modifier.fillMaxWidth().height(BadgerSize.coverHeight).clip(BadgerShapes.Surface))
    }
}

@Composable
internal fun ConflictResolution(item: ConflictItem, busy: Boolean, onResolve: (Boolean, Boolean) -> Unit) {
    // 裁决只针对本次展示的差异，离开详情后必须重新选择，不能恢复旧裁决。
    var choice by remember(item) {
        mutableStateOf("")
    }
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
        item.notice?.let {
            Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            ConflictChoices(item, busy, choice) {
                choice = it
                Log.d(TAG, "已选择冲突处理方式：$it")
            }
        }
        Button(onClick = {
            Log.d(TAG, "确认处理分歧，保留两份=${choice == "both"}")
            onResolve(choice != "remote", choice == "both")
        }, enabled = !busy && when (choice) {
            "local" -> item.canAdoptLocal
            "remote" -> true
            "both" -> item.canKeepBoth
            else -> false
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) { Text("确认选择") }
    }
}

@Composable
private fun ConflictChoices(item: ConflictItem, busy: Boolean, choice: String, onSelect: (String) -> Unit) {
    AccountSelectionRow("分歧项采用本机内容", choice == "local", !busy && item.canAdoptLocal) { onSelect("local") }
    AccountSelectionRow("分歧项采用云端内容", choice == "remote", !busy) { onSelect("remote") }
    if (item.canKeepBoth) AccountSelectionRow("保留两位联系人", choice == "both", !busy) { onSelect("both") }
}
