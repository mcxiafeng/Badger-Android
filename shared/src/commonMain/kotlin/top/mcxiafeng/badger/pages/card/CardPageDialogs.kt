package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.importer.CollectionConflictAction
import top.mcxiafeng.badger.data.importer.ContactConflictAction
import top.mcxiafeng.badger.data.importer.ImportConflict
import top.mcxiafeng.badger.data.importer.ImportResult
import top.mcxiafeng.badger.data.model.CardCollectionWithCount as CollectionWithCount
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.deleteFileQuietly
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "CardPage"
// [B2 fix] Snackbar 显示时长
private const val SNACKBAR_DURATION_MS = 2000L

@Composable
internal fun CardScreenDialogs(
    successState: CardUiState.Success?,
    selectedCollectionIds: Set<Long>,
    showCollectionDeleteDialog: Boolean,
    showEditCollectionDialog: Boolean,
    showCreateDialog: Boolean,
    showImportDialog: Boolean,
    importConflicts: List<ImportConflict>?,
    importCollectionActions: Map<Int, CollectionConflictAction>,
    importRenameNames: Map<Int, String>,
    showImportRenameField: Boolean,
    importRenameInput: String,
    showContactConflictDialog: Boolean,
    mergeChecked: SnapshotStateMap<Int, Boolean>,
    newStyleChecked: SnapshotStateMap<Int, Boolean>,
    forceImportChecked: SnapshotStateMap<Int, Boolean>,
    importChecked: SnapshotStateMap<Int, Boolean>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onSelectedCollectionIdsChange: (Set<Long>) -> Unit,
    onIsInSelectionModeChange: (Boolean) -> Unit,
    onShowCollectionDeleteDialogChange: (Boolean) -> Unit,
    onShowEditCollectionDialogChange: (Boolean) -> Unit,
    onShowCreateDialogChange: (Boolean) -> Unit,
    onShowImportDialogChange: (Boolean) -> Unit,
    onImportConflictsChange: (List<ImportConflict>?) -> Unit,
    onImportCollectionActionsChange: (Map<Int, CollectionConflictAction>) -> Unit,
    onImportRenameNamesChange: (Map<Int, String>) -> Unit,
    onShowImportRenameFieldChange: (Boolean) -> Unit,
    onImportRenameInputChange: (String) -> Unit,
    onShowContactConflictDialogChange: (Boolean) -> Unit,
    onDeleteCollection: (CollectionWithCount) -> Unit,
    onUpdateCollection: suspend (CardCollectionCacheEntity) -> Unit,
    onCreateCollection: (String, String?, String?, Long?) -> Unit,
    onExecuteImport: suspend (
        List<ImportConflict>,
        Map<Int, CollectionConflictAction>,
        Map<Int, ContactConflictAction>,
        Map<Int, String>,
        Map<Int, Boolean>,
    ) -> ImportResult,
    onLaunchImportPicker: () -> Unit,
) {
    if (showCollectionDeleteDialog && selectedCollectionIds.isNotEmpty()) {
        val allCollections = successState?.collections ?: emptyList()
        val selectedItems = allCollections.filter { it.id in selectedCollectionIds }
        val count = selectedCollectionIds.size
        val message = if (count == 1 && selectedItems.isNotEmpty()) {
            "确定删除「${selectedItems.first().name}」吗？其中的联系人不会被删除。"
        } else {
            "确定删除 $count 个名片夹吗？其中的联系人不会被删除。"
        }
        BadgerConfirmDialog(
            show = true,
            title = "删除名片夹",
            message = message,
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                selectedItems.forEach { item ->
                    deleteFileQuietly(item.backgroundImagePath)
                    BadgerLog.d(TAG, "deleteCollection: id=${item.id}, bgPath=${item.backgroundImagePath} cleaned")
                    scope.launch(BadgerDispatchers.io) { onDeleteCollection(item) }
                }
                onShowCollectionDeleteDialogChange(false)
                onIsInSelectionModeChange(false)
                onSelectedCollectionIdsChange(emptySet())
            },
            onDismiss = {
                onShowCollectionDeleteDialogChange(false)
                onIsInSelectionModeChange(false)
                onSelectedCollectionIdsChange(emptySet())
            },
        )
    }

    if (showEditCollectionDialog && selectedCollectionIds.size == 1) {
        val allCollections = successState?.collections ?: emptyList()
        val item = allCollections.find { it.id in selectedCollectionIds }
        if (item != null) {
            EditCollectionDialog(
                collection = item.toCacheEntity(),
                onDismiss = {
                    onShowEditCollectionDialogChange(false)
                    onIsInSelectionModeChange(false)
                    onSelectedCollectionIdsChange(emptySet())
                },
                onConfirm = { updatedCollection ->
                    scope.launch {
                        onUpdateCollection(updatedCollection)
                        snackbarHostState.showSnackbar("名片夹已更新", duration = SnackbarDuration.Custom(SNACKBAR_DURATION_MS))
                    }
                    onShowEditCollectionDialogChange(false)
                    onIsInSelectionModeChange(false)
                    onSelectedCollectionIdsChange(emptySet())
                },
            )
        }
    }

    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { onShowCreateDialogChange(false) },
            onConfirm = { name, desc, bgPath, dominantColor ->
                onShowCreateDialogChange(false)
                onCreateCollection(name, desc, bgPath, dominantColor)
                scope.launch {
                    snackbarHostState.showSnackbar("名片夹已创建", duration = SnackbarDuration.Custom(SNACKBAR_DURATION_MS))
                }
            },
        )
    }

    LaunchedEffect(showImportDialog) {
        if (showImportDialog) {
            onShowImportDialogChange(false)
            onLaunchImportPicker()
        }
    }

    val collectionConflicts = importConflicts?.filter { it.existingCollection != null } ?: emptyList()
    val currentCollectionConflict = collectionConflicts.firstOrNull { it.rowId !in importCollectionActions }
    if (currentCollectionConflict != null) {
        WindowDialog(
            show = true,
            title = "导入「${currentCollectionConflict.collectionExport.name}」",
            onDismissRequest = {
                onImportConflictsChange(null)
                onImportCollectionActionsChange(emptyMap())
                onImportRenameNamesChange(emptyMap())
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("该名片夹已存在", modifier = Modifier.padding(bottom = BadgerSpacing.md))
                if (showImportRenameField) {
                    TextField(
                        value = importRenameInput,
                        onValueChange = onImportRenameInputChange,
                        label = "新名称",
                        modifier = Modifier.fillMaxWidth().padding(bottom = BadgerSpacing.sm),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(text = "取消", onClick = { onShowImportRenameFieldChange(false) }, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(BadgerSpacing.lgx))
                        TextButton(
                            text = "确认",
                            onClick = {
                                val name = currentCollectionConflict.collectionExport.name
                                onImportCollectionActionsChange(
                                    importCollectionActions + (currentCollectionConflict.rowId to CollectionConflictAction.RENAME),
                                )
                                onImportRenameNamesChange(
                                    importRenameNames + (currentCollectionConflict.rowId to importRenameInput.ifBlank { "${name}_2" }),
                                )
                                onShowImportRenameFieldChange(false)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    TextButton(
                        text = "合并到已有名片夹",
                        onClick = {
                            onImportCollectionActionsChange(
                                importCollectionActions + (currentCollectionConflict.rowId to CollectionConflictAction.MERGE),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                    TextButton(
                        text = "改名导入为新名片夹",
                        onClick = {
                            onImportRenameInputChange("${currentCollectionConflict.collectionExport.name}_2")
                            onShowImportRenameFieldChange(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                    TextButton(
                        text = "不要导入",
                        onClick = {
                            onImportCollectionActionsChange(
                                importCollectionActions + (currentCollectionConflict.rowId to CollectionConflictAction.SKIP),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else if (importConflicts != null && currentCollectionConflict == null && !showContactConflictDialog) {
        val allContacts = importConflicts.flatMap { it.contactConflicts }
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
        onShowContactConflictDialogChange(true)
    }

    if (showContactConflictDialog && importConflicts != null) {
        ImportConflictDialog(
            conflicts = importConflicts,
            onExecuteImport = onExecuteImport,
            scope = scope,
            mergeChecked = mergeChecked,
            newStyleChecked = newStyleChecked,
            forceImportChecked = forceImportChecked,
            importChecked = importChecked,
            collectionActions = importCollectionActions,
            renamedCollectionNames = importRenameNames,
            onDismiss = {
                onShowContactConflictDialogChange(false)
                mergeChecked.clear()
                newStyleChecked.clear()
                forceImportChecked.clear()
                importChecked.clear()
                onImportConflictsChange(null)
                onImportCollectionActionsChange(emptyMap())
                onImportRenameNamesChange(emptyMap())
            },
            onSuccess = { msg -> showToast(msg) },
        )
    }
}
