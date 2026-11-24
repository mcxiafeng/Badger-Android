package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodoRoute(onEdit: (String) -> Unit, viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query, viewModel) { viewModel.search(query) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    val closeSearch = {
        searching = false
        query = ""
        viewModel.search("")
    }
    BackHandler(enabled = searching, onBack = closeSearch)
    Scaffold(topBar = {
        TopAppBar(title = "待办", actions = {
            IconButton(onClick = {
                if (searching) closeSearch() else searching = true
                Log.d(TAG, "Changed task search visibility; expanded=$searching")
            }) { Icon(Icons.Default.Search, "搜索待办") }
            IconButton(onClick = {
                Log.d(TAG, "Opening new task")
                onEdit("")
            }) { Icon(Icons.Default.Add, "新建待办") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(searching) {
                TextField(value = query, onValueChange = { query = it },
                    label = "搜索标题、说明或地点", singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.sm))
            }
            state.error?.let { TodoError(it, viewModel::reload) }
            TodoContent(state, viewModel::toggle, onEdit)
        }
    }
}

@Composable
private fun TodoContent(state: TodoUiState, onToggle: (String) -> Unit, onEdit: (String) -> Unit) {
    if (state.pending.isNotEmpty() || state.completed.isNotEmpty()) {
        TodoList(state, onToggle, onEdit)
    } else {
        Box(Modifier.fillMaxSize().padding(bottom = LocalFloatingBarBottomPadding.current), contentAlignment = Alignment.Center) {
            when {
                state.loading -> Text("正在读取待办…")
                !state.loaded -> Text("待办暂时无法读取")
                state.query.isNotEmpty() -> Text("未找到匹配的待办")
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                    Text("还没有待办")
                    Text("点击添加", color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onEdit("") }.padding(BadgerSpacing.md))
                }
            }
        }
    }
}

@Composable
private fun TodoList(state: TodoUiState, onToggle: (String) -> Unit, onEdit: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(
        start = BadgerSpacing.xl, end = BadgerSpacing.xl, top = BadgerSpacing.md,
        bottom = LocalFloatingBarBottomPadding.current + BadgerSpacing.xl,
    ), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)) {
        items(state.pending, key = { it.item.id }) { row ->
            TodoRow(row, !state.saving, onToggle, onEdit, Modifier.animateItem())
        }
        if (state.completed.isNotEmpty()) {
            item(key = "completed_title") { SmallTitle("已完成") }
            items(state.completed, key = { it.item.id }) { row ->
                TodoRow(row, !state.saving, onToggle, onEdit, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun TodoRow(
    row: TodoRowState, enabled: Boolean, onToggle: (String) -> Unit, onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    Card(modifier = modifier, cornerRadius = BadgerRadius.card, insideMargin = PaddingValues(BadgerSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggle(item.id) }, enabled = enabled,
                minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget) {
                Icon(if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    if (item.completed) "重新打开待办" else "完成待办", tint = MiuixTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).clickable { onEdit(item.id) }.padding(vertical = BadgerSpacing.md),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
                Text(item.title, style = MiuixTheme.textStyles.title4.copy(
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (item.completed) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurface)
                if (row.summary.isNotBlank()) Text(row.summary, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onEdit(item.id) }, minWidth = BadgerSize.minimumTouchTarget,
                minHeight = BadgerSize.minimumTouchTarget) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "编辑待办") }
        }
    }
}

@Composable
internal fun TodoError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(BadgerSpacing.xl), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(message, color = MiuixTheme.colorScheme.error)
        Text("点击重试", color = MiuixTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRetry))
    }
}

private const val TAG = "TodoPageTester"
