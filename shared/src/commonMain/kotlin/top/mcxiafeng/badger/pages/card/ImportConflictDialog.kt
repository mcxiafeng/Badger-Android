package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.importer.ContactConflictAction
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.importer.ImportConflict
import top.mcxiafeng.badger.data.importer.executeImport
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast

private const val TAG = "ImportConflictDialog"

@Composable
fun ImportConflictDialog(
    conflicts: List<ImportConflict>,
    onExecuteImport: suspend (
        List<ImportConflict>,
        Map<Int, top.mcxiafeng.badger.data.importer.CollectionConflictAction>,
        Map<Int, ContactConflictAction>,
        Map<Int, String>,
        Map<Int, Boolean>
    ) -> top.mcxiafeng.badger.data.importer.ImportResult,
    scope: CoroutineScope,
    mergeChecked: SnapshotStateMap<Int, Boolean>,
    newStyleChecked: SnapshotStateMap<Int, Boolean>,
    forceImportChecked: SnapshotStateMap<Int, Boolean>,
    importChecked: SnapshotStateMap<Int, Boolean>,
    collectionActions: Map<Int, top.mcxiafeng.badger.data.importer.CollectionConflictAction> = emptyMap(),
    renamedCollectionNames: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val allContacts = conflicts.flatMap { it.contactConflicts }

    if (allContacts.isEmpty()) {
        WindowDialog(
            show = true,
            title = "导入联系人",
            onDismissRequest = onDismiss
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("没找到可导入的联系人", style = MiuixTheme.textStyles.body2)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = "确定",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        val duplicateCount = allContacts.count { it.existingContact != null }
        WindowDialog(
            show = true,
            title = if (duplicateCount > 0) "导入联系人（${duplicateCount}重复）" else "导入联系人（${allContacts.size}）",
            onDismissRequest = onDismiss
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(36.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("名称", style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
                    if (duplicateCount > 0) {
                        Text("合并信息", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                        Text("新建导入标签", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                        Text("新联系人", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                    } else {
                        Text("导入", style = MiuixTheme.textStyles.body2, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allContacts, key = { it.rowId }) { cc ->
                        val rowId = cc.rowId
                        val name = cc.contactExport.name
                        val isDuplicate = cc.existingContact != null
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(
                                name = name,
                                avatarUrl = cc.contactExport.avatarUrl,
                                size = 36
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                name,
                                style = MiuixTheme.textStyles.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDuplicate) {
                                
                                Checkbox(
                                    state = if (mergeChecked[rowId] ?: true) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = mergeChecked[rowId] ?: true
                                        mergeChecked[rowId] = !current
                                        if (!current) {
                                            forceImportChecked[rowId] = false
                                            newStyleChecked[rowId] = false
                                        }
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                                if (!(forceImportChecked[rowId] ?: false)) {
                                    Checkbox(
                                        state = if (newStyleChecked[rowId] ?: false) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            val current = newStyleChecked[rowId] ?: false
                                            if (!current && !(mergeChecked[rowId] ?: true)) {
                                                mergeChecked[rowId] = true
                                            }
                                            newStyleChecked[rowId] = !current
                                        },
                                        modifier = Modifier.width(72.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(72.dp))
                                }
                                Checkbox(
                                    state = if (forceImportChecked[rowId] ?: false) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = forceImportChecked[rowId] ?: false
                                        forceImportChecked[rowId] = !current
                                        if (!current) {
                                            mergeChecked[rowId] = false
                                            newStyleChecked[rowId] = true
                                        }
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                            } else {
                                
                                Checkbox(
                                    state = if (importChecked[rowId] ?: true) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        val current = importChecked[rowId] ?: true
                                        importChecked[rowId] = !current
                                    },
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(text = "确认", onClick = {
                        
                        val contactActions = mutableMapOf<Int, ContactConflictAction>()
                        val contactAddStyleMap = mutableMapOf<Int, Boolean>()
                        for (cc in allContacts) {
                            val rowId = cc.rowId
                            if (cc.existingContact != null) {
                                val m = mergeChecked[rowId] ?: true
                                val n = newStyleChecked[rowId] ?: false
                                val f = forceImportChecked[rowId] ?: false
                                when {
                                    m -> {
                                        contactActions[rowId] = ContactConflictAction.MERGE
                                        contactAddStyleMap[rowId] = n
                                    }
                                    f -> {
                                        contactActions[rowId] = ContactConflictAction.FORCE_IMPORT
                                        contactAddStyleMap[rowId] = n
                                    }
                                    else -> {
                                        contactActions[rowId] = ContactConflictAction.SKIP
                                    }
                                }
                            } else {
                                if (importChecked[rowId] != false) {
                                    contactActions[rowId] = ContactConflictAction.FORCE_IMPORT
                                } else {
                                    contactActions[rowId] = ContactConflictAction.SKIP
                                }
                            }
                        }
                        scope.launch {
                            try {
                                val result = onExecuteImport(
                                    conflicts,
                                    collectionActions,
                                    contactActions,
                                    renamedCollectionNames,
                                    contactAddStyleMap
                                )
                                withContext(Dispatchers.Main) {
                                    BadgerLog.d(TAG, "importContacts: executed, collections=${result.importedCollections}, new=${result.importedContacts}, merged=${result.mergedContacts}")
                                    val msg = if (result.importedCollections > 0) {
                                        "导入完成：${result.importedCollections}个名片夹，${result.importedContacts}个新联系人，${result.mergedContacts}位已合并"
                                    } else {
                                        "导入完成：${result.importedContacts}位新联系人，${result.mergedContacts}位已合并"
                                    }
                                    onSuccess(msg)
                                }
                            } catch (e: Exception) {
                                BadgerLog.e(TAG, "importContacts: execute failed", e)
                                withContext(Dispatchers.Main) {
                                    showToast("导入失败: ${e.message}")
                                }
                            }
                        }
                        onDismiss()
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
