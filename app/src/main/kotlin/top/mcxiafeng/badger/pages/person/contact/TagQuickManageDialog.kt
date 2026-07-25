package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.ui.components.TagColorChangeDialog
import top.mcxiafeng.badger.ui.components.TagRenameDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.ui.graphics.Color

/**
 * 联系人详情页的"管理标签"入口 Dialog。
 *
 * 设计意图：
 * - 仅显示**当前联系人已绑定的标签**（避免详情页泄露全标签库）。
 * - 支持：改色 / 改名（这两个是标签本身的属性，影响该联系人展示）。
 * - **不支持删除 / 合并**——这是全局标签管理操作，统一跳顶级页。
 * - 底部一个"→ 打开全局标签管理"链接。
 *
 * 与 [TagManagerDialog] 的关系：旧版 TagManagerDialog 已删除，本 Dialog 是它的
 * 详情页版轻量替代。
 */
@Composable
internal fun TagQuickManageDialog(
    show: Boolean,
    contactId: Long,
    tagRepository: TagRepository,
    onDismiss: () -> Unit,
    onOpenFullManager: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    if (!show) return
    val scope = rememberCoroutineScope()

    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<Tag?>(null) }
    var colorTarget by remember { mutableStateOf<Tag?>(null) }

    LaunchedEffect(contactId) {
        try {
            tags = tagRepository.getTagsByContact(contactId)
        } catch (e: Exception) {
            Log.e(TAG, "load tags for contact failed", e)
            tags = emptyList()
        }
    }

    WindowDialog(
        show = true,
        title = "管理当前标签",
        summary = if (tags.isEmpty()) "当前联系人未绑定任何标签" else "共 ${tags.size} 个",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (tags.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前联系人还没有标签。\n返回后在「选择标签」中添加。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        QuickManageRow(
                            tag = tag,
                            onRename = { renameTarget = tag },
                            onColor = { colorTarget = tag },
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onOpenFullManager()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "打开全局标签管理（新建/删除/合并）",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(text = "完成", onClick = onDismiss)
            }
        }
    }

    renameTarget?.let { tag ->
        TagRenameDialog(
            show = true,
            tag = tag,
            onDismiss = { renameTarget = null },
            onSave = { newName ->
                scope.launch {
                    try {
                        tagRepository.renameTag(tag.id, newName)
                        Log.d(TAG, "rename ok: ${tag.id} -> $newName")
                        snackbarHostState?.let {
                            it.showSnackbar("已重命名")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "rename failed", e)
                        snackbarHostState?.let {
                            it.showSnackbar("重命名失败：${e.message ?: "未知错误"}")
                        }
                    }
                }
                renameTarget = null
            },
        )
    }

    colorTarget?.let { tag ->
        TagColorChangeDialog(
            show = true,
            tag = tag,
            onDismiss = { colorTarget = null },
            onSave = { argb ->
                scope.launch {
                    try {
                        tagRepository.setTagColor(tag.id, argb)
                        Log.d(TAG, "color ok: ${tag.id} -> 0x${argb.toString(16)}")
                        snackbarHostState?.let { it.showSnackbar("颜色已更新") }
                    } catch (e: Exception) {
                        Log.e(TAG, "color failed", e)
                        snackbarHostState?.let {
                            it.showSnackbar("改色失败：${e.message ?: "未知错误"}")
                        }
                    }
                }
                colorTarget = null
            },
        )
    }
}

@Composable
private fun QuickManageRow(
    tag: Tag,
    onRename: () -> Unit,
    onColor: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val dotColor = Color(tag.color).let { if (it.alpha == 0f) cs.primary else it }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MiuixTheme.textStyles.body1,
                color = cs.onSurface,
            )
            Text(
                text = if (tag.source == "ai") "AI 推荐" else "手动创建",
                style = MiuixTheme.textStyles.footnote2,
                color = cs.onSurfaceVariantSummary,
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "改名 ${tag.name}",
                tint = cs.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onColor, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ColorLens,
                contentDescription = "改色 ${tag.name}",
                tint = cs.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val TAG = "TagQuickManageDialog"