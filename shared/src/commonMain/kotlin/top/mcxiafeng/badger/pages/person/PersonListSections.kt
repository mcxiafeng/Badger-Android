package top.mcxiafeng.badger.pages.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.model.LetterCount
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateCompact
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** U13：字母索引热区宽度 ≥ 48dp */
internal val LetterIndexHitWidth = 48.dp

/**
 * 联系人列表分组内容（U13 自 PersonPage 下沉）。
 *
 * 负责：空搜索态 / 匹配名字标题 / 字母头 sticky 视觉 / 标签命中分组。
 * 多选闭包仍由页面传入，避免把 Screen 状态泄漏进列表层。
 */
internal fun LazyListScope.personGroupedContactItems(
    displayItems: List<Contact>,
    tagHitGroups: List<TagHitGroup>,
    searchQuery: String,
    lastShownLetter: Ref<String?>,
    contactTagsMap: Map<Long, List<TagCacheEntity>>,
    selectedIds: Set<Long>,
    isSelectMode: Boolean,
    onContactClick: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onEnterSelectMode: (Long) -> Unit,
) {
    if (displayItems.isEmpty() && tagHitGroups.isEmpty()) {
        item(key = "empty_search") {
            BadgerEmptyStateCompact(
                text = "未找到联系人",
                modifier = Modifier.padding(vertical = BadgerSpacing.xxxl),
            )
        }
        return
    }

    if (searchQuery.isNotBlank() && displayItems.isNotEmpty()) {
        item(key = "search_header_names") {
            Text(
                text = "匹配名字（${displayItems.size}）",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(
                    start = BadgerSpacing.lgx,
                    top = BadgerSpacing.md,
                    bottom = BadgerSpacing.xs,
                ),
            )
        }
    }

    val showAlphabetHeaders = searchQuery.isBlank()
    if (showAlphabetHeaders) lastShownLetter.v = null
    items(
        count = displayItems.size,
        key = { index -> "c_${displayItems[index].id}" },
        contentType = { "contact" },
    ) { index ->
        val contact = displayItems[index]
        val currentLetter = PinyinUtils.getContactPinyinInitial(contact.name)
        val prevLetter = if (index > 0) {
            displayItems.getOrNull(index - 1)?.let { PinyinUtils.getContactPinyinInitial(it.name) }
        } else {
            null
        }
        val showHeader = if (!showAlphabetHeaders) {
            false
        } else if (prevLetter != null) {
            currentLetter != prevLetter
        } else {
            currentLetter != lastShownLetter.v
        }

        Column {
            if (showHeader) {
                lastShownLetter.v = currentLetter
                LetterSectionHeader(currentLetter)
            }
            ContactRow(
                contact = contact,
                contactTags = contactTagsMap,
                selectedIds = selectedIds,
                isSelectMode = isSelectMode,
                onContactClick = onContactClick,
                onToggleSelected = onToggleSelected,
                onEnterSelectMode = onEnterSelectMode,
            )
        }
    }

    tagHitGroups.forEach { group ->
        item(key = "search_header_tag_${group.tag.id}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BadgerSpacing.lgx, top = BadgerSpacing.md, bottom = BadgerSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(group.tag.color)),
                )
                Spacer(modifier = Modifier.width(BadgerSpacing.sm))
                Text(
                    text = "标签「${group.tag.name}」（${group.contacts.size}）",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        items(
            count = group.contacts.size,
            key = { idx -> "tag_${group.tag.id}_${group.contacts[idx].id}" },
            contentType = { "contact" },
        ) { idx ->
            val contact = group.contacts[idx]
            ContactRow(
                contact = contact,
                contactTags = contactTagsMap,
                selectedIds = selectedIds,
                isSelectMode = isSelectMode,
                onContactClick = onContactClick,
                onToggleSelected = onToggleSelected,
                onEnterSelectMode = onEnterSelectMode,
            )
        }
    }
}

@Composable
private fun LetterSectionHeader(letter: String) {
    Text(
        text = letter,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(
            start = BadgerSpacing.lgx,
            top = BadgerSpacing.sm,
            bottom = BadgerSpacing.xs,
        ),
    )
}

/**
 * 右侧字母索引 + 拖动气泡（U13）。
 *
 * 热区宽度 [LetterIndexHitWidth]；滚动逻辑仍由页面传入 listState。
 */
@Composable
internal fun PersonLetterIndexOverlay(
    letterCounts: List<LetterCount>,
    displayItemCount: Int,
    hasContactsInDb: Boolean,
    fixedItemCount: Int,
    listState: LazyListState,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isIndexDragging by remember { mutableStateOf(false) }
    var currentIndexLetter by remember { mutableStateOf("") }
    val indexLetters = remember {
        listOf("⭐") + ('A'..'Z').map { it.toString() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(LetterIndexHitWidth)
                .padding(top = topPadding + 48.dp, bottom = bottomPadding + 72.dp),
        ) {
            LetterIndexBar(
                letters = indexLetters,
                onSelectLetter = { letter ->
                    when (letter) {
                        "⭐" -> {
                            val myProfileIndex = if (hasContactsInDb) 2 else 1
                            scope.launch { listState.animateScrollToItem(myProfileIndex) }
                        }
                        else -> {
                            val target = letterCounts.firstOrNull { it.letter == letter }?.let {
                                fixedItemCount +
                                    letterCounts
                                        .takeWhile { lc -> lc.letter < letter }
                                        .sumOf { lc -> lc.count }
                            }
                            if (target != null) {
                                val totalItemCount = fixedItemCount + displayItemCount
                                val safeTarget = target.coerceAtMost(totalItemCount - 1)
                                scope.launch { listState.animateScrollToItem(safeTarget) }
                            }
                        }
                    }
                },
                onDragStateChange = { dragging, letter ->
                    isIndexDragging = dragging
                    currentIndexLetter = letter
                },
                modifier = Modifier.fillMaxHeight(),
            )
        }
        LetterTooltip(visible = isIndexDragging, letter = currentIndexLetter)
    }
}
