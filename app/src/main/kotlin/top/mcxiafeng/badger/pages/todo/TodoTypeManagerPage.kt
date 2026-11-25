package top.mcxiafeng.badger.pages.todo

import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import top.mcxiafeng.badger.data.todo.TodoType
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.GlassButtonScope
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.mcxiafeng.badger.ui.glass.mergePagePadding
import top.mcxiafeng.badger.ui.components.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun TodoTypeManagerPage(onBack: () -> Unit, viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = state.loaded && !state.saving
    Scaffold(progressiveTopBlur = true, modifier = Modifier.imePadding(), topBar = {
        GlassButtonScope {
            TopAppBar(title = "待办类型", navigationIcon = {
                IconButton(onClick = onBack, enabled = enabled,
                    icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }, actions = {
                IconButton(enabled = enabled, onClick = { editor = "" },
                    icon = Icons.Default.Add, contentDescription = "新建类型")
            })
        }
    }) { padding ->
        LazyColumn(Modifier.consumeWindowInsets(padding),
            contentPadding = mergePagePadding(padding, PaddingValues(vertical = BadgerSpacing.md))) {
            state.error?.let { message -> item { TodoError(message, viewModel::reload) } }
            item { SmallTitle("自定义") }
            val custom = state.types.filterNot { it.isPreset }
            if (custom.isEmpty()) item { Text("还没有自定义类型", modifier = Modifier.padding(BadgerSpacing.lg)) }
            items(custom, key = { it.id }) { type ->
                ArrowPreference(title = type.name, startAction = { TodoTypeSwatch(type) }, enabled = enabled,
                    onClick = { editor = typeSnapshotGson.toJson(type) }, endActions = {
                        DeleteTodoTypeAction(type, enabled, viewModel)
                    })
            }
            item { SmallTitle("预设颜色") }
            items(TodoType.presets, key = { it.id }) { type ->
                TodoTypeOption(title = type.name, type = type)
            }
        }
    }
    editor?.let { snapshot ->
        TodoTypeEditor(snapshot.takeIf(String::isNotEmpty)?.let { typeSnapshotGson.fromJson(it, TodoType::class.java) },
            state, onDismiss = { if (!state.saving) editor = null }, onSave = { type, name, color ->
                viewModel.saveType(type, name, color) { editor = null }
            })
    }
}

@Composable
private fun DeleteTodoTypeAction(type: TodoType, enabled: Boolean, viewModel: TodoViewModel) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    IconButton(enabled = enabled, onClick = { confirm = true }, minWidth = BadgerSize.minimumTouchTarget,
        minHeight = BadgerSize.minimumTouchTarget) { Icon(Icons.Default.Delete, "删除${type.name}") }
    if (confirm) ConfirmTodoDialog("删除「${type.name}」？", "使用此类型的待办会显示为无类型。", "删除",
        onDismiss = { confirm = false }) {
        confirm = false
        viewModel.deleteType(type) {}
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
