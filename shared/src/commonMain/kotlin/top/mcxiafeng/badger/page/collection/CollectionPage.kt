package top.mcxiafeng.badger.page.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.ui.components.Alert
import top.mcxiafeng.badger.ui.components.EntranceReveal
import top.mcxiafeng.badger.ui.components.GhostCardStack
import top.mcxiafeng.badger.ui.components.StatusPanel
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val TAG = "CollectionPageTester"

private const val GRID_COLUMNS = 2

private const val ENTRANCE_STAGGER_MS = 20L

class CollectionPage {

    companion object {

        @Composable
        fun PageCollection(
            vm: CollectionViewModel = viewModel { CollectionViewModel() },
        ) {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

            when (val current = state) {
                CollectionUiState.Loading -> CollectionSkeleton()
                is CollectionUiState.Error -> StatusPanel.onError(current.message) { vm.refresh() }
                is CollectionUiState.Success -> CollectionListPane(
                    collections = current.collections,
                    isRefreshing = isRefreshing,
                    onRefresh = vm::refresh,
                    onCreateCollection = vm::createCollection,
                    onUpdateCollection = vm::updateCollection,
                    onDeleteCollection = vm::deleteCollection,
                )
            }
        }

        @Composable
        private fun CollectionListPane(
            collections: List<Collection>,
            isRefreshing: Boolean,
            onRefresh: () -> Unit,
            onCreateCollection: (String, String?) -> Unit,
            onUpdateCollection: (Collection, String, String?) -> Unit,
            onDeleteCollection: (Collection) -> Unit,
        ) {
            var showCreateDialog by remember { mutableStateOf(false) }
            var showEditDialog by remember { mutableStateOf(false) }
            var editingCollection by remember { mutableStateOf<Collection?>(null) }
            var deletingCollection by remember { mutableStateOf<Collection?>(null) }
            val pullToRefreshState = rememberPullToRefreshState()
            val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "名片夹",
                        subtitle = "${collections.size} 个名片夹",
                        scrollBehavior = scrollBehavior,
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            BadgerLog.d(TAG, "FAB: open create dialog")
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(48.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = "新建名片夹",
                            tint = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                },
            ) { innerPadding ->
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        BadgerLog.d(TAG, "CollectionPage: pull-to-refresh")
                        onRefresh()
                    },
                    pullToRefreshState = pullToRefreshState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(innerPadding),
                ) {
                    if (collections.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GhostCardStack(modifier = Modifier.padding(bottom = BadgerSpacing.lg))
                                Text(
                                    text = "还没有名片夹",
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = "点击添加",
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { showCreateDialog = true }
                                        .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical()
                                .padding(horizontal = BadgerSpacing.cardPadding),
                            contentPadding = PaddingValues(
                                top = BadgerSpacing.md,
                                bottom = BadgerSpacing.sm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
                        ) {
                            items(
                                items = collections.chunked(GRID_COLUMNS),
                                key = { row -> row.joinToString(",") { it.uuid.toString() } },
                                contentType = { _ -> "collection_row" },
                            ) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
                                ) {
                                    rowItems.forEachIndexed { colIndex, collection ->
                                        EntranceReveal(
                                            delayMillis = colIndex * ENTRANCE_STAGGER_MS,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            CollectionCard(
                                                collection = collection,
                                                modifier = Modifier.fillMaxWidth(),
                                                onEdit = {
                                                    BadgerLog.d(TAG, "edit collection: ${collection.uuid}")
                                                    editingCollection = collection
                                                    showEditDialog = true
                                                },
                                                onDelete = {
                                                    BadgerLog.d(TAG, "delete collection: ${collection.uuid}")
                                                    deletingCollection = collection
                                                },
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showCreateDialog) {
                CollectionEditDialog(
                    title = "新建名片夹",
                    summary = "创建后可以把联系人收纳进来",
                    confirmText = "创建",
                    initialName = "",
                    initialDescription = null,
                    onConfirm = { name, description ->
                        BadgerLog.d(TAG, "create dialog confirmed")
                        showCreateDialog = false
                        onCreateCollection(name, description)
                    },
                    onDismiss = {
                        BadgerLog.d(TAG, "create dialog dismissed")
                        showCreateDialog = false
                    },
                )
            }

            if (showEditDialog) {
                val editing = editingCollection
                if (editing != null) {
                    CollectionEditDialog(
                        title = "编辑名片夹",
                        summary = null,
                        confirmText = "保存",
                        initialName = editing.name,
                        initialDescription = editing.description,
                        onConfirm = { name, description ->
                            BadgerLog.d(TAG, "edit dialog confirmed")
                            showEditDialog = false
                            onUpdateCollection(editing, name, description)
                        },
                        onDismiss = {
                            BadgerLog.d(TAG, "edit dialog dismissed")
                            showEditDialog = false
                        },
                    )
                }
            }

            val deleting = deletingCollection
            if (deleting != null) {
                Alert(
                    show = true,
                    title = "删除名片夹",
                    summary = "删除「${deleting.name}」后不可恢复，夹内的联系人不会被删除",
                    confirmText = "删除",
                    onConfirm = {
                        BadgerLog.d(TAG, "delete confirmed")
                        deletingCollection = null
                        onDeleteCollection(deleting)
                    },
                    onDismiss = {
                        BadgerLog.d(TAG, "delete cancelled")
                        deletingCollection = null
                    },
                )
            }
        }
    }
}
