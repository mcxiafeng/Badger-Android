package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.CheckboxPreference

/** An empty ID creates a task; nonempty IDs always edit an existing record. */
@Composable
fun TodoEditorPage(id: String, onBack: () -> Unit, viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable(id, stateSaver = TodoDraftSaver) { mutableStateOf(TodoDraft()) }
    var snapshot by rememberSaveable(id, stateSaver = TodoSnapshotSaver) {
        mutableStateOf(LocalTodo(id = "", title = "", createdAt = 0L))
    }
    var initialized by rememberSaveable(id) { mutableStateOf(id.isEmpty()) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val item = state.items.find { it.id == id }
    LaunchedEffect(item, initialized) {
        if (!initialized && item != null) {
            snapshot = item
            draft = TodoDraft.from(item)
            initialized = true
            Log.d(TAG, "Initialized task editor once")
        }
    }
    val original = snapshot.takeIf { it.id.isNotEmpty() }
    val changed = initialized && draft != (original?.let(TodoDraft::from) ?: TodoDraft())
    val close = {
        if (!state.saving) {
            Log.d(TAG, "Closing editor; unsaved=$changed")
            if (changed) discard = true else onBack()
        }
    }
    BackHandler(onBack = close)
    TodoEditorContent(state, id.isEmpty(), initialized, changed, draft, { draft = it }, viewModel,
        onBack = close, onDelete = { delete = true }, onSave = { viewModel.save(draft, original, onBack) })
    if (discard) ConfirmTodoDialog("放弃修改？", "未保存的待办内容将丢失。", "放弃修改", { discard = false }) {
        discard = false
        onBack()
    }
    if (delete) ConfirmTodoDialog("删除待办？", "删除这条待办，关联联系人会保留。", "删除", { delete = false }) {
        delete = false
        viewModel.delete(id, onBack)
    }
}

@Composable
private fun TodoEditorContent(
    state: TodoUiState, creating: Boolean, initialized: Boolean, changed: Boolean,
    draft: TodoDraft, onDraftChange: (TodoDraft) -> Unit, viewModel: TodoViewModel,
    onBack: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit,
) {
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TodoEditorTopBar(creating, initialized, !state.saving,
            canSave = initialized && state.loaded && changed && draft.title.isNotBlank() && !state.saving,
            onBack = onBack, onDelete = onDelete, onSave = onSave)
    }) { padding ->
        if (initialized) {
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(BadgerSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
                TodoEditorFields(draft, !state.saving, onDraftChange)
                TodoContactPicker(state.contacts, draft.contacts, !state.saving, viewModel) {
                    onDraftChange(draft.copy(contacts = it))
                }
                if (!creating) CheckboxPreference(title = "已完成", checked = draft.completed,
                    enabled = !state.saving, onCheckedChange = { onDraftChange(draft.copy(completed = it)) })
                state.error?.let { TodoError(it, viewModel::reload) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(BadgerSpacing.xl)) {
                Text(if (state.loading) "正在读取待办…" else if (state.loaded) "这条待办已不存在，请返回列表" else "待办暂时无法读取")
                state.error?.let { TodoError(it, viewModel::reload) }
            }
        }
    }
}

@Composable
private fun TodoEditorTopBar(
    creating: Boolean, initialized: Boolean, enabled: Boolean, canSave: Boolean,
    onBack: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit,
) {
    TopAppBar(title = if (creating) "新建待办" else "编辑待办", navigationIcon = {
        IconButton(onClick = onBack, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }, actions = {
        if (!creating && initialized) IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Default.Delete, "删除待办")
        }
        IconButton(onClick = onSave, enabled = canSave) {
            Icon(Icons.Default.Check, if (enabled) "保存待办" else "正在保存")
        }
    })
}

@Composable
private fun TodoEditorFields(draft: TodoDraft, enabled: Boolean, onChange: (TodoDraft) -> Unit) {
    TextField(value = draft.title, onValueChange = { onChange(draft.copy(title = it)) }, enabled = enabled,
        label = "标题（必填）", modifier = Modifier.fillMaxWidth())
    TodoDateField(draft.date, enabled) { onChange(draft.copy(date = it)) }
    TextField(value = draft.note, onValueChange = { onChange(draft.copy(note = it)) }, enabled = enabled,
        label = "补充说明", modifier = Modifier.fillMaxWidth())
    TextField(value = draft.place, onValueChange = { onChange(draft.copy(place = it)) }, enabled = enabled,
        label = "地点（选填）", modifier = Modifier.fillMaxWidth())
}

private const val TAG = "TodoEditorPageTester"
