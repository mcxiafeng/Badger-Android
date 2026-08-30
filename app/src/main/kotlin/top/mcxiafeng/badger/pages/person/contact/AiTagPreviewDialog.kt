package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * AI 标签预览 Dialog —— 重构后版本。
 *
 * 视觉与 [TagPickerDialog] 一致：两段 FlowRow chip 排版，
 * 已有标签（默认勾选）+ AI 建议的新标签（默认不勾）。
 *
 * 每个 chip 含 confidence 进度条（替换原"置信度 78%"文本）。
 *
 * @param candidates AI / 本地启发式返回的候选
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiTagPreviewDialog(
    show: Boolean,
    candidates: List<AiTagGenerator.TagCandidate>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<AiTagGenerator.TagCandidate>) -> Unit,
) {
    if (!show) return
    val cs = MiuixTheme.colorScheme

    val existing = candidates.filter { it.matchedExisting }
    val newOnes = candidates.filter { !it.matchedExisting }
    val checkedMap = remember(candidates) {
        mutableStateMapOf<String, Boolean>().apply {
            existing.forEach { put(it.name, true) }
            newOnes.forEach { put(it.name, false) }
        }
    }

    val selectedCount by remember { derivedStateOf { checkedMap.values.count { it } } }
    val summaryText = when {
        isLoading -> "生成中…"
        candidates.isEmpty() -> errorMessage ?: "AI 未返回结果"
        else -> "已选 $selectedCount / ${candidates.size}"
    }

    WindowDialog(
        show = true,
        title = "AI 推荐标签",
        summary = summaryText,
        onDismissRequest = onDismiss,
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = "正在调用 AI 生成标签...",
                        style = MiuixTheme.textStyles.body2,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
            }
            candidates.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = errorMessage ?: "AI 未返回任何候选标签",
                        style = MiuixTheme.textStyles.body2,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (existing.isNotEmpty()) {
                        SectionHeader("已有标签（推荐复用）")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            existing.forEach { c ->
                                val tag = Tag(
                                    name = c.name,
                                    color = c.color,
                                    createTime = System.currentTimeMillis(),
                                )
                                TagChipWithProgress(
                                    tag = tag,
                                    selected = checkedMap[c.name] == true,
                                    confidence = c.confidence,
                                    onClick = { checkedMap[c.name] = !(checkedMap[c.name] ?: false) },
                                )
                            }
                        }
                    }
                    if (newOnes.isNotEmpty()) {
                        SectionHeader("AI 建议的新标签")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            newOnes.forEach { c ->
                                val tag = Tag(
                                    name = c.name,
                                    color = c.color,
                                    createTime = System.currentTimeMillis(),
                                )
                                TagChipWithProgress(
                                    tag = tag,
                                    selected = checkedMap[c.name] == true,
                                    confidence = c.confidence,
                                    onClick = { checkedMap[c.name] = !(checkedMap[c.name] ?: false) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        DialogButtonRow(
            negativeText = "取消",
            positiveText = "采纳",
            onNegative = onDismiss,
            onPositive = {
                val selected = candidates.filter { checkedMap[it.name] == true }
                Log.d(TAG, "onConfirm: selected=${selected.size}/${candidates.size}")
                onConfirm(selected)
            },
            positiveEnabled = !isLoading && candidates.isNotEmpty(),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private const val TAG = "AiTagPreviewDialog"
