package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.pages.person.ContactLetterIndex
import top.mcxiafeng.badger.pages.person.PersonContactItem
import top.mcxiafeng.badger.pages.person.PersonLetterHeader
import top.mcxiafeng.badger.pages.person.PersonLoadStatus
import top.mcxiafeng.badger.pages.person.PersonPagingSession
import top.mcxiafeng.badger.pages.person.PersonPlaceholder
import top.mcxiafeng.badger.pages.person.PersonSectionHeader
import top.mcxiafeng.badger.pages.person.PersonSelectionState
import top.mcxiafeng.badger.pages.person.PersonViewModel
import top.mcxiafeng.badger.ui.components.SkeletonListPlaceholder
import top.mcxiafeng.badger.ui.components.TextField
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.GlassButtonScope
import top.mcxiafeng.badger.ui.glass.SaveIconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.mergePagePadding
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Full-page contact picker for the todo editor. It reuses the contacts tab's paging source, letter
 * index and row layout, but every row toggles a pick and the only exits are cancel (leading) and
 * confirm (trailing), styled exactly like the editor's own toolbar buttons.
 */
@Composable
fun TodoContactPickerPage(
    selected: List<Long>, onCancel: () -> Unit, onConfirm: (List<Long>) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    // Picks live outside the paging session so changing the search query never drops them.
    var picked by rememberSaveable(
        stateSaver = listSaver<List<Long>, Long>(save = { it }, restore = { it })
    ) { mutableStateOf(selected.distinct().filter { it > 0 }) }
    var query by rememberSaveable { mutableStateOf("") }
    val session by viewModel.pagingSession.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, query) { viewModel.updateSearchQuery(query) }
    Scaffold(progressiveTopBlur = true, modifier = Modifier.imePadding(), topBar = {
        // Same glass scope as the editor's top bar, so cancel and confirm read as one pair.
        GlassButtonScope {
            TopAppBar(title = "选择联系人", navigationIcon = {
                TodoCancelButton(onCancel = {
                    Log.d(TAG, "Cancelled contact picking; kept=${picked.size}")
                    onCancel()
                }, enabled = true)
            }, actions = {
                SaveIconButton(ready = true, enabled = true, onClick = {
                    Log.d(TAG, "Confirmed contact picks; count=${picked.size}")
                    onConfirm(picked)
                }, contentDescription = "确定")
            })
        }
    }) { padding ->
        TodoPickerContent(viewModel, session, query, { query = it }, picked.toSet(), padding) { id ->
            picked = if (id in picked) picked - id else picked + id
            Log.d(TAG, "Toggled picked contact; count=${picked.size}")
        }
    }
}

@Composable
private fun TodoPickerContent(
    viewModel: PersonViewModel, session: PersonPagingSession, query: String, onQuery: (String) -> Unit,
    picked: Set<Long>, padding: PaddingValues, onToggle: (Long) -> Unit,
) {
    val rows = session.rows.collectAsLazyPagingItems()
    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty() && trimmed == session.query
    val listState = rememberLazyListState()
    val letters = if (searching) emptyList() else viewModel.letterCounts.collectAsStateWithLifecycle(emptyList()).value
    val sections = if (searching) session.sections.collectAsStateWithLifecycle(emptyList()).value else emptyList()
    val tags by viewModel.contactTagsMap.collectAsStateWithLifecycle()
    // The contacts tab's selection look (checked circle, no share menu) without its ViewModel state.
    val selection = remember(picked) { PersonSelectionState(enabled = true, ids = picked) }
    val letterStarts = remember(letters) { letterStarts(letters) }
    val sectionStarts = remember(sections) { sectionStarts(sections) }
    val gutter = if (searching) BadgerSpacing.xs else BadgerSize.indexRailWidth
    val empty = rows.itemCount == 0
    // Tag dots and the row's checked mark both read the same bounded loaded-id window.
    LaunchedEffect(rows, viewModel) {
        snapshotFlow { rows.itemSnapshotList.items.map { it.contact.id }.distinct().sorted() }
            .distinctUntilChanged().collect(viewModel::updateLoadedContactIds)
    }
    LaunchedEffect(empty, searching) {
        Log.d(TAG, "Picker list empty=$empty searching=$searching; search field scrolls with rows")
    }
    Box(Modifier.fillMaxSize().consumeWindowInsets(padding)
        .padding(mergePagePadding(padding, PaddingValues(horizontal = BadgerSpacing.xl)))) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().scrollTopFade().springOverscroll(),
            contentPadding = PaddingValues(bottom = if (empty) 0.dp else BadgerSpacing.xl)) {
            item(key = "picker_search", contentType = "search") {
                // The field is the list's first item, so it scrolls away together with the
                // contacts. When there is nothing to show it also hosts the status copy in the
                // remaining viewport height, keeping the empty list non-scrollable.
                Column(if (empty) Modifier.fillParentMaxHeight() else Modifier) {
                    TextField(cornerRadius = BadgerRadius.surface, value = query, onValueChange = onQuery,
                        label = "搜索姓名、账号或标签", singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = BadgerSpacing.sm, end = gutter, bottom = BadgerSpacing.sm))
                    if (empty) TodoPickerState(rows, searching, Modifier.weight(1f))
                }
            }
            if (!empty) {
                items(count = rows.itemCount, key = rows.itemKey { it.key }, contentType = { "contact" }) { index ->
                    val row = rows[index]
                    Column(Modifier.padding(end = gutter)) {
                        if (searching) sectionStarts[index]?.let { PersonSectionHeader(it) }
                        else letterStarts[index]?.let { PersonLetterHeader(it) }
                        if (row == null) PersonPlaceholder()
                        else PersonContactItem(row.contact, tags[row.contact.id].orEmpty(), selection,
                            onClick = { onToggle(row.contact.id) }, onShare = {})
                    }
                }
            }
        }
        if (!searching) ContactLetterIndex(letters, listState, fixedItemCount = SEARCH_HEADER_ITEMS, profileIndex = -1,
            showProfile = false, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Loading/error/empty body shown under the search field when the paging list has no rows. */
@Composable
private fun TodoPickerState(rows: LazyPagingItems<PersonContactRow>, searching: Boolean, modifier: Modifier) {
    when {
        rows.loadState.refresh is LoadState.Loading -> SkeletonListPlaceholder(modifier)
        rows.loadState.refresh is LoadState.Error -> Box(modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center) { PersonLoadStatus(rows::retry) }
        else -> TodoPickerEmpty(searching, modifier)
    }
}

@Composable
private fun TodoPickerEmpty(searching: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(if (searching) "未找到联系人" else "还没有联系人", style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
    }
}

private fun letterStarts(letters: List<LetterCount>): Map<Int, String> = buildMap {
    var offset = 0
    letters.forEach { put(offset, it.letter); offset += it.count }
}

private fun sectionStarts(sections: List<PersonContactSection>): Map<Int, PersonContactSection> = buildMap {
    var offset = 0
    sections.forEach { put(offset, it); offset += it.count }
}

private const val TAG = "TodoContactPickerPageTester"

/** The search field is the single list item before the first contact row. */
private const val SEARCH_HEADER_ITEMS = 1
