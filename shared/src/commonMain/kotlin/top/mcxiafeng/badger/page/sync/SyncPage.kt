package top.mcxiafeng.badger.page.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Backpack
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MoveLeft
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import top.mcxiafeng.badger.data.repository.PendingAtomSummary
import top.mcxiafeng.badger.data.repository.SyncStatusSnapshot
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.formatEpochDateTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class SyncPage {

    companion object {

//        @Composable
//        fun PageSync() {
//            val viewModel: SyncViewModel = viewModel { SyncViewModel() }
//            val state by viewModel.uiState.collectAsStateWithLifecycle()
//            when (val current = state) {
//                SyncUiState.Loading -> LoadingPane()
//                is SyncUiState.Error -> ErrorPane(current.message) {
//                    viewModel.onEvent(SyncEvent.Refresh)
//                }
//                is SyncUiState.Success -> SyncSuccessPane(current) {
//                    viewModel.onEvent(it)
//                }
//            }
//        }

        @Composable
        fun PageSync(
            onBackClick: () -> Unit,
        ) {
            val viewModel: SyncViewModel = viewModel { SyncViewModel() }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            Column (
                Modifier.fillMaxWidth().fillMaxHeight().background(color = MiuixTheme.colorScheme.surface)
            ){
                SmallTopAppBar(
                    title = "同步" ,
                    navigationIcon = {
                        IconButton(onClick = { onBackClick }) {
                            Icon(Lucide.MoveLeft, contentDescription = "返回")
                        }
                    }
                )
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(80.dp).background(MiuixTheme.colorScheme.surfaceContainer,shape = RoundedCornerShape(16.dp))
                ){

                }
            }



        }

    }
}

@Composable
private fun SyncSuccessPane(state: SyncUiState.Success, onEvent: (SyncEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item  { SyncStatusCard(state.snapshot) }
        if (state.snapshot.recentAtoms.isNotEmpty()) {
            item(key = "pending_card") { PendingAtomsCard(state.snapshot) }
        }
        item {
            SyncActionCard(
                syncing = state.syncing,
                message = state.message,
                onSyncNow = { onEvent(SyncEvent.SyncNow) },
            )
        }
    }
}

@Composable
private fun SyncStatusCard(snapshot: SyncStatusSnapshot) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (snapshot.hasAttention) Lucide.TriangleAlert else Lucide.CircleCheck,
                contentDescription = null,
                tint = if (snapshot.hasAttention) cs.error else cs.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    snapshot.hasAttention -> "有 ${snapshot.pendingTotal} 条修改待同步"
                    snapshot.loggedIn -> "同步正常，本地与云端一致"
                    else -> "未登录，同步暂不可用"
                },
                style = MiuixTheme.textStyles.subtitle,
                color = if (snapshot.hasAttention) cs.error else cs.primary,
            )
        }
        if (snapshot.failedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "其中 ${snapshot.failedCount} 条重试失败，最新错误：${snapshot.lastError ?: "未知"}",
                style = MiuixTheme.textStyles.footnote1,
                color = cs.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (snapshot.loggedIn) {
            StatusDetailRow(
                label = "同步游标",
                value = if (snapshot.cursor > 0L) "v${snapshot.cursor}" else "尚未同步（首次拉取为全量）",
            )
            StatusDetailRow(
                label = "上次同步",
                value = if (snapshot.lastSyncTime > 0L) formatEpochDateTime(snapshot.lastSyncTime) else "尚未同步",
            )
        } else {
            StatusDetailRow("登录状态", "未登录，登录后自动同步")
        }
        StatusDetailRow("待同步总数", "${snapshot.pendingTotal} 条")
        snapshot.oldestPendingAt?.let {
            StatusDetailRow("最早待同步于", formatEpochDateTime(it))
        }
        StatusDetailRow(
            label = "本地数据",
            value = "联系人 ${snapshot.localPersons} · 名片夹 ${snapshot.localCollections} · 标签 ${snapshot.localTags}",
        )
    }
}

@Composable
private fun StatusDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun PendingAtomsCard(snapshot: SyncStatusSnapshot) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text(text = "待同步明细", style = MiuixTheme.textStyles.subtitle)
        snapshot.recentAtoms.forEach { atom ->
            PendingAtomRow(atom)
        }
        val hidden = snapshot.pendingTotal - snapshot.recentAtoms.size
        if (hidden > 0) {
            Text(
                text = "还有 $hidden 条更早的待同步记录",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun PendingAtomRow(atom: PendingAtomSummary) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${kindLabel(atom.kind)} · ${syncTypeLabel(atom.syncType)}",
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = if (atom.attempts > 0) "已重试 ${atom.attempts} 次" else "等待推送",
                style = MiuixTheme.textStyles.footnote1,
                color = if (atom.attempts > 0) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        atom.lastError?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SyncActionCard(syncing: Boolean, message: String?, onSyncNow: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.RefreshCw,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "手动同步", style = MiuixTheme.textStyles.subtitle)
            }
            Text(
                text = "推送本地待同步修改到服务端，再按同步游标增量拉取云端变更",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = onSyncNow,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (syncing) "同步中…" else "立即同步")
            }
            message?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "加载失败：$message",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
            TextButton(text = "重试", onClick = onRetry)
        }
    }
}

private fun kindLabel(kind: EntityKind): String = when (kind) {
    EntityKind.PERSON -> "联系人"
    EntityKind.PROFILE -> "我的资料"
    EntityKind.COLLECTION -> "名片夹"
    EntityKind.TAG -> "标签"
}

private fun syncTypeLabel(type: SyncType): String = when (type) {
    SyncType.INSERT -> "新增"
    SyncType.UPDATE -> "修改"
    SyncType.DELETE -> "删除"
}
