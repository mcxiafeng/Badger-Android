package top.mcxiafeng.badger.data.repository

/**
 * 关键直推操作（commitDelete / commitMerge）的结果。
 *
 * Repository 当前采用同步直推：本地先做必要的乐观状态修改，再直接调用服务端。
 * 失败时不会假装存在 PendingUpload/Worker 接管；调用方应根据 [SentFailed] 决定
 * 是否提示用户重试或由后续编辑再次触发同步。
 */
sealed class CommitResult {
    /** 服务端成功（DELETE 的 404 也视为幂等成功），且本地收尾完成。 */
    data object SentSuccess : CommitResult()

    /** 直推失败，本地数据保持可恢复状态，未进入后台队列。 */
    data class SentFailed(val reason: String) : CommitResult()

    /** 联系人本地不存在，操作已经没有需要处理的目标。 */
    data object NotFound : CommitResult()
}
