package top.mcxiafeng.badger.pages.card

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
import top.mcxiafeng.badger.data.ContactConflictAction
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.ImportConflict
import top.mcxiafeng.badger.data.executeImport
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "ImportConflictDialog"

/**
 * 导入联系人冲突对话框（共享组件）
 *
 * 所有动作/勾选状态一律以 [ContactConflict.rowId] 为键（[F6/F7] 禁止 name 键：同名联系人
 * 会互相串状态）。LazyColumn key 也用 rowId。
 *
 * @param conflicts 导入冲突列表
 * @param onExecuteImport 执行导入回调（动作表键 = rowId）
 * @param scope 协程作用域
 * @param mergeChecked 合并选中状态（键 = ContactConflict.rowId）
 * @param newStyleChecked "新建导入标签"选中状态：勾选后为该联系人额外建一个 `导入样式 N` Tag
 * @param forceImportChecked 强制导入选中状态
 * @param importChecked 导入选中状态
 * @param collectionActions 名片夹冲突动作（键 = ImportConflict.rowId；CardPage 传入，CollectionDetailPage 传 emptyMap）
 * @param renamedCollectionNames 重命名的名片夹名称（键 = ImportConflict.rowId；CardPage 传入，CollectionDetailPage 传 emptyMap）
 * @param onDismiss 对话框关闭回调
 * @param onSuccess 导入成功回调（接收结果消息）
 */
@Composable
fun ImportConflictDialog(
    conflicts: List<ImportConflict>,
    onExecuteImport: suspend (
        List<ImportConflict>,
        Map<Int, top.mcxiafeng.badger.data.CollectionConflictAction>,
        Map<Int, ContactConflictAction>,
        Map<Int, String>,
        Map<Int, Boolean>
    ) -> top.mcxiafeng.badger.data.ImportResult,
    scope: CoroutineScope,
    mergeChecked: SnapshotStateMap<Int, Boolean>,
    newStyleChecked: SnapshotStateMap<Int, Boolean>,
    forceImportChecked: SnapshotStateMap<Int, Boolean>,
    importChecked: SnapshotStateMap<Int, Boolean>,
    collectionActions: Map<Int, top.mcxiafeng.badger.data.CollectionConflictAction> = emptyMap(),
    renamedCollectionNames: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
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
                // 表头
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
                                style = TextStyle(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDuplicate) {
                                // 重复联系人：三选框
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
                                // 新联系人：单选框（导入/跳过）
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
                        // [F6/F7] 动作表按 rowId 组装，同名联系人各自独立
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
                                    Log.d(TAG, "importContacts: executed, collections=${result.importedCollections}, new=${result.importedContacts}, merged=${result.mergedContacts}")
                                    val msg = if (result.importedCollections > 0) {
                                        "导入完成：${result.importedCollections}个名片夹，${result.importedContacts}个新联系人，${result.mergedContacts}位已合并"
                                    } else {
                                        "导入完成：${result.importedContacts}位新联系人，${result.mergedContacts}位已合并"
                                    }
                                    onSuccess(msg)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "importContacts: execute failed", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
