package top.mcxiafeng.badger.pages.todo

import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.ui.components.ColorPicker
import top.mcxiafeng.badger.ui.components.TextField
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.DeleteIconButton
import top.mcxiafeng.badger.ui.glass.GlassActionGroup
import top.mcxiafeng.badger.ui.glass.GlassBackButton
import top.mcxiafeng.badger.ui.glass.GlassButtonScope
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.mcxiafeng.badger.ui.glass.mergePagePadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodoTypeManagerPage(onBack: () -> Unit, viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    // Plain List<String> only round-trips through the bundle via an explicit saver.
    var selectedIds by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) { mutableStateOf(emptyList<String>()) }
    val enabled = state.loaded && !state.saving
    val custom = state.types.filterNot { it.isPreset }
    // Drop ids whose type vanished meanwhile (external reload) so the count never lies.
    val selected = custom.filter { it.id in selectedIds }
    val exitSelection: () -> Unit = {
        selecting = false
        selectedIds = emptyList()
        Log.d(TAG, "Exited type selection mode")
    }
    LaunchedEffect(custom.isEmpty()) { if (selecting && custom.isEmpty()) exitSelection() }
    BackHandler(enabled = selecting) { if (!state.saving) exitSelection() }
    Scaffold(progressiveTopBlur = true, modifier = Modifier.imePadding(), topBar = {
        TodoTypeTopBar(
            selecting = selecting,
            selected = selected,
            enabled = enabled,
            hasCustom = custom.isNotEmpty(),
            onBack = onBack,
            onCreate = { editor = "" },
            onEnterSelection = {
                selectedIds = emptyList()
                selecting = true
                Log.d(TAG, "Entered type selection mode")
            },
            onExitSelection = exitSelection,
            onDelete = { viewModel.deleteTypes(it, onDeleted = exitSelection) },
            saving = state.saving,
        )
    }) { padding ->
        TodoTypeList(custom, enabled, selecting, selected, padding, state.error, viewModel::reload,
            onEdit = { editor = typeSnapshotGson.toJson(it) },
            onToggle = { type ->
                selectedIds = if (type.id in selectedIds) selectedIds - type.id else selectedIds + type.id
                Log.d(TAG, "Changed type selection; count=${selectedIds.size}")
            })
    }
    editor?.let { snapshot ->
        TodoTypeEditor(snapshot.takeIf(String::isNotEmpty)?.let { typeSnapshotGson.fromJson(it, TodoType::class.java) },
            state, onDismiss = { if (!state.saving) editor = null }, onSave = { type, name, color ->
                viewModel.saveType(type, name, color) { editor = null }
            })
    }
}

/** Same capsule as the todo list's toolbar: create stays on the left, selection joins on the right. */
@Composable
private fun TodoTypeTopBar(
    selecting: Boolean, selected: List<TodoType>, enabled: Boolean, hasCustom: Boolean, saving: Boolean,
    onBack: () -> Unit, onCreate: () -> Unit, onEnterSelection: () -> Unit, onExitSelection: () -> Unit,
    onDelete: (List<TodoType>) -> Unit,
) {
    AnimatedContent(
        targetState = selecting,
        transitionSpec = {
            fadeIn(tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(tween(BadgerMotion.DURATION_TOUCH_FEEDBACK, easing = FastOutSlowInEasing)))
        },
        label = "TodoTypeTopBarMode",
    ) { inSelection ->
        if (inSelection) {
            TopAppBar(
                title = "已选择 ${selected.size} 项",
                color = Color.Transparent,
                navigationIcon = { TodoCancelButton(onCancel = onExitSelection, enabled = !saving) },
                actions = {
                    GlassButtonScope {
                        TodoTypeDeleteAction(selected, enabled = !saving, onDelete = onDelete)
                    }
                },
            )
        } else {
            TopAppBar(
                title = "待办类型",
                // Navigation keeps its glass lens even though this page's content buttons are flat.
                navigationIcon = { GlassBackButton(onBack = onBack, enabled = enabled) },
                actions = {
                    GlassActionGroup {
                        IconButton(enabled = enabled, onClick = onCreate,
                            icon = Icons.Default.Add, contentDescription = "新建类型")
                        // Only presets are read-only, so selection needs at least one custom type.
                        val canSelect = enabled && hasCustom
                        IconButton(enabled = canSelect, onClick = onEnterSelection) {
                            Icon(Icons.Default.Checklist, "选择类型",
                                tint = if (canSelect) LocalContentColor.current
                                else MiuixTheme.colorScheme.disabledOnSurface)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TodoTypeList(
    custom: List<TodoType>, enabled: Boolean, selecting: Boolean, selected: List<TodoType>,
    padding: PaddingValues, error: String?, onRetry: () -> Unit,
    onEdit: (TodoType) -> Unit, onToggle: (TodoType) -> Unit,
) {
    LazyColumn(Modifier.consumeWindowInsets(padding).scrollTopFade().springOverscroll(),
        contentPadding = mergePagePadding(padding, PaddingValues(vertical = BadgerSpacing.md))) {
        error?.let { message -> item { TodoError(message, onRetry) } }
        item { SmallTitle("自定义") }
        if (custom.isEmpty()) item { Text("还没有自定义类型", modifier = Modifier.padding(BadgerSpacing.lg)) }
        items(custom, key = { it.id }) { type ->
            if (selecting) {
                TodoTypeOption(title = type.name, type = type, checked = type in selected, enabled = enabled,
                    onClick = { onToggle(type) })
            } else {
                ArrowPreference(title = type.name, startAction = { TodoTypeSwatch(type) }, enabled = enabled,
                    onClick = { onEdit(type) })
            }
        }
        item { SmallTitle("预设颜色") }
        items(TodoType.presets, key = { it.id }) { type ->
            TodoTypeOption(title = type.name, type = type)
        }
    }
}

/** Selection-mode trailing delete, mirroring the todo list's circular gray-to-red affordance. */
@Composable
private fun TodoTypeDeleteAction(selected: List<TodoType>, enabled: Boolean, onDelete: (List<TodoType>) -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    // The confirm flag must never outlive the selection it was opened for.
    LaunchedEffect(selected.isEmpty()) { if (selected.isEmpty()) confirming = false }
    DeleteIconButton(
        ready = selected.isNotEmpty(),
        enabled = enabled,
        onClick = {
            if (selected.isEmpty()) {
                Log.d(TAG, "Ignored type deletion; nothing is selected")
            } else {
                confirming = true
                Log.d(TAG, "Opened type deletion confirmation; count=${selected.size}")
            }
        },
        contentDescription = "删除${selected.size}项类型",
    )
    if (confirming && selected.isNotEmpty()) {
        val impact = if (selected.size == 1) "使用该类型的待办会显示为无类型。" else "使用这些类型的待办会显示为无类型。"
        ConfirmTodoDialog("删除 ${selected.size} 项类型？", "删除后无法恢复，$impact", "删除",
            onDismiss = { confirming = false }) {
            confirming = false
            Log.d(TAG, "Confirmed task-type deletion; count=${selected.size}")
            onDelete(selected)
        }
    }
}

@Composable
private fun TodoTypeEditor(
    type: TodoType?, state: TodoUiState, onDismiss: () -> Unit,
    onSave: (TodoType?, String, Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(type?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(type?.color ?: TodoType.presets.first().color) }
    WindowDialog(show = true, title = if (type == null) "新建类型" else "编辑类型", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            TextField(cornerRadius = BadgerRadius.surface, value = name, onValueChange = { name = it }, label = "类型名称", enabled = !state.saving,
                singleLine = true, modifier = Modifier.fillMaxWidth())
            ColorPicker(color = Color(color), onColorChanged = { color = it.copy(alpha = 1f).toArgb().toLong() and ARGB_MASK })
            state.error?.let { Text(it) }
        }
        TodoDialogActions("保存", onDismiss, enabled = !state.saving && name.isNotBlank(), onConfirm = {
            Log.d(TAG, "Saving task type; create=${type == null}")
            onSave(type, name, color)
        })
    }
}

@Composable
internal fun TodoTypeSwatch(type: TodoType) {
    Box(Modifier.size(BadgerSpacing.md).background(Color(type.color), CircleShape))
}

private val typeSnapshotGson = Gson()
private const val ARGB_MASK = 0xFFFFFFFFL
private const val TAG = "TodoTypeManagerPageTester"
