package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncEngineHolder
import top.mcxiafeng.badger.sync.SyncGatewayException
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "SyncConflictHostTester"

/**
 * 全局同步冲突裁决弹窗：collect [SyncEngine.conflicts]，有冲突即弹（复用 [Alert]）。
 * 生命周期 = 用户裁决的"下次再提示"：点外部/返回键只闭掉本轮弹窗（atom 不销账、
 * 冲突态保持），下一轮同步（[SyncEngine.rounds] 前进）自动重新弹出；
 * 确认 = 保留我的（重推本地值），取消 = 用服务端的（覆盖本地）。
 */
@Composable
fun SyncConflictHost(engine: SyncEngine = SyncEngineHolder.get()) {
    val conflicts by engine.conflicts.collectAsState()
    val rounds by engine.rounds.collectAsState()
    // 点外部后闭一轮；rounds 前进即清空 → 同一条冲突下一轮重新弹
    var dismissedAtomId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(rounds) { dismissedAtomId = null }

    val current = conflicts.firstOrNull { it.atomId != dismissedAtomId }
    val scope = rememberCoroutineScope()

    if (current != null) {
        Alert(
            show = true,
            title = "同步冲突：${current.displayName}",
            summary = "这个${kindLabel(current.entityKind)}在服务端也被修改过。" +
                "要保留你的修改，还是采用服务端的版本？",
            confirmText = "保留我的",
            cancelText = "用服务端的",
            onConfirm = {
                scope.launch { resolve(engine, current.atomId, keepLocal = true) }
            },
            onCancel = {
                scope.launch { resolve(engine, current.atomId, keepLocal = false) }
            },
            onDismiss = {
                // 点外部/返回键不替用户做决定：atom 不销账、冲突态保持；
                // 本轮闭窗，下一轮同步（rounds 前进）重新弹
                BadgerLog.d(TAG, "冲突弹窗被外部关闭，下轮同步重新提示 (atom#${current.atomId})")
                dismissedAtomId = current.atomId
            },
        )
    }
}

private suspend fun resolve(engine: SyncEngine, atomId: Long, keepLocal: Boolean) {
    try {
        engine.resolveConflict(atomId, keepLocal)
    } catch (e: SyncGatewayException) {
        // 预期失败（离线点裁决）：冲突保持原样，弹窗下轮同步原样再弹
        BadgerLog.w(TAG, "冲突裁决推送失败 (atom#$atomId keepLocal=$keepLocal)：${e.message}")
    }
}

private fun kindLabel(kind: EntityKind): String = when (kind) {
    EntityKind.PERSON -> "联系人"
    EntityKind.PROFILE -> "名片"
    EntityKind.COLLECTION -> "名片夹"
    EntityKind.TAG -> "标签"
}
