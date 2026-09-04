package top.mcxiafeng.badger.sync

/**
 * [KMP K09] 同步调度抽象（expect）：Outbox 重放的触发边界。
 *
 * - Android actual：包装现有 `OutboxScheduler`（WorkManager unique work，
 *   NetworkType.CONNECTED + 10s 指数退避 + APPEND_OR_REPLACE 契约——行为零变化）。
 * - iOS actual：BGTaskScheduler 注册骨架 + 前台时机兜底（App 回前台 kick）；
 *   真机语义验证在 K17（Q1 裁决：云 Mac 租用后进行）。
 *
 * **职责边界**：消费循环（OutboxWorker 逐行 replay）与平台调度（何时跑）解耦；
 * 本接口只管「何时跑」，不知道行怎么重放。
 */
expect class SyncDispatcher {
    /** 触发一次重放。平台侧自行去抖（Android=WorkManager unique；iOS=合并窗口）。 */
    fun kick()
}
