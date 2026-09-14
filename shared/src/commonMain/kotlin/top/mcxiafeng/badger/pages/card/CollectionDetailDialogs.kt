package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.importer.ImportConflict
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

private const val TAG = "CollectionDetailDialogs"

@Composable
internal fun CollectionDetailDialogs(
    viewModel: CardViewModel,
    collectionId: Long,
    collection: CardCollection?,
    
    showBatchRemoveDialog: Boolean,
    onDismissBatchRemove: () -> Unit,
    selectedContactIds: Set<Long>,
    exitSelectionMode: () -> Unit,
    
    showAddChoiceDialog: Boolean,
    onDismissAddChoice: () -> Unit,
    onOpenContactPicker: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToCreateContact: () -> Unit,
    
    showEditDialog: Boolean,
    onDismissEdit: () -> Unit,
    onEditConfirm: (CardCollection) -> Unit,
    
    showDeleteDialog: Boolean,
    onDismissDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    
    showContactPicker: Boolean,
    onDismissContactPicker: () -> Unit,
    onContactSelected: (Contact) -> Unit,
    searchContacts: (String) -> Flow<List<Contact>>,
    
    importContactConflicts: List<ImportConflict>?,
    showContactConflictDialog: Boolean,
    setShowContactConflictDialog: (Boolean) -> Unit,
    mergeChecked: SnapshotStateMap<Int, Boolean>,
    newStyleChecked: SnapshotStateMap<Int, Boolean>,
    forceImportChecked: SnapshotStateMap<Int, Boolean>,
    importChecked: SnapshotStateMap<Int, Boolean>,
    onDismissConflict: () -> Unit,
) {
        val scope = rememberCoroutineScope()

    
    if (showBatchRemoveDialog && selectedContactIds.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = "移除联系人",
            summary = "确定要从「${collection?.name.orEmpty()}」移除选中的 ${selectedContactIds.size} 个联系人吗？联系人本身不会被删除。",
            onDismissRequest = onDismissBatchRemove
        ) {
            DialogButtonRow(
                positiveText = "移除",
                isDestructive = true,
                onNegative = onDismissBatchRemove,
                onPositive = {
                    onDismissBatchRemove()
                    val ids = selectedContactIds.toList()
                    scope.launch(BadgerDispatchers.io) {
                        viewModel.removeContactsFromCollection(ids, collectionId)
                        withContext(Dispatchers.Main) {
                            BadgerLog.d(TAG, "batchRemove: count=${ids.size}")
                            showToast("已移除 ${ids.size} 个联系人")
                            exitSelectionMode()
                        }
                    }
                }
            )
        }
    }

    
    if (showAddChoiceDialog) {
        WindowDialog(
            show = true,
            title = "添加联系人",
            onDismissRequest = onDismissAddChoice
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "从已有联系人添加",
                    summary = "从通讯录中选择",
                    onClick = onOpenContactPicker
                )
                BasicComponent(
                    title = "扫码添加",
                    summary = "扫描名片二维码",
                    onClick = onNavigateToScanner
                )
                BasicComponent(
                    title = "手动添加",
                    summary = "手动输入联系人信息",
                    onClick = onNavigateToCreateContact
                )
            }
        }
    }

    
    if (showEditDialog && collection != null) {
        EditCollectionDialog(
            collection = collection!!,
            onDismiss = onDismissEdit,
            onConfirm = onEditConfirm
        )
    }

    
    if (showDeleteDialog && collection != null) {
        WindowDialog(
            show = true,
            title = "删除名片夹",
            summary = "确定删除「${collection!!.name}」吗？其中的联系人不会被删除。",
            onDismissRequest = onDismissDelete
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(text = "取消", onClick = onDismissDelete, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        onDismissDelete()
                        onDeleteConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    
    if (showContactPicker) {
        ContactSelectDialog(
            searchContacts = searchContacts,
            onDismiss = onDismissContactPicker,
            onContactSelected = onContactSelected
        )
    }

    
    if (importContactConflicts != null && !showContactConflictDialog) {
        val allContacts = importContactConflicts!!.flatMap { it.contactConflicts }
        mergeChecked.clear()
        newStyleChecked.clear()
        forceImportChecked.clear()
        importChecked.clear()
        allContacts.forEach { cc ->
            if (cc.existingContact != null) {
                mergeChecked[cc.rowId] = true
                newStyleChecked[cc.rowId] = false
                forceImportChecked[cc.rowId] = false
            } else {
                importChecked[cc.rowId] = true
            }
        }
        setShowContactConflictDialog(true)
    }

    if (showContactConflictDialog && importContactConflicts != null) {
        ImportConflictDialog(
            conflicts = importContactConflicts!!,
            onExecuteImport = viewModel::executeImport,
            scope = scope,
            mergeChecked = mergeChecked,
            newStyleChecked = newStyleChecked,
            forceImportChecked = forceImportChecked,
            importChecked = importChecked,
            onDismiss = onDismissConflict,
            onSuccess = { msg ->
                showToast(msg)
            }
        )
    }
}
