package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.platform.contentColorFor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck

/**
 * 名片夹详情页 — 空联系人列表占位
 */
@Composable
internal fun CollectionDetailEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "暂无联系人",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1
        )
    }
}

/**
 * 名片夹详情页 — 联系人列表条目（计数 + 联系人卡片）
 */
internal fun LazyListScope.collectionDetailContactList(
    contacts: List<Contact>,
    isInSelectionMode: Boolean,
    selectedContactIds: Set<Long>,
    memberCounts: Map<Long, Int>,
    collection: CardCollection?,
    onContactClick: (contact: Contact, isSelected: Boolean) -> Unit,
    onContactLongClick: (contact: Contact) -> Unit,
) {
    item {
        Text(
            text = "共 ${contacts.size} 位联系人",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
    }
    items(
        contacts,
        key = { it.id },
        contentType = { _ -> "contact" }
    ) { contact ->
        val isSelected = isInSelectionMode && contact.id in selectedContactIds
        Box(
            modifier = Modifier.combinedClickable(
                onClick = { onContactClick(contact, isSelected) },
                onLongClick = { onContactLongClick(contact) }
            )
        ) {
            BasicComponent(
                title = contact.name,
                summary = contact.note,
                modifier = Modifier.then(
                    if (isSelected) Modifier.background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    else Modifier
                ),
                startAction = {
                    ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 40)
                },
                endActions = {
                    if (isInSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Lucide.CircleCheck else Lucide.Circle,
                            contentDescription = if (isSelected) "已选" else "未选",
                            tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    val count = memberCounts[contact.id] ?: 1
                    if (count > 1) {
                        val badgeColor = collection?.dominantColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary
                        val badgeTextColor = collection?.dominantColor?.let { contentColorFor(it) } ?: Color.White
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                color = badgeTextColor,
                                style = MiuixTheme.textStyles.footnote2
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            )
        }
    }
}
