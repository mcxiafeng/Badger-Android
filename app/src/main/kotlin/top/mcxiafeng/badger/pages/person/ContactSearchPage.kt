package top.mcxiafeng.badger.pages.person

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import top.mcxiafeng.badger.ui.components.SkeletonListPlaceholder
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.ToolbarPosition

@Composable
fun ContactSearchRoute(onBack: () -> Unit, onContactClick: (Long) -> Unit) {
    // Miuix Nav scopes this instance to the search entry, keeping the main list unfiltered.
    val viewModel: PersonViewModel = hiltViewModel()
    ContactSearchScreen(viewModel, onBack, onContactClick)
}

@Composable
internal fun ContactSearchScreen(viewModel: PersonViewModel, onBack: () -> Unit, onContactClick: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf(viewModel.searchQuery.value) }
    var focusOnEntry by rememberSaveable { mutableStateOf(true) }
    val session by viewModel.pagingSession.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val trimmedQuery = query.trim()
    val showResults = trimmedQuery.isNotEmpty() && trimmedQuery == session.query
    LaunchedEffect(viewModel, query) { viewModel.updateSearchQuery(query) }
    BackHandler(enabled = selection.enabled, onBack = viewModel::exitSelection)
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            ContactSearchTopBar(
                viewModel, selection,
                onSelect = if (showResults) { { dismissKeyboard(); viewModel.enterSelection() } } else null,
                onBack = { dismissKeyboard(); onBack() },
            )
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
        floatingToolbar = { PersonSelectionToolbar(viewModel, selection) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (!selection.enabled) ContactSearchField(query, { query = it }, dismissKeyboard, focusOnEntry) {
                focusOnEntry = false
            }
            when {
                showResults -> key(trimmedQuery) {
                    ContactSearchResults(viewModel, session, onContactClick, dismissKeyboard, Modifier.weight(1f))
                }
                // 搜索防抖/session 切换等待：骨架行与结果列表同构，替代居中转圈
                trimmedQuery.isNotEmpty() -> SkeletonListPlaceholder(
                    modifier = Modifier.fillMaxSize().padding(top = BadgerSpacing.lg)
                )
            }
        }
    }
    PersonSelectionFeedback(viewModel)
}

@Composable
private fun ContactSearchResults(
    viewModel: PersonViewModel, session: PersonPagingSession, onContactClick: (Long) -> Unit,
    dismissKeyboard: () -> Unit, modifier: Modifier,
) {
    LaunchedEffect(Unit) { Log.d("ContactSearchPageTester", "Spring result viewport clipped below fixed search") }
    Box(modifier.fillMaxWidth().clipToBounds().testTag("contact_search_result_viewport")) {
        PersonPagingContent(viewModel, session, rememberLazyListState(),
            onContactClick = { dismissKeyboard(); onContactClick(it) }, onShareOpened = dismissKeyboard)
    }
}

@Composable
private fun ContactSearchTopBar(
    viewModel: PersonViewModel, selection: PersonSelectionState, onSelect: (() -> Unit)?, onBack: () -> Unit,
) {
    SmallTopAppBar(
        title = if (selection.enabled) "已选择 ${selection.ids.size} 项" else "搜索联系人",
        navigationIcon = {
            IconButton(
                onClick = if (selection.enabled) viewModel::exitSelection else onBack,
                minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget,
            ) {
                if (selection.enabled) Icon(Icons.Default.Close, "取消")
                else Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
        actions = {
            if (selection.enabled) PersonSelectAllButton(viewModel, selection)
            else if (onSelect != null) PersonMoreMenu(onSelect = onSelect)
        },
    )
}

@Composable
private fun ContactSearchField(
    query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit,
    focusOnEntry: Boolean, onFocusRequested: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, focusOnEntry) {
        if (focusOnEntry) lifecycle.withResumed {
            expanded = true
            onFocusRequested()
            Log.d("ContactSearchPageTester", "Requested search field focus")
        }
    }
    val label = "姓名、账号或标签"
    // The route owns the results and Back. Only the input field needs to expand for focus.
    SearchBar(
        modifier = Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.sm),
        onExpandedChange = {},
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch() },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.semantics { contentDescription = label },
                label = label,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(
                        start = SearchBarDefaults.LeadingIconStartPadding,
                        end = SearchBarDefaults.LeadingIconEndPadding,
                    ))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(
                        onClick = { onQueryChange("") },
                        minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget,
                    ) { Icon(Icons.Default.Close, "清除搜索") }
                },
            )
        },
    ) {}
}
