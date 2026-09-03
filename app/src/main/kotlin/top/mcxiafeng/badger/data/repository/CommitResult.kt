package top.mcxiafeng.badger.data.repository

/**
 * 写路径结果（commitDelete / commitMerge / ContactWriter）。
 *
 * - [SentSuccess]：远端直推已完成（DELETE 404 也视为幂等成功）。
 * - [Written]：本地事务已提交（ContactWriter 三入口）；Outbox CREATE/PATCH 可能仍在排队。
 * - [SentFailed]：失败，调用方提示重试。
 * - [NotFound]：本地没有可操作的目标。
 */
sealed class CommitResult {
    /** 服务端成功（DELETE 的 404 也视为幂等成功），且本地收尾完成。 */
    data object SentSuccess : CommitResult()

    /** 本地事务已提交，返回联系人 rowId（扫码保存/合并/附加）。 */
    data class Written(val contactId: Long) : CommitResult()

    /** 直推或本地写入失败，调用方决定是否提示重试。 */
    data class SentFailed(val reason: String) : CommitResult()

    /** 联系人本地不存在，操作已经没有需要处理的目标。 */
    data object NotFound : CommitResult()
}
