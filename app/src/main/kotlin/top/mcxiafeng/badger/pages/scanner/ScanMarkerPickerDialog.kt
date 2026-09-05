package top.mcxiafeng.badger.pages.scanner

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/**
 * 「本次扫描标记 Tag」选择器 —— 单选 Dialog。
 *
 * 与 TagPickerDialog 的关键差异:
 * - 单选 (标记 Tag 只能有一个);不需要「取消/确定」按钮,点 chip 即选中即确认
 * - 提供「无」chip(显式清空)
 * - 提供新建入口(输入名字 + 选颜色,与 TagPickerDialog 类似的体验)
 *
 * @param show 是否显示
 * @param tagRepository 注入:用于 observeAllTags + upsertTag
 * @param currentTagId 当前已选 Tag.id(给 chip 标记选中态);`null` 表示「无」
 * @param onDismiss 关闭回调
 * @param onPicked 用户点选 chip 后的回调:`Pair(tagId, tagName, tagColor)` 或 `Triple(null, "", default)` 表示「无」
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScanMarkerPickerDialog(
    show: Boolean,
    tagRepository: TagRepository,
    currentTagId: Long?,
    onDismiss: () -> Unit,
    onPicked: (tagId: Long?, tagName: String, tagColor: Long) -> Unit,
) {
    if (!show) return
    val scope = rememberCoroutineScope()

    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateField by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf(0xFF1976D2L) }
    // 「临时选中」用于 UI 高亮;点 chip 后才通过 onPicked 回传
    var selectedId by remember(currentTagId) { mutableStateOf(currentTagId) }

    LaunchedEffect(Unit) {
        tagRepository.observeAllTags().collect { list ->
            allTags = list
            isLoading = false
        }
    }

    WindowDialog(
        show = true,
        title = "本次扫描标记",
        summary = if (selectedId == null) "不标记" else "标记本次扫描涉及的所有联系人",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 「无」chip — 始终排在第一个,显式表达"不标记"语义
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 「无」chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selectedId == null) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                        .clickable {
                            selectedId = null
                            onPicked(null, "", 0xFF1976D2L)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "无",
                        style = MiuixTheme.textStyles.body2,
                        color = if (selectedId == null) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface,
                    )
                }
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        Text("加载标签中...", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                } else {
                    allTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedId == tag.id) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceContainer
                                )
                                .clickable {
                                    selectedId = tag.id
                                    onPicked(tag.id, tag.name, tag.color)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.color))
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = tag.name,
                                    style = MiuixTheme.textStyles.body2,
                                    color = if (selectedId == tag.id) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 「新建标签」入口 — 完全复用 TagPickerDialog 的展开体验
            if (showCreateField) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = "新标签名(代表本次扫描的场合)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    ColorPalette(
                        color = Color(newTagColor),
                        onColorChanged = { newTagColor = it.value.toLong() },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                showCreateField = false
                                newTagName = ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "创建",
                            onClick = {
                                val name = newTagName.trim()
                                if (name.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            val newId = tagRepository.upsertTag(name, newTagColor, source = "manual")
                                            Log.d(TAG, "新建标记 Tag: id=$newId name=$name")
                                            onPicked(newId, name, newTagColor)
                                            newTagName = ""
                                            showCreateField = false
                                            onDismiss()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "upsertTag failed", e)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateField = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = "新建标签",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "新建标签",
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            DialogButtonRow(
                negativeText = "关闭",
                positiveText = "完成",
                onNegative = onDismiss,
                onPositive = onDismiss,
            )
        }
    }
}

private const val TAG = "ScanMarkerPickerDialog"
