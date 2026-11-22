package top.mcxiafeng.badger.pages.person

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.LetterCount
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PersonContactRow
import top.mcxiafeng.badger.data.PersonContactSection
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.pages.account.AccountSyncTone
import top.mcxiafeng.badger.pages.account.AccountViewModel
import top.mcxiafeng.badger.pages.account.presentAccountSync
import top.mcxiafeng.badger.pages.person.contact.ToolbarAction
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.components.WorkspacePullRefresh
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PersonRoute(accountSync: AccountViewModel, onAddContact: () -> Unit = {}, onContactClick: (Long) -> Unit = {}) {
    val viewModel: PersonViewModel = hiltViewModel()
    val appViewModel: AppViewModel = hiltViewModel()
    val tick by appViewModel.userProfileTick.collectAsStateWithLifecycle()
    LaunchedEffect(tick) { viewModel.refreshUserProfile() }
    PersonScreen(viewModel, onAddContact, onContactClick, accountSync)
}

@Composable
internal fun PersonScreen(
    viewModel: PersonViewModel, onAddContact: () -> Unit, onContactClick: (Long) -> Unit,
    accountSync: AccountViewModel? = null,
) {
    val session by viewModel.pagingSession.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val mainListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val onRefresh = if (accountSync?.canRefresh == true) rememberContactRefresh(accountSync) else null
    BackHandler(enabled = selection.enabled || searchExpanded) {
        if (selection.enabled) viewModel.exitSelection() else searchExpanded = false
    }
    Scaffold(topBar = { PersonTopBar(viewModel, selection, onRefresh) },
        floatingActionButton = { PersonAddButton(!selection.enabled, onAddContact) },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
        floatingToolbar = { PersonDeleteToolbar(selection) { showDelete = true } }) { padding ->
        // Keep the editor mounted while debounce replaces the paged result session.
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PersonSearchBar(query, searchExpanded, viewModel::updateSearchQuery) { searchExpanded = it }
            val canPull = onRefresh != null && !selection.enabled
            val content: @Composable () -> Unit = {
                key(session) {
                    val searchListState = rememberLazyListState()
                    PersonPagingContent(viewModel, session, if (session.query.isEmpty()) mainListState else searchListState,
                        onAddContact, onContactClick, canPull)
                }
            }
            if (accountSync != null && onRefresh != null) {
                WorkspacePullRefresh(accountSync.refreshing, onRefresh, Modifier.weight(1f).fillMaxSize(),
                    enabled = canPull, content = content)
            } else {
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
    if (showDelete) PersonDeleteDialog(selection.ids.size, onDismiss = { showDelete = false }, onConfirm = {
        showDelete = false
        scope.launch { viewModel.deleteSelectedContacts() }
    })
    PersonImportDialogs(viewModel)
}

@Composable
private fun rememberContactRefresh(accountSync: AccountViewModel): () -> Unit {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(accountSync, context, lifecycle) {
        {
            accountSync.requestSync { progress ->
                val status = presentAccountSync(local = false, progress = progress)
                if (status.tone == AccountSyncTone.ATTENTION && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Toast.makeText(context, status.title, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun PersonTopBar(viewModel: PersonViewModel, selection: PersonSelectionState, onRefresh: (() -> Unit)?) {
    var showMenu by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onQAuxvFileSelected(uri)
    }
    TopAppBar(title = if (selection.enabled) "已选择 ${selection.ids.size} 项" else "联系人",
        navigationIcon = {
            if (selection.enabled) IconButton(onClick = viewModel::exitSelection) { Icon(Icons.Default.Close, "取消") }
        }, actions = {
            if (selection.enabled) IconButton(onClick = {
                if (!selection.selectingAll && !selection.deleting) viewModel.toggleSelectAll()
            }) {
                Icon(Icons.Default.CheckCircle,
                    if (selection.selectingAll) "正在全选" else if (selection.allSelected) "取消全选" else "全选",
                    tint = if (selection.allSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                OverlayListPopup(show = showMenu, alignment = PopupPositionProvider.Align.TopEnd,
                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                    onDismissRequest = { showMenu = false }) {
                    ListPopupColumn {
                        val optionSize = if (onRefresh == null) 1 else 2
                        if (onRefresh != null) DropdownImpl(text = "同步资料", optionSize = optionSize,
                            isSelected = false, index = 0, onSelectedIndexChange = { showMenu = false; onRefresh() })
                        DropdownImpl(text = "从 QAuxiliary 导入 QQ 好友", optionSize = optionSize, isSelected = false,
                            index = optionSize - 1, onSelectedIndexChange = { showMenu = false; launcher.launch("*/*") })
                    }
                }
            }
        })
}

@Composable
private fun PersonAddButton(visible: Boolean, onAddContact: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        FloatingActionButton(onClick = onAddContact, modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
            Icon(Icons.Default.Add, "添加", tint = MiuixTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun PersonDeleteToolbar(selection: PersonSelectionState, onDelete: () -> Unit) {
    AnimatedVisibility(selection.enabled && selection.ids.isNotEmpty(), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        Box(Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
            FloatingToolbar(cornerRadius = BadgerRadius.card) {
                ToolbarAction(Icons.Default.Delete, "删除", tint = MiuixTheme.colorScheme.error,
                    onClick = { if (!selection.selectingAll && !selection.deleting) onDelete() })
            }
        }
    }
}

@Composable
private fun PersonPagingContent(
    viewModel: PersonViewModel, session: PersonPagingSession, listState: LazyListState,
    onAddContact: () -> Unit, onContactClick: (Long) -> Unit, canPull: Boolean,
) {
    val rows = session.rows.collectAsLazyPagingItems()
    val letters by viewModel.letterCounts.collectAsStateWithLifecycle(emptyList())
    val sections by session.sections.collectAsStateWithLifecycle(emptyList())
    val tags by viewModel.contactTagsMap.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val hasContacts = letters.isNotEmpty() || (session.query.isEmpty() && rows.itemCount > 0)
    val fixedItems = if (hasContacts) HEADER_ITEMS_WITH_HINT else HEADER_ITEMS
    val anchor = rememberPersonScrollAnchor(rows, listState, fixedItems) { viewModel.resolveAnchor(session.query, it) }
    LaunchedEffect(rows) {
        snapshotFlow { rows.itemSnapshotList.items.map { it.contact.id }.distinct().sorted() }
            .distinctUntilChanged().collect(viewModel::updateLoadedContactIds)
    }
    if (rows.itemCount == 0) {
        PersonEmptyContent(profile, rows.loadState.refresh, session.query.isNotEmpty(),
            rows::retry, onAddContact, onContactClick, canPull)
    } else {
        PersonLoadedContent(viewModel, rows, listState, letters, sections, tags, profile, selection, anchor,
            hasContacts, fixedItems, session.query.isNotEmpty(), onContactClick)
    }
}

@Composable
private fun PersonEmptyContent(profile: UserProfile?, refresh: LoadState, searching: Boolean,
    retry: () -> Unit, onAddContact: () -> Unit, onContactClick: (Long) -> Unit, canPull: Boolean) {
    val emptyPull = if (canPull) {
        // Dispatch the gesture to the refresh container without moving the empty content.
        Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
    } else Modifier
    Column(Modifier.fillMaxSize().padding(bottom = LocalFloatingBarBottomPadding.current).then(emptyPull)) {
        MyProfileHeader(profile) { onContactClick(MY_CONTACT_ID) }
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (refresh is LoadState.NotLoading) PersonEmptyState(searching, onAddContact)
            else PersonLoadStatus(refresh is LoadState.Loading, refresh is LoadState.Error, retry)
        }
    }
}

@Composable
private fun PersonLoadedContent(
    viewModel: PersonViewModel, rows: LazyPagingItems<PersonContactRow>, listState: LazyListState,
    letters: List<LetterCount>, sections: List<PersonContactSection>, tags: Map<Long, List<Tag>>, profile: UserProfile?,
    selection: PersonSelectionState, anchor: PersonScrollAnchor, hasContacts: Boolean, fixedItems: Int, searching: Boolean,
    onContactClick: (Long) -> Unit,
) {
    val showIndex = !selection.enabled && !searching
    val gutter = if (showIndex) BadgerSize.indexRailWidth else BadgerSpacing.xs
    val sectionStarts = remember(sections) { sectionStarts(sections) }
    val letterStarts = remember(letters) { letterStarts(letters) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalFloatingBarBottomPadding.current + BadgerSize.floatingActionClearance)) {
            if (hasContacts) item(key = "hint_long_press") {
                FirstTimeHint("长按联系人可多选删除", "long_press_person", Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.xs))
            }
            item(key = "my_profile") { Box(Modifier.padding(end = gutter)) { MyProfileHeader(profile) { onContactClick(MY_CONTACT_ID) } } }
            personRows(rows, sectionStarts, letterStarts, searching, tags, selection,
                Modifier.padding(end = gutter), viewModel::toggleSelection, onContactClick)
        }
        if (showIndex) ContactLetterIndex(letters, listState, fixedItems, fixedItems - 1,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(
                top = BadgerSize.minimumTouchTarget,
                bottom = LocalFloatingBarBottomPadding.current + BadgerSize.floatingActionClearance), onJump = anchor::clear)
        if (rows.loadState.refresh is LoadState.Error || rows.loadState.append is LoadState.Error || rows.loadState.prepend is LoadState.Error) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LocalFloatingBarBottomPadding.current)) {
                PersonLoadStatus(false, true, rows::retry)
            }
        }
    }
}

private fun LazyListScope.personRows(
    rows: LazyPagingItems<PersonContactRow>, sections: Map<Int, PersonContactSection>, letters: Map<Int, String>,
    searching: Boolean, tags: Map<Long, List<Tag>>, selection: PersonSelectionState, modifier: Modifier,
    onToggle: (Long) -> Unit, onContactClick: (Long) -> Unit,
) {
    items(count = rows.itemCount, key = rows.itemKey { it.key }, contentType = { "contact" }) { index ->
        val row = rows[index]
        Column(modifier) {
            if (searching) sections[index]?.let { PersonSectionHeader(it) }
            else letters[index]?.let { PersonLetterHeader(it) }
            if (row == null) PersonPlaceholder()
            else PersonContactItem(row.contact, tags[row.contact.id].orEmpty(), selection,
                onClick = {
                    Log.d("PersonPageTester", "Contact row action; selecting=${selection.enabled}")
                    if (selection.enabled) onToggle(row.contact.id) else onContactClick(row.contact.id)
                }, onLongClick = { if (!selection.enabled) onToggle(row.contact.id) })
        }
    }
}

private fun sectionStarts(sections: List<PersonContactSection>): Map<Int, PersonContactSection> = buildMap {
    var offset = 0
    sections.forEach { put(offset, it); offset += it.count }
}

private fun letterStarts(letters: List<LetterCount>): Map<Int, String> = buildMap {
    var offset = 0
    letters.forEach { put(offset, it.letter); offset += it.count }
}

private const val HEADER_ITEMS = 1
private const val HEADER_ITEMS_WITH_HINT = 2
