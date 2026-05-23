package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.ContactRepository
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.collections.iterator

/**
 * 名片夹选择弹窗（B站收藏夹风格）
 *
 * 展示所有名片夹列表，支持多选（Checkbox），
 * 已加入的名片夹默认勾选，取消勾选则移除。
 * 底部支持新建名片夹。
 *
 * @param repository 数据仓库
 * @param contactId 联系人 ID
 * @param currentCollectionIds 联系人当前所在的名片夹 ID 集合
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，参数为 (新增的名片夹ID集合, 移除的名片夹ID集合)
 */
@Composable
internal fun CollectionPickerDialog(
    repository: ContactRepository,
    contactId: Long,
    currentCollectionIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (addedIds: Set<Long>, removedIds: Set<Long>) -> Unit
) {
    val scope = rememberCoroutineScope()

    var collections by remember { mutableStateOf<List<CardCollection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val checkedMap = remember { mutableStateMapOf<Long, Boolean>().apply {
        currentCollectionIds.forEach { put(it, true) }
    }}

    var showCreateField by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.getAllCollections().collect { list ->
            collections = list
            isLoading = false
        }
    }

    WindowDialog(
        show = true,
        title = "添加到名片夹",
        onDismissRequest = onDismiss
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (collections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无名片夹", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(collections, key = { it.id }) { collection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (checkedMap[collection.id] == true) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                checkedMap[collection.id] = !(checkedMap[collection.id] ?: false)
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = collection.name, style = MiuixTheme.textStyles.body1)
                            if (!collection.description.isNullOrBlank()) {
                                Text(
                                    text = collection.description,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showCreateField) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = "新名片夹名称",
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                TextButton(
                    text = "创建",
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            scope.launch {
                                val id = repository.insertCollection(
                                    CardCollection(name = newCollectionName.trim())
                                )
                                checkedMap[id] = true
                                newCollectionName = ""
                                showCreateField = false
                            }
                        }
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateField = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新建名片夹",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "确定",
                onClick = {
                    val addedIds = mutableSetOf<Long>()
                    val removedIds = mutableSetOf<Long>()
                    for ((id, checked) in checkedMap) {
                        if (checked && id !in currentCollectionIds) {
                            addedIds.add(id)
                        } else if (!checked && id in currentCollectionIds) {
                            removedIds.add(id)
                        }
                    }
                    onConfirm(addedIds, removedIds)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}