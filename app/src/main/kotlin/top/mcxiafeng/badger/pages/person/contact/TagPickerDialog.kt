package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.TagRepository
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 标签多选 Dialog
 *
 * 参考 [CollectionPickerDialog] 风格:WindowDialog + FlowRow + Checkbox 多选。
 *
 * @param tagRepository 标签仓库（注入以调用 upsertTag/createTag 等）
 * @param currentTagIds 联系人当前已关联的 Tag id 集合（dialog 默认勾选）
 * @param onConfirm 确认回调：(addedTagIds, removedTagIds) → 给 ViewModel 调 addTagToContact/removeTagFromContact
 * @param onManageTags 长按管理按钮触发,跳到 [TagManagerDialog]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerDialog(
    show: Boolean,
    tagRepository: TagRepository,
    currentTagIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (addedIds: Set<Long>, removedIds: Set<Long>) -> Unit,
    onManageTags: () -> Unit = {},
) {
    if (!show) return
    val scope = rememberCoroutineScope()
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // checkedMap: tagId -> 是否选中。初始按 currentTagIds 全勾。
    val checkedMap = remember(currentTagIds) { mutableStateMapOf<Long, Boolean>().apply {
        currentTagIds.forEach { put(it, true) }
    }}
    var showCreateField by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf(0xFF1976D2L) }

    LaunchedEffect(Unit) {
        tagRepository.observeAllTags().collect { list ->
            allTags = list
            // [修复防御]: 新建 Tag 后,新 Tag 不在 checkedMap 自动选中,需要手动勾,
            // 不会自动写入联系人(避免用户创建了一个 tag 立即被勾选导致意外关联)。
            isLoading = false
        }
    }

    WindowDialog(
        show = true,
        title = "选择标签",
        summary = if (checkedMap.values.count { it } > 0) "已选 ${checkedMap.values.count { it }} 个" else "",
        onDismissRequest = onDismiss
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (allTags.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("暂无标签,点击下方新建", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
        } else {
            // [修复防御]: 用户要求 Tag 按"段落堆叠"呈现——每个 Tag 一个整块,宽度按内容自然撑开,
            // 横向流式换行(FlowRow);整块点击切换 checked,选中态 primary @ alpha 0.12 整块高亮。
            // 不再使用 fillMaxWidth + Checkbox 的"左对齐 + 复选框"写法。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTags.forEach { tag ->
                    val checked = checkedMap[tag.id] == true
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (checked) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MiuixTheme.colorScheme.surfaceContainer
                            )
                            .clickable { checkedMap[tag.id] = !checked }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Color(tag.color) 在 Long = 0x00000000 时会变全透明,使用 copy(alpha) 避免 Color.Transparent 陷阱。
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(tag.color))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tag.name,
                                style = MiuixTheme.textStyles.body2,
                                color = if (checked) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                            if (checked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 管理标签按钮(点击展开"管理全部 Tag" Dialog)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onManageTags() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "管理标签",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "管理标签",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body2
            )
        }

        if (showCreateField) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = "新标签名",
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                TextButton(
                    text = "创建",
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            scope.launch {
                                try {
                                    val id = tagRepository.upsertTag(newTagName.trim(), newTagColor, source = "manual")
                                    // 创建后自动勾选新 Tag(用户体验:刚建的 tag 必然是想要的)
                                    checkedMap[id] = true
                                    Log.d("TagPickerDialog", "created new tag id=$id name=$newTagName")
                                    newTagName = ""
                                    showCreateField = false
                                } catch (e: Exception) {
                                    Log.e("TagPickerDialog", "upsertTag failed", e)
                                }
                            }
                        }
                    }
                )
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
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建标签",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新建标签",
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                text = "确定",
                onClick = {
                    val newCheckedIds = checkedMap.filter { it.value }.keys
                    val addedIds = newCheckedIds - currentTagIds
                    val removedIds = currentTagIds - newCheckedIds
                    Log.d("TagPickerDialog", "confirm: added=${addedIds.size} removed=${removedIds.size}")
                    onConfirm(addedIds, removedIds)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
