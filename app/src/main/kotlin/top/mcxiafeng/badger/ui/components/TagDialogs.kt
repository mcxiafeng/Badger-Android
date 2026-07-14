package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.pages.settings.colorCompose
import top.mcxiafeng.badger.pages.settings.toArgbLong
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 标签管理页用的子 Dialog 集合（顶级页 / 详情页 Dialog 共用）。
 *
 * 全部遵循 feedback_dialog_rules.md：
 * - 使用 Pattern A (`if (showXxx) WindowDialog(show = true, ...)`)。
 * - 按钮 ≤ 2 个；多于 2 个的诉求通过"红字文字链接 + 二次确认"实现。
 * - dismiss / 取消 / 确认 / 选项 onClick 全部置位 flag（这里 flag 由调用方持有）。
 */

private const val DLG_LOG = "TagDialogs"

// ========== 改名 Dialog ==========

@Composable
fun TagRenameDialog(
    show: Boolean,
    tag: Tag,
    onDismiss: () -> Unit,
    onSave: (newName: String) -> Unit,
) {
    if (!show) return
    var name by remember(tag.id) { mutableStateOf(tag.name) }

    WindowDialog(
        show = true,
        title = "重命名标签",
        summary = tag.name,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "标签名",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "保存",
                onNegative = onDismiss,
                onPositive = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank() && trimmed != tag.name) {
                        onSave(trimmed)
                    } else if (trimmed.isBlank()) {
                        Log.d(DLG_LOG, "rename: blank input ignored")
                    } else {
                        onDismiss()
                    }
                },
            )
        }
    }
}

// ========== 换色 Dialog ==========

@Composable
fun TagColorChangeDialog(
    show: Boolean,
    tag: Tag,
    onDismiss: () -> Unit,
    onSave: (colorArgb: Long) -> Unit,
) {
    if (!show) return
    var draftColor by remember(tag.id) { mutableStateOf(Color(tag.color)) }

    WindowDialog(
        show = true,
        title = "修改颜色",
        summary = tag.name,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(draftColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "色点",
                    style = MiuixTheme.textStyles.body1,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(12.dp))
            // [修复防御]: 原实现用 (value shr 32).toLong() and 0xFFFFFFFFL 提取 ARGB，
            // 在 Compose 1.6+ 上 ULong 处理有溢出风险。改用 Color.toArgb() 安全转换。
            ColorPalette(
                color = draftColor,
                onColorChanged = { draftColor = it },
            )
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "确定",
                onNegative = onDismiss,
                onPositive = { onSave(draftColor.toArgbLong()) },
            )
        }
    }
}

// ========== 删除选项 Dialog ==========
// 设计：
// - 主区：标签预览 + 风险说明
// - 按钮：取消 / 合并到… （最多 2 个按钮，符合规范）
// - "强制删除（不保留关联）" 以红色文字链接形式出现，点击二次确认。

@Composable
fun TagDeleteChoiceDialog(
    show: Boolean,
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirmMerge: () -> Unit,
    onConfirmForceDelete: () -> Unit,
) {
    if (!show) return
    var showForceConfirm by remember { mutableStateOf(false) }

    WindowDialog(
        show = true,
        title = "删除标签",
        summary = "「${tag.name}」将被处理",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tag.colorCompose)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = tag.name,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = "此标签将从所有联系人上移除。如需保留使用记录，请先合并到其他标签。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "合并到…",
                onNegative = onDismiss,
                onPositive = onConfirmMerge,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "强制删除（不保留关联）",
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showForceConfirm = true }
                    .padding(vertical = 10.dp),
            )
        }
    }

    if (showForceConfirm) {
        WindowDialog(
            show = true,
            title = "确认强制删除",
            summary = "「${tag.name}」",
            onDismissRequest = { showForceConfirm = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "强制删除将立即清除此标签，所有联系人上的关联记录也会一并移除，无法撤销。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.size(16.dp))
                DialogButtonRow(
                    negativeText = "取消",
                    positiveText = "强制删除",
                    onNegative = { showForceConfirm = false },
                    onPositive = {
                        showForceConfirm = false
                        onConfirmForceDelete()
                    },
                    isDestructive = true,
                )
            }
        }
    }
}

// ========== 合并目标选择 Dialog ==========

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagMergeTargetPickerDialog(
    show: Boolean,
    sourceTag: Tag,
    candidates: List<Tag>,
    onDismiss: () -> Unit,
    onPicked: (toTag: Tag) -> Unit,
) {
    if (!show) return

    WindowDialog(
        show = true,
        title = "合并到",
        summary = "把「${sourceTag.name}」的使用记录转移到",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (candidates.isEmpty()) {
                Text(
                    text = "没有其他标签可以合并。先创建一个新标签或选强制删除。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    candidates.forEach { cand ->
                        TagCandidateChip(tag = cand, onClick = { onPicked(cand) })
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "返回",
                positiveText = "取消合并",
                onNegative = onDismiss,
                onPositive = onDismiss,
            )
        }
    }
}

@Composable
private fun TagCandidateChip(tag: Tag, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tag.colorCompose)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

// ========== 新建标签 Dialog ==========

@Composable
fun TagCreateDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, colorArgb: Long) -> Unit,
) {
    if (!show) return
    var name by remember { mutableStateOf("") }
    val presetColors = remember {
        listOf(
            0xFF1976D2L, 0xFF388E3CL, 0xFFD32F2FL, 0xFFF57C00L,
            0xFF7B1FA2L, 0xFF00838FL, 0xFF5D4037L, 0xFF455A64L,
            0xFFC2185BL, 0xFFAFB42BL, 0xFFE64A19L, 0xFF424242L,
        )
    }
    var selectedColor by remember { mutableStateOf(presetColors.first()) }

    WindowDialog(
        show = true,
        title = "新建标签",
        summary = "为标签设置名称和颜色",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "标签名",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.size(12.dp))
            ColorPalette(
                color = Color(selectedColor),
                onColorChanged = { selectedColor = it.toArgbLong() },
            )
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "创建",
                positiveEnabled = name.trim().isNotEmpty(),
                onNegative = onDismiss,
                onPositive = { onCreate(name, selectedColor) },
            )
        }
    }
}

// ========== 批量操作辅助：颜色选择 Dialog ==========

@Composable
fun BatchColorPickerDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (colorArgb: Long) -> Unit,
) {
    if (!show) return
    var draftColor by remember { mutableStateOf(Color(0xFF1976D2L)) }

    WindowDialog(
        show = true,
        title = "批量修改颜色",
        summary = "应用到选中的所有标签",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(draftColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "新颜色预览",
                    style = MiuixTheme.textStyles.body1,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(12.dp))
            ColorPalette(
                color = draftColor,
                onColorChanged = { draftColor = it },
            )
            Spacer(Modifier.size(16.dp))
            DialogButtonRow(
                negativeText = "取消",
                positiveText = "应用",
                onNegative = onDismiss,
                onPositive = { onPick(draftColor.toArgbLong()) },
            )
        }
    }
}
