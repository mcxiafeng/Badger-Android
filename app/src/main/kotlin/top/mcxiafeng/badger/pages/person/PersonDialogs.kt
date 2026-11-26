package top.mcxiafeng.badger.pages.person

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.QAuxvConflictAction
import top.mcxiafeng.badger.data.QAuxvFriendEntry
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.glass.WindowDialog

@Composable
internal fun PersonDeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    WindowDialog(show = true, title = "删除联系人",
        summary = "确定要删除选中的 $count 个联系人吗？此操作不可撤销。", onDismissRequest = onDismiss) {
        DialogButtonRow(positiveText = "删除", onNegative = onDismiss, onPositive = onConfirm, isDestructive = true)
    }
}

@Composable
internal fun PersonImportDialogs(viewModel: PersonViewModel) {
    val state by viewModel.qaImportState.collectAsStateWithLifecycle()
    val progress by viewModel.qaImportProgress.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<List<QAuxvFriendEntry>>(emptyList()) }
    var showConflict by remember { mutableStateOf(false) }
    val preview = state as? QAuxvImportState.Preview
    BackHandler(enabled = state is QAuxvImportState.Parsing || state is QAuxvImportState.Importing) {}
    PersonImportFeedback(viewModel)
    QAuxvProgressDialog("正在解析", "正在读取并解析文件…", state is QAuxvImportState.Parsing)
    QAuxvProgressDialog("正在导入", progress?.let { "${it.displayLabel()} ${it.current}/${it.total}" } ?: "正在写入联系人…",
        state is QAuxvImportState.Importing)
    if (preview != null) QAuxvPreviewDialog(state = preview, show = true,
        onToggleCheck = viewModel::togglePreviewCheck, onSelectAll = viewModel::selectAllPreview,
        onDeselectAll = viewModel::deselectAllPreview,
        onCancel = { showConflict = false; selected = emptyList(); viewModel.cancelImport() },
        onConfirm = { entries ->
            if (entries.any { it.uin in preview.existingContactIdByUin }) { selected = entries; showConflict = true }
            else viewModel.commitImport(entries.map { Triple(it, null, QAuxvConflictAction.InsertAnyway) })
        })
    if (showConflict) QAuxvConflictDialog(show = true, selectedEntries = selected,
        existingContactIdByUin = preview?.existingContactIdByUin ?: emptyMap(),
        onCancel = { showConflict = false; selected = emptyList() },
        onResolve = { decisions -> showConflict = false; selected = emptyList(); viewModel.commitImport(decisions) })
}

@Composable
private fun PersonImportFeedback(viewModel: PersonViewModel) {
    val context = LocalContext.current
    val result by viewModel.qaImportResult.collectAsStateWithLifecycle()
    val error by viewModel.qaImportError.collectAsStateWithLifecycle()
    LaunchedEffect(result, error) {
        val text = result?.let { "新增 ${it.inserted} / 替换 ${it.replaced} / 跳过 ${it.skipped}" }
            ?: error?.let { "导入失败: $it" }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            viewModel.consumeImportResult()
        }
    }
}

@Composable
internal fun PersonSelectionFeedback(viewModel: PersonViewModel) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }
}
