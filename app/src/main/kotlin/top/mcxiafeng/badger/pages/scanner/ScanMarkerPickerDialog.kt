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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "ScanMarkerPickerDialog"
private const val DEFAULT_TAG_COLOR = 0xFF1976D2L

/**
 * 「本次扫描标记 Tag」选择器 —— 单选 Dialog。
 *
 * 点选已有 Tag 或「无」后立即回传并关闭；新建 Tag 时先完成创建，再回传选择结果。
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
    var newTagColor by remember { mutableStateOf(DEFAULT_TAG_COLOR) }
    var selectedId by remember(currentTagId) { mutableStateOf(currentTagId) }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tagRepository.observeAllTags().collect { tags ->
            allTags = tags
            isLoading = false
        }
    }

    fun dismissIfIdle() {
        if (!isCreating) onDismiss()
    }

    WindowDialog(
        show = show,
        title = "本次扫描标记",
        summary = if (selectedId == null) "不标记" else "标记本次扫描涉及的所有联系人",
        onDismissRequest = ::dismissIfIdle,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = BadgerSpacing.xs, vertical = BadgerSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
            ) {
                ScanMarkerChip(
                    text = "无",
                    selected = selectedId == null,
                    onClick = {
                        if (isCreating) return@ScanMarkerChip
                        selectedId = null
                        onPicked(null, "", DEFAULT_TAG_COLOR)
                        onDismiss()
                    },
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "加载标签中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    allTags.forEach { tag ->
                        ScanMarkerChip(
                            text = tag.name,
                            selected = selectedId == tag.id,
                            leadingColor = Color(tag.color),
                            onClick = {
                                if (isCreating) return@ScanMarkerChip
                                selectedId = tag.id
                                onPicked(tag.id, tag.name, tag.color)
                                onDismiss()
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.sm))

            if (showCreateField) {
                CreateScanMarkerContent(
                    name = newTagName,
                    color = newTagColor,
                    isCreating = isCreating,
                    onNameChange = { if (!isCreating) newTagName = it },
                    onColorChange = { if (!isCreating) newTagColor = it },
                    onCancel = {
                        if (isCreating) return@CreateScanMarkerContent
                        showCreateField = false
                        newTagName = ""
                    },
                    onCreate = {
                        val name = newTagName.trim()
                        if (name.isBlank() || isCreating) return@CreateScanMarkerContent

                        isCreating = true
                        val color = newTagColor
                        scope.launch {
                            try {
                                val newId = tagRepository.upsertTag(
                                    name,
                                    color,
                                    source = "manual",
                                )
                                Log.d(TAG, "新建标记 Tag: id=$newId name=$name")
                                onPicked(newId, name, color)
                                newTagName = ""
                                showCreateField = false
                                onDismiss()
                            } catch (e: Exception) {
                                Log.e(TAG, "upsertTag failed", e)
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BadgerRadius.md))
                        .clickable(enabled = !isCreating) { showCreateField = true }
                        .padding(vertical = BadgerSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建标签",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(BadgerSpacing.xs))
                    Text(
                        text = "新建标签",
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }

            Spacer(modifier = Modifier.height(BadgerSpacing.md))

            DialogButtonRow(
                negativeText = "关闭",
                positiveText = "完成",
                onNegative = ::dismissIfIdle,
                onPositive = ::dismissIfIdle,
            )
        }
    }
}
