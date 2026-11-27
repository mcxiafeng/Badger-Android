package top.mcxiafeng.badger.pages.server

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.network.core.ServerAddress
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private const val TAG = "ServerAddressesListTester"
private val ADDRESS_MARKER_SIZE = 36.dp
private val STATUS_ICON_SIZE = 14.dp

@Composable
internal fun AddressListSection(
    servers: List<ServerAddress>,
    activeBase: String?,
    testing: Set<String>,
    testResults: Map<String, AddressTestResult>,
    onReorder: (List<String>) -> Unit,
    onEdit: (ServerAddress) -> Unit,
    onRemove: (ServerAddress) -> Unit,
    onAdd: () -> Unit,
) {
    if (servers.isEmpty()) {
        // 空状态规范：简洁提示 + 主题色"点击添加"，禁止可滚动
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (activeBase == null) "还没有连接地址" else "还没有备用地址",
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            Text("点击添加", Modifier.clickable { onAdd() }, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
        }
        return
    }
    val ordered = remember(servers) { mutableStateListOf<ServerAddress>().apply { addAll(servers) } }
    val rowBounds = remember(servers) { mutableMapOf<String, Pair<Float, Float>>() }
    var expandedUrl by remember { mutableStateOf<String?>(null) }
    var draggingUrl by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var dropIndex by remember { mutableStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        ordered.forEachIndexed { index, address ->
            key(address.url) {
                AddressRow(
                    index = index,
                    address = address,
                    dragOffset = if (draggingUrl == address.url) pointerY - startY else 0f,
                    isDragging = draggingUrl == address.url,
                    isActive = address.url.equals(activeBase, ignoreCase = true),
                    expanded = expandedUrl == address.url,
                    testResult = testResults[address.url],
                    testing = address.url in testing,
                    onExpand = { expandedUrl = if (expandedUrl == address.url) null else address.url },
                    onEdit = { onEdit(address) },
                    onRemove = { onRemove(address) },
                    onPosition = { top, bottom -> rowBounds[address.url] = top to bottom },
                    onDragStart = { y ->
                        draggingUrl = address.url
                        pointerY = y
                        startY = y
                        dropIndex = index
                        Log.d(TAG, "开始拖动地址排序")
                    },
                    onDrag = { delta ->
                        pointerY += delta
                        val first = ordered.firstOrNull()?.let { rowBounds[it.url]?.first }
                        val last = ordered.lastOrNull()?.let { rowBounds[it.url]?.second }
                        dropIndex = when {
                            first != null && pointerY < first -> 0
                            last != null && pointerY > last -> ordered.lastIndex
                            else -> ordered.indices.minByOrNull { target ->
                                rowBounds[ordered[target].url]?.let { bounds ->
                                    abs(pointerY - (bounds.first + bounds.second) / 2f)
                                } ?: Float.MAX_VALUE
                            } ?: dropIndex
                        }
                    },
                    onDragFinish = { completed ->
                        val from = ordered.indexOfFirst { it.url == draggingUrl }
                        if (completed && from >= 0 && dropIndex >= 0 && from != dropIndex) {
                            ordered.add(dropIndex, ordered.removeAt(from))
                            onReorder(ordered.map { it.url })
                            Log.d(TAG, "拖动排序已提交")
                        } else {
                            Log.d(TAG, "拖动排序未变更或已取消")
                        }
                        draggingUrl = null
                        dropIndex = -1
                    },
                )
            }
        }
    }
}

@Composable
private fun AddressRow(
    index: Int,
    address: ServerAddress,
    dragOffset: Float,
    isDragging: Boolean,
    isActive: Boolean,
    expanded: Boolean,
    testResult: AddressTestResult?,
    testing: Boolean,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPosition: (Float, Float) -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinish: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var handleTop by remember { mutableStateOf(0f) }
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinish by rememberUpdatedState(onDragFinish)
    Card(
        modifier = Modifier.fillMaxWidth()
            .onGloballyPositioned {
                val top = it.positionInRoot().y
                onPosition(top, top + it.size.height)
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            ,
    ) {
        Column(modifier = Modifier.padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = BadgerSpacing.sm),
                )
                Column(modifier = Modifier.weight(1f).clickable(indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    Log.d(TAG, "Address expansion requested; expanded=$expanded")
                    onExpand()
                }.padding(vertical = BadgerSpacing.sm)) {
                    Text(
                        text = address.name.ifBlank { address.url },
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (testing) {
                            CircularProgressIndicator(size = SPINNER_SIZE, strokeWidth = SPINNER_STROKE)
                        } else if (testResult != null) {
                            Icon(
                                imageVector = if (testResult.success) Icons.Outlined.Check else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (testResult.success) colors.primary else colors.error,
                                modifier = Modifier.size(STATUS_ICON_SIZE),
                            )
                        }
                        if (testing || testResult != null) Spacer(modifier = Modifier.width(BadgerSpacing.xs))
                        val status = when {
                            testing -> "自动检测中"
                            testResult?.success == false -> "无法连接"
                            testResult?.success == true -> "连接正常"
                            isActive -> "待检测"
                            index == 0 -> "首选地址 · 待检测"
                            else -> "备用地址 · 待检测"
                        }
                        Text(
                            text = if (isActive) "当前地址 · $status" else status,
                            style = MiuixTheme.textStyles.footnote1,
                            color = when {
                                !testing && testResult?.success == false -> colors.error
                                !testing && testResult?.success == true -> colors.primary
                                else -> colors.onSurfaceVariantSummary
                            },
                        )
                    }
                }
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起详情" else "展开详情",
                    )
                }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { handleTop = it.positionInRoot().y }
                        .pointerInput(address.url) {
                            detectDragGestures(
                                onDragStart = { currentOnDragStart(handleTop + it.y) },
                                onDragEnd = { currentOnDragFinish(true) },
                                onDragCancel = { currentOnDragFinish(false) },
                                onDrag = { change, drag ->
                                    change.consume()
                                    currentOnDrag(drag.y)
                                },
                            )
                        }
                        .size(ADDRESS_MARKER_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "拖动排序 ${address.name.ifBlank { "未命名地址" }}",
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
            if (expanded) {
                AddressDetails(address, testing, testResult, onEdit, onRemove)
            }
        }
    
    }
}

@Composable
private fun AddressDetails(
    address: ServerAddress,
    testing: Boolean,
    result: AddressTestResult?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = BadgerSpacing.sm, top = BadgerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = address.url,
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceVariantSummary,
            )
            if (!testing && result?.success == false) {
                Text(
                    text = "检测失败 · ${result.message ?: "无法连接"}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${address.name.ifBlank { "未命名地址" }}")
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "删除 ${address.name.ifBlank { "未命名地址" }}",
                tint = colors.error,
            )
        }
    }
}

@Composable
internal fun AddressEditorDialog(
    editingUrl: String?,
    formName: String,
    formUrl: String,
    formHttps: Boolean,
    formUrlReason: String?,
    canSave: Boolean,
    onName: (String) -> Unit,
    onUrl: (String) -> Unit,
    onHttps: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = if (editingUrl == null) "添加连接地址" else "编辑连接地址",
        summary = "为同一服务器添加内网、公网等地址，按照列表顺序尝试连接。",
        onDismissRequest = onCancel,
    ) {
        Column {
            TextField(
                value = formName,
                onValueChange = onName,
                label = "别名（可选）",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            ServerUrlInput(
                value = formUrl,
                onValueChange = onUrl,
                reason = formUrlReason.takeIf { formUrl.isNotBlank() },
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            HttpsSwitchRow(checked = formHttps, onChange = onHttps)
            Spacer(modifier = Modifier.height(BadgerSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (editingUrl == null) "添加" else "保存")
                }
            }
        }
    }
}
