package top.mcxiafeng.badger.pages.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.model.QAuxvConflictAction
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast

private const val TAG = "PersonPage"

/**
 * PersonScreen 对话框宿主（U13 自 PersonPage 下沉）。
 *
 * 覆盖 QAuxv 解析/导入/预览/冲突 + 批量删除确认。状态仍由 Screen 持有。
 */
@Composable
internal fun PersonScreenDialogs(
    viewModel: PersonViewModel,
    qaImportState: QAuxvImportState,
    pendingSelected: List<QAuxvFriendEntry>,
    showConflictDialog: Boolean,
    showDeleteConfirmDialog: Boolean,
    selectedIds: Set<Long>,
    scope: CoroutineScope,
    onPendingSelectedChange: (List<QAuxvFriendEntry>) -> Unit,
    onShowConflictDialogChange: (Boolean) -> Unit,
    onShowDeleteConfirmDialogChange: (Boolean) -> Unit,
    onDeleteContacts: suspend (List<Long>) -> Unit,
    exitSelectMode: () -> Unit,
) {
    val qaImportProgress by viewModel.qaImportProgress.collectAsStateWithLifecycle()
    val importingSummary = qaImportProgress?.let { "${it.displayLabel()} ${it.current}/${it.total}" }
        ?: "正在写入联系人…"

    QAuxvProgressDialog(
        title = "正在解析",
        summary = "正在读取并解析文件…",
        show = qaImportState is QAuxvImportState.Parsing,
    )
    QAuxvProgressDialog(
        title = "正在导入",
        summary = importingSummary,
        show = qaImportState is QAuxvImportState.Importing,
    )
    val previewState = qaImportState as? QAuxvImportState.Preview
    if (previewState != null) {
        QAuxvPreviewDialog(
            state = previewState,
            show = true,
            onToggleCheck = viewModel::togglePreviewCheck,
            onSelectAll = viewModel::selectAllPreview,
            onDeselectAll = viewModel::deselectAllPreview,
            onCancel = {
                onShowConflictDialogChange(false)
                onPendingSelectedChange(emptyList())
                viewModel.cancelImport()
            },
            onConfirm = { selected ->
                val hasConflict = selected.any { it.uin in previewState.existingContactIdByUin }
                if (!hasConflict) {
                    val decisions = selected.map { entry ->
                        Triple(entry, null, QAuxvConflictAction.InsertAnyway)
                    }
                    viewModel.commitImport(decisions)
                } else {
                    onPendingSelectedChange(selected)
                    onShowConflictDialogChange(true)
                }
            },
        )
    }
    if (showConflictDialog) {
        val conflictMap = previewState?.existingContactIdByUin ?: emptyMap()
        QAuxvConflictDialog(
            show = true,
            selectedEntries = pendingSelected,
            existingContactIdByUin = conflictMap,
            onCancel = {
                onShowConflictDialogChange(false)
                onPendingSelectedChange(emptyList())
            },
            onResolve = { decisions ->
                onShowConflictDialogChange(false)
                onPendingSelectedChange(emptyList())
                viewModel.commitImport(decisions)
            },
        )
    }

    if (showDeleteConfirmDialog) {
        BadgerConfirmDialog(
            show = true,
            title = "删除联系人",
            message = "确定要删除选中的 ${selectedIds.size} 个联系人吗？此操作不可撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onShowDeleteConfirmDialogChange(false)
                val idsToDelete = selectedIds.toList()
                BadgerLog.d(TAG, "PersonScreen: delete confirm pressed, ids=$idsToDelete")
                scope.launch {
                    onDeleteContacts(idsToDelete)
                    showToast("已删除 ${idsToDelete.size} 个联系人")
                    exitSelectMode()
                }
            },
            onDismiss = { onShowDeleteConfirmDialogChange(false) },
        )
    }
}
