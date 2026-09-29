package top.mcxiafeng.badger.page.person

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.UserRoundPlus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.page.person.dialogs.ShareDialog
import top.mcxiafeng.badger.page.system.StatusPanel
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.time.Duration.Companion.milliseconds

class PersonPage {

    companion object {
        @Composable
        fun PagePerson(
            vm: PersonViewModel = viewModel { PersonViewModel() },
        ) {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
            val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()

            when (val current = state) {
                PersonUiState.Loading -> StatusPanel.onLoading()
                is PersonUiState.Error -> StatusPanel.onError(current.message) { vm.refresh() }
                is PersonUiState.Success -> PersonListPane(
                    persons = current.persons,
                    searchQuery = searchQuery,
                    isRefreshing = isRefreshing,
                    onRefresh = vm::refresh,
                    onSearchQueryChange = vm::onSearchQueryChange,
                )
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        private fun PersonListPane(
            persons: List<Person>,
            searchQuery: String,
            isRefreshing: Boolean,
            onRefresh: () -> Unit,
            onSearchQueryChange: (String) -> Unit,
        ) {
            val focusManager = LocalFocusManager.current
            var expanded by remember { mutableStateOf(value = false) }
            val pullToRefreshState = rememberPullToRefreshState()
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            var sharePerson by remember { mutableStateOf<Person?>(null) }

            val fullAlphabet = remember { ('A'..'Z').toList() + '#' }

            // Group persons by initial letter and sort them
            val groupedPersons: List<Pair<Char, List<Person>>> = remember(persons) {
                persons
                    .groupBy { person ->
                        val name = person.name ?: ""
                        val initialStr = PinyinUtils.getContactPinyinInitial(name)
                        val char = initialStr.firstOrNull()?.uppercaseChar() ?: '#'
                        if (char in 'A'..'Z') char else '#'
                    }
                    .mapValues { entry ->
                        entry.value.sortedWith(compareBy { person -> person.name ?: "" })
                    }
                    .entries
                    .map { it.key to it.value }
                    .sortedWith(compareBy { (char, _) ->
                        if (char == '#') 'Z' + 1 else char
                    })
            }

            // Pre-calculate list index for each section header
            val headerIndices = remember(groupedPersons) {
                val map = mutableMapOf<Char, Int>()
                var idx = 0
                groupedPersons.forEach { (letter, list) ->
                    map[letter] = idx
                    idx += 1 + list.size
                }
                map
            }

            var selectedLetter by remember { mutableStateOf<Char?>(null) }
            var showCenterIndicator by remember { mutableStateOf(false) }
            var hideIndicatorJob by remember { mutableStateOf<Job?>(null) }

            fun scrollToLetter(letter: Char) {
                selectedLetter = letter
                showCenterIndicator = true

                val targetLetter = if (headerIndices.containsKey(letter)) {
                    letter
                } else {
                    val available = headerIndices.keys.sortedWith(compareBy { if (it == '#') 'Z' + 1 else it })
                    available.firstOrNull { it > letter } ?: available.lastOrNull()
                }

                targetLetter?.let { headerIndices[it] }?.let { targetIndex ->
                    coroutineScope.launch {
                        lazyListState.scrollToItem(targetIndex)
                    }
                }
            }

            fun onDraggingChanged(dragging: Boolean) {
                if (dragging) {
                    hideIndicatorJob?.cancel()
                    showCenterIndicator = true
                } else {
                    hideIndicatorJob?.cancel()
                    hideIndicatorJob = coroutineScope.launch {
                        delay(600.milliseconds)
                        showCenterIndicator = false
                        selectedLetter = null
                    }
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "联系人",
                        subtitle = "${persons.size} 个联系人",
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { /* 处理点击事件 */ },
                        modifier = Modifier.padding(end = 24.dp).size(48.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.UserRoundPlus,
                            contentDescription = "添加",
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                },
            ) { innerPadding ->
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = pullToRefreshState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                focusManager.clearFocus()
                            },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            SearchBar(
                                modifier = Modifier.padding(horizontal = BadgerSpacing.cardPadding, vertical = BadgerSpacing.sm),
                                inputField = {
                                    InputField(
                                        query = searchQuery,
                                        onQueryChange = onSearchQueryChange,
                                        onSearch = { focusManager.clearFocus() },
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it },
                                    )
                                },
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                            ) {
                                // 搜索结果展开内容
                            }


                            if (persons.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(BadgerSpacing.cardPadding),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (searchQuery.isBlank()) "还没有联系人" else "未找到相关联系人",
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(BadgerSpacing.md))
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .overScrollVertical()
                                        .padding(end = 28.dp)
                                        .padding(horizontal = BadgerSpacing.cardPadding),
                                    contentPadding = PaddingValues(bottom = BadgerSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
                                ) {
                                    groupedPersons.forEach { (letter, personList) ->
                                        item {
                                            SmallTitle(
                                                text = letter.toString(),
                                                insideMargin = PaddingValues(vertical = 0.dp),
                                                textColor = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                        items(
                                            items = personList,
                                            key = { it.uuid.toString() },
                                        ) { person ->
                                            PersonItemRow(
                                                person = person,
                                                onShare = { sharePerson = person },
                                                onDelete = { sharePerson = person }
                                            )
                                        }
                                    }
                                }
                            }

                        }

                        // Right Alphabet Index Bar
                        if (persons.isNotEmpty()) {
                            AlphabetIndexBar(
                                alphabet = fullAlphabet,
                                selectedLetter = selectedLetter,
                                onLetterSelected = { letter ->
                                    scrollToLetter(letter)
                                },
                                onDraggingChanged = { dragging ->
                                    onDraggingChanged(dragging)
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp),
                            )
                        }

                        // Center Big Letter Popup Indicator
                        AnimatedVisibility(
                            visible = showCenterIndicator && selectedLetter != null,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            selectedLetter?.let { letter ->
                                Card(
                                    modifier = Modifier.size(80.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = letter.toString(),
                                            style = MiuixTheme.textStyles.title1,
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ShareDialog(
                    person = sharePerson,
                    show = sharePerson != null,
                    onDismissRequest = { sharePerson = null },
                    scope = coroutineScope
                )
            }
        }


        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        private fun PersonItemRow(
            person: Person,
            onClick: (() -> Unit)? = null,
            onShare: (() -> Unit)? = null,
            onDelete: (() -> Unit)? = null,
        ) {
            var showPopup by remember { mutableStateOf(false) }
            val displayName = person.name?.ifBlank { null } ?: "未命名联系人"

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { showPopup = true },
                        )
                        .padding(vertical = BadgerSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        name = displayName,
                        avatarPath = person.avatarURL,
                        avatarUrl = person.avatarURL,
                        size = 40,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = BadgerSpacing.md),
                    ) {
                        Text(
                            text = displayName,
                            style = MiuixTheme.textStyles.title4,
                        )
                    }
                }

                OverlayListPopup(
                    show = showPopup,
                    alignment = PopupPositionProvider.Align.TopStart,
                    onDismissRequest = { showPopup = false },
                ) {
                    ListPopupColumn {
                        val items = listOf("分享", "删除")
                        items.forEachIndexed { index, text ->
                            DropdownImpl(
                                text = text,
                                optionSize = items.size,
                                isSelected = false,
                                index = index,
                                onSelectedIndexChange = {
                                    showPopup = false
                                    when (index) {
                                        0 -> onShare?.invoke()
                                        1 -> onDelete?.invoke()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
