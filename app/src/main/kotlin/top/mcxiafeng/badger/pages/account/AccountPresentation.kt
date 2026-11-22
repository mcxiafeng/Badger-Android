package top.mcxiafeng.badger.pages.account

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.sync.SyncProgress

internal enum class AccountSyncAction { SYNC, LOGIN, CONFLICTS }
internal enum class AccountSyncTone { NEUTRAL, SUCCESS, ATTENTION }

@Immutable
internal data class AccountSyncPresentation(
    val title: String,
    val detail: String? = null,
    val actionLabel: String,
    val action: AccountSyncAction = AccountSyncAction.SYNC,
    val tone: AccountSyncTone = AccountSyncTone.NEUTRAL,
)

internal fun presentAccountSync(local: Boolean, progress: SyncProgress): AccountSyncPresentation {
    if (local) return AccountSyncPresentation("本地用户", "资料仅保存在本机", "登录与切换账号", AccountSyncAction.LOGIN)
    if (progress.running) return AccountSyncPresentation("正在同步", actionLabel = "同步中…")
    val message = progress.message.trim()
    if (needsAccountLogin(message)) return AccountSyncPresentation(
        "需要重新登录", "请重新登录后继续同步", "重新登录", AccountSyncAction.LOGIN, AccountSyncTone.ATTENTION)
    if (progress.conflicts > 0) return AccountSyncPresentation(
        "${progress.conflicts} 项冲突待处理", "核对本机与云端的不同内容", "处理冲突",
        AccountSyncAction.CONFLICTS, AccountSyncTone.ATTENTION)
    if (message == "已同步" && !progress.pending) return AccountSyncPresentation(
        "已同步", actionLabel = "同步资料", tone = AccountSyncTone.SUCCESS)
    if (progress.pending && message in listOf("已同步", "部分资料待同步"))
        return AccountSyncPresentation("有资料待同步", actionLabel = "同步资料")
    if (message == "同步已暂停") return AccountSyncPresentation("同步已暂停", actionLabel = "继续同步")
    if (message == "尚未同步" && !progress.pending) return AccountSyncPresentation("尚未同步", actionLabel = "同步资料")
    return AccountSyncPresentation("同步未完成", accountDisplayMessage(message), "重试同步", tone = AccountSyncTone.ATTENTION)
}

internal fun needsAccountLogin(message: String): Boolean =
    LOGIN_MESSAGES.any { message.contains(it, ignoreCase = true) }

internal fun accountDisplayMessage(message: String): String = when {
    needsAccountLogin(message) -> "请重新登录后重试"
    "重新建立" in message -> "资料已有变化，请重新预览"
    message.any { it in '一'..'鿿' } -> message
    else -> "暂时无法完成操作，请检查连接后重试"
}

private val LOGIN_MESSAGES = listOf("UNAUTHORIZED", "SESSION_CHANGED", "SESSION_EXPIRED", "重新登录", "登录已过期")
