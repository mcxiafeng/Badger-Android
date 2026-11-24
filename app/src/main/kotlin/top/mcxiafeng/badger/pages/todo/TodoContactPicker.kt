package top.mcxiafeng.badger.pages.todo

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun TodoContactPicker(
    contacts: List<Contact>, selected: List<Long>, enabled: Boolean,
    viewModel: TodoViewModel, onChange: (List<Long>) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val available = remember(contacts) { contacts.associate { it.id to it.name } }
    val names = remember(selected, available) { selected.mapNotNull(available::get) }
    Card(cornerRadius = BadgerRadius.card, insideMargin = PaddingValues()) {
        ArrowPreference(title = "关联联系人", enabled = enabled,
            summary = if (names.isEmpty()) "未关联联系人" else "已选择 ${names.size} 人：${names.joinToString("、")}",
            onClick = {
                expanded = true
                Log.d(TAG, "Opened multiple-contact picker")
            })
    }
    if (expanded) TodoContactDialog(selected.filter { it in available }, viewModel,
        onDismiss = { expanded = false }, onConfirm = {
            expanded = false
            Log.d(TAG, "Confirmed contact associations; count=${it.size}")
            onChange(it.filter { id -> id in available })
        })
}

@Composable
private fun TodoContactDialog(
    selected: List<Long>, viewModel: TodoViewModel, onDismiss: () -> Unit, onConfirm: (List<Long>) -> Unit,
) {
    val choices by viewModel.contactChoices.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable(stateSaver = SelectedIdsSaver) { mutableStateOf(selected) }
    LaunchedEffect(Unit) { viewModel.searchContacts(query) }
    WindowDialog(show = true, title = "关联联系人", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            TextField(value = query, onValueChange = { query = it; viewModel.searchContacts(it) },
                label = "搜索姓名、账号或标签", singleLine = true, modifier = Modifier.fillMaxWidth())
            Box(Modifier.weight(1f, fill = false)) {
                TodoContactResults(choices, checked, viewModel::retryContactSearch) { id, isChecked ->
                    checked = if (isChecked) (checked + id).distinct() else checked - id
                    Log.d(TAG, "Changed contact selection; count=${checked.size}")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onConfirm(checked) }, modifier = Modifier.weight(1f)) { Text("完成（${checked.size}）") }
            }
        }
    }
}

@Composable
private fun TodoContactResults(
    choices: TodoContactChoices, selected: List<Long>, onRetry: () -> Unit, onChange: (Long, Boolean) -> Unit,
) {
    when {
        choices.loading -> Text("正在读取联系人…")
        choices.error != null -> TodoError(choices.error, onRetry)
        choices.contacts.isEmpty() -> Box(Modifier.fillMaxWidth().padding(BadgerSpacing.lg)) {
            Text(if (choices.query.isBlank()) "还没有联系人" else "未找到匹配的联系人")
        }
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = CONTACT_LIST_MAX_HEIGHT)) {
            items(choices.contacts, key = { it.id }) { contact ->
                CheckboxPreference(title = contact.name, checked = contact.id in selected,
                    onCheckedChange = { onChange(contact.id, it) })
            }
        }
    }
}

private val SelectedIdsSaver = listSaver<List<Long>, Long>(save = { it }, restore = { it })
private val CONTACT_LIST_MAX_HEIGHT = 320.dp
private const val TAG = "TodoContactPickerTester"
