package top.mcxiafeng.badger.pages.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MAX_TAG_DOTS = 3
private val TAG_DOT_SIZE = 8.dp

@Composable
internal fun MyProfileHeader(profile: UserProfile?, onClick: () -> Unit) {
    BasicComponent(title = "我的名片", summary = profile?.name?.let { "查看和编辑 $it 的信息" } ?: "查看和编辑个人信息",
        startAction = { ContactAvatar(name = profile?.name ?: "用户", avatarPath = profile?.avatarPath, size = 40) },
        modifier = Modifier.padding(bottom = BadgerSpacing.xl), onClick = onClick)
}

@Composable
internal fun PersonContactItem(
    contact: Contact, dots: List<Tag>, selection: PersonSelectionState, onClick: () -> Unit, onShare: () -> Unit,
) {
    val isSelected = contact.id in selection.ids
    var showMenu by remember(selection.enabled) { mutableStateOf(false) }
    Box {
        BasicComponent(
            title = contact.name,
            startAction = { ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, avatarPath = contact.avatarPath) },
            endActions = {
                if (selection.enabled) Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "已选" else "未选",
                    tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(BadgerSpacing.xl),
                ) else PersonTagDots(dots)
            },
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (selection.enabled) selected = isSelected
            }.combinedClickable(
                onClick = onClick,
                onLongClickLabel = "打开联系人操作菜单",
                onLongClick = if (selection.enabled) null else { { showMenu = true } },
            ),
        )
        if (!selection.enabled) PersonContactMenu(showMenu, onDismiss = { showMenu = false }, onShare = {
            showMenu = false
            onShare()
        })
    }
}

@Composable
private fun PersonContactMenu(show: Boolean, onDismiss: () -> Unit, onShare: () -> Unit) {
    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.TopStart,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            DropdownImpl(text = "分享", optionSize = 1, isSelected = false, index = 0,
                onSelectedIndexChange = { onShare() })
        }
    }
}

@Composable
private fun PersonTagDots(dots: List<Tag>) {
    Row(horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        dots.take(MAX_TAG_DOTS).forEach { tag ->
            Box(Modifier.size(TAG_DOT_SIZE).clip(CircleShape).background(Color(tag.color)))
        }
        if (dots.size > MAX_TAG_DOTS) Text("+${dots.size - MAX_TAG_DOTS}", style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun PersonSectionHeader(section: PersonContactSection) {
    Row(Modifier.fillMaxWidth().padding(start = BadgerSpacing.lg, top = BadgerSpacing.md, bottom = BadgerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        if (section.groupId != 0L) {
            Box(Modifier.size(TAG_DOT_SIZE).clip(CircleShape).background(Color(section.groupColor)))
            Spacer(Modifier.width(BadgerSpacing.sm))
        }
        Text(if (section.groupId == 0L) "匹配名字（${section.count}）" else "标签「${section.groupName}」（${section.count}）",
            style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun PersonLetterHeader(letter: String) {
    Text(letter, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = BadgerSpacing.lg, top = BadgerSpacing.sm, bottom = BadgerSpacing.xs))
}

@Composable
internal fun PersonLoadStatus(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(BadgerSpacing.xl), contentAlignment = Alignment.Center) {
        Text("加载失败，点击重试", Modifier.clickable { onRetry() }, color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
internal fun PersonEmptyState(onAddContact: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (onAddContact == null) "未找到联系人" else "还没有联系人", style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (onAddContact != null) {
            Spacer(Modifier.height(BadgerSpacing.sm))
            Text("添加联系人", Modifier.clickable { onAddContact() }.padding(BadgerSpacing.sm), color = MiuixTheme.colorScheme.primary)
        }
    }
}

/** Nonzero placeholder height prevents the lazy layout from composing every unloaded position. */
@Composable
internal fun PersonPlaceholder() { Spacer(Modifier.fillMaxWidth().height(BadgerSize.minimumTouchTarget + BadgerSpacing.xl)) }
