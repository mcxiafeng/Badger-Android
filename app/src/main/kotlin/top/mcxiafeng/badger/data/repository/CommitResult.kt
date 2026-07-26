package top.mcxiafeng.badger.data.repository

/**
 * [V2-P6] 关键操作(commitDelete / commitMerge)同步通道返回结果。
 *
 * 设计要点:
 * - `SentSuccess` / `SentFailed` 用降级语义,UI 层不感知 Worker / 队列细节;
 * - `NotFound` 让 UI 弹"联系人已不存在"toast,避免 Promises 给 Promise 失败。
 *
 * 单边决断(op 真不可撤销)→ 返 [SentSuccess] 时**已**对服务端生效;
 * `SentFailed` 语义:直发 HTTP 失败,但 Worker 已入队接管,UI 可弹"删除进行中",
 * Worker 兜底完成后由 OperationHistoryPage 通知用户。
 */
sealed class CommitResult {
    /** 服务端 200(或 404 幂等) + 本地物理删除完成 — op 已落袋。 */
    data object SentSuccess : CommitResult()

    /** 直发 HTTP 失败,Worker 接管(in_progress = "删除中")— 30s 后 RevertStuckOpWorker 兜底。 */
    data class SentFailed(val reason: String) : CommitResult()

    /** 联系人本地不存在(已被其他流程删掉) — no-op。 */
    data object NotFound : CommitResult()
}
