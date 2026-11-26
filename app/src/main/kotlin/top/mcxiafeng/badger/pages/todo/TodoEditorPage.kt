package top.mcxiafeng.badger.pages.todo

import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.todo.LocalTodo
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.GlassBackButton
import top.mcxiafeng.badger.ui.glass.GlassButtonScope
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.SaveIconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.mergePagePadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField

/** An empty ID creates a task; nonempty IDs always edit an existing record. */
@Composable
fun TodoEditorPage(
    id: String, onBack: () -> Unit, onManageTypes: () -> Unit, onContactClick: (Long) -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
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
    val close = { if (!state.saving) { if (changed) discard = true else onBack() } }
    BackHandler(onBack = close)
    TodoEditorContent(state, id.isEmpty(), initialized, draft, { draft = it }, viewModel,
        onBack = close, onManageTypes = onManageTypes, onContactClick = onContactClick,
        onDelete = { delete = true }, onSave = {
            Log.d(TAG, "Save action received; editing=${original != null}")
            viewModel.save(draft, original, onBack)
        })
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
    state: TodoUiState, creating: Boolean, initialized: Boolean,
    draft: TodoDraft, onDraftChange: (TodoDraft) -> Unit, viewModel: TodoViewModel,
    onBack: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit, onManageTypes: () -> Unit,
    onContactClick: (Long) -> Unit,
) {
    val valid = initialized && state.loaded && draft.title.isNotBlank() && draft.scheduleError == null
    val canSave = valid && !state.saving
    Scaffold(progressiveTopBlur = true, modifier = Modifier.imePadding(), topBar = {
        GlassButtonScope {
            TodoEditorTopBar(creating, initialized, !state.saving, valid, canSave,
                onBack = onBack, onDelete = onDelete, onSave = onSave)
        }
    }) { padding ->
        if (initialized) {
            val enabled = !state.saving && state.loaded
            Column(Modifier.fillMaxSize().consumeWindowInsets(padding)
                .scrollTopFade().springOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(mergePagePadding(padding, PaddingValues(BadgerSpacing.xl))),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
                TodoEditorFields(state, draft, enabled, onDraftChange, onManageTypes)
                TodoContactPicker(state.contacts, draft.contacts, enabled, viewModel, onContactClick) {
                    onDraftChange(draft.copy(contacts = it))
                }
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
    creating: Boolean, initialized: Boolean, enabled: Boolean, valid: Boolean, canSave: Boolean,
    onBack: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit,
) {
    TopAppBar(title = if (creating) "新建待办" else "编辑待办", navigationIcon = {
        GlassBackButton(onBack = onBack, enabled = enabled)
    }, actions = {
        if (!creating && initialized) {
            IconButton(onClick = onDelete, enabled = enabled,
                icon = Icons.Default.Delete, contentDescription = "删除待办")
            Spacer(Modifier.width(BadgerSpacing.md))
        }
        SaveIconButton(ready = valid, enabled = canSave, onClick = onSave,
            contentDescription = if (creating) "保存待办" else "保存修改")
    })
}

@Composable
private fun TodoEditorFields(
    state: TodoUiState, draft: TodoDraft, enabled: Boolean, onChange: (TodoDraft) -> Unit,
    onManageTypes: () -> Unit,
) {
    TextField(cornerRadius = BadgerRadius.surface, value = draft.title, onValueChange = { onChange(draft.copy(title = it)) }, enabled = enabled,
        label = "标题（必填）", modifier = Modifier.fillMaxWidth())
    TextField(cornerRadius = BadgerRadius.surface, value = draft.note, onValueChange = { onChange(draft.copy(note = it)) }, enabled = enabled,
        label = "备注", modifier = Modifier.fillMaxWidth())
    TodoScheduleFields(draft, enabled, onChange)
    TodoClassificationFields(draft, state.types, enabled, onChange, onManageTypes)
    TextField(cornerRadius = BadgerRadius.surface, value = draft.place, onValueChange = { onChange(draft.copy(place = it)) }, enabled = enabled,
        label = "地点（选填）", modifier = Modifier.fillMaxWidth())
}

private const val TAG = "TodoEditorPageTester"
