package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 名片夹选择弹窗（B站收藏夹风格）
 *
 * 展示所有名片夹列表，支持多选（Checkbox），
 * 已加入的名片夹默认勾选，取消勾选则移除。
 * 底部支持新建名片夹。
 *
 * 基于 [BadgerDialog] 封装。
 *
 * @param collectionRepository 名片夹数据仓库
 * @param contactId 联系人 ID
 * @param currentCollectionIds 联系人当前所在的名片夹 ID 集合
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，参数为 (新增的名片夹ID集合, 移除的名片夹ID集合)
 */
@Composable
internal fun CollectionPickerDialog(
    collectionRepository: CollectionRepository,
    contactId: Long,
    currentCollectionIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (addedIds: Set<Long>, removedIds: Set<Long>) -> Unit
) {
    val scope = rememberCoroutineScope()

    var collections by remember { mutableStateOf<List<CardCollection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val checkedMap = remember(currentCollectionIds) { mutableStateMapOf<Long, Boolean>().apply {
        currentCollectionIds.forEach { put(it, true) }
    }}

    var showCreateField by remember { mutableStateOf(false) }
    var newCollectionName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        collectionRepository.getAllCollections().collect { list ->
            collections = list
            isLoading = false
        }
    }

    BadgerDialog(
        show = true,
        title = "添加到名片夹",
        onDismissRequest = onDismiss,
        onPositive = {
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
                Text("暂无名片夹", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
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
                                val id = collectionRepository.insertCollection(
                                    CardCollection(
                                        name = newCollectionName.trim(),
                                        createTime = System.currentTimeMillis(),
                                    )
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
                    contentDescription = "新建名片夹",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "新建名片夹",
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
    }
}