package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.pages.person.contact.detail.BatchResolvedItem
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 批量导入社交平台 Dialog
 *
 * 两阶段流程:
 * 1. **输入**:多行文本框,每行一个 URL
 * 2. **结果**:展示解析结果列表(成功/失败),用户勾选后批量添加
 *
 * @param show 是否显示
 * @param onDismiss 关闭回调
 * @param onConfirm 确认批量添加回调,参数为 `List<BatchResolvedItem>` (仅 selected=true 的)
 * @param onBatchResolve 批量解析回调,由调用方触发 `viewModel.batchResolvePlatforms`
 */
@Composable
fun BatchImportPlatformsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<BatchResolvedItem>) -> Unit,
    onBatchResolve: suspend (List<String>) -> List<BatchResolvedItem>,
) {
    if (!show) return

    val scope = rememberCoroutineScope()

    // 输入文本
    var inputText by rememberSaveable { mutableStateOf("") }
    // 解析状态: null=未解析, emptyList=解析中, nonEmpty=已解析
    var results by rememberSaveable { mutableStateOf<List<BatchResolvedItem>?>(null) }
    // 各条目的勾选状态 (index → selected)
    var selectedMap by rememberSaveable { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    // 错误消息
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // 是否处于结果展示阶段
    val isResultPhase = results != null

    WindowDialog(
        show = true,
        title = if (isResultPhase) "解析结果" else "批量导入平台",
        summary = if (isResultPhase) "选择要添加的平台" else "每行粘贴一个链接",
        onDismissRequest = {
            onDismiss()
            // 重置状态
            inputText = ""
            results = null
            selectedMap = emptyMap()
            errorMessage = null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (!isResultPhase) {
                // ========== 输入阶段 ==========
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it; errorMessage = null },
                    label = "链接列表",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    singleLine = false,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "支持任意平台链接，服务端自动识别",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                DialogButtonRow(
                    positiveText = "解析",
                    onNegative = {
                        onDismiss()
                        inputText = ""
                        results = null
                        selectedMap = emptyMap()
                        errorMessage = null
                    },
                    onPositive = {
                        val urls = inputText.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        if (urls.isEmpty()) {
                            errorMessage = "请输入至少一个链接"
                            return@DialogButtonRow
                        }
                        // 标记为解析中 (emptyList sentinel)
                        results = emptyList()
                        scope.launch {
                            try {
                                val resolved = onBatchResolve(urls)
                                results = resolved
                                selectedMap = resolved.indices
                                    .filter { resolved[it].resolved != null }
                                    .associateWith { true }
                            } catch (e: Exception) {
                                results = null
                                errorMessage = "解析失败: ${e.message}"
                            }
                        }
                    },
                )
            } else {
                // ========== 结果阶段 ==========
                val items = results!!

                if (items.isEmpty()) {
                    // 解析中 (sentinel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val successCount = items.count { it.resolved != null }
                    val failCount = items.size - successCount

                    // 统计摘要
                    Text(
                        text = "成功 $successCount 条" + if (failCount > 0) "，失败 $failCount 条" else "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 结果列表
                    items.forEachIndexed { index, item ->
                        val isSelected = selectedMap[index] == true
                        val hasResolved = item.resolved != null
                        val def = FIELD_DEF_MAP[item.fieldKey]
                        val displayName = def?.displayName ?: item.fieldKey

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (hasResolved) Modifier.clickable {
                                        selectedMap = selectedMap.toMutableMap().apply {
                                            put(index, !isSelected)
                                        }
                                    } else Modifier
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 平台图标
                            PlatformIcon(
                                fieldKey = item.fieldKey,
                                color = if (hasResolved) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f),
                                sizeDp = 28f,
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 平台名 + URL
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasResolved) {
                                        val name = item.resolved!!.name
                                        if (name != null) "$displayName · $name" else displayName
                                    } else {
                                        "$displayName · 解析失败"
                                    },
                                    style = MiuixTheme.textStyles.body2,
                                    color = if (hasResolved) MiuixTheme.colorScheme.onBackground
                                    else MiuixTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.url,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // 勾选 / 失败图标
                            if (hasResolved) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "已选择",
                                            tint = MiuixTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "失败",
                                    tint = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 操作按钮
                    val selectedItems = items.filterIndexed { i, _ -> selectedMap[i] == true }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            text = "返回",
                            onClick = {
                                results = null
                                selectedMap = emptyMap()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                onConfirm(selectedItems)
                                onDismiss()
                                inputText = ""
                                results = null
                                selectedMap = emptyMap()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(text = "添加 ${selectedItems.size} 项")
                        }
                    }
                }
            }
        }
    }
}
