package top.mcxiafeng.badger.network

import top.mcxiafeng.badger.sync.OutboxScheduler
import top.mcxiafeng.badger.sync.OutboxStore

/**
 * [KMP K16] `/api` 契约的 OkHttp 实现（Android 传输；主体逻辑在 common 的
 * [ServerApiBase]，本类只装配 OkHttp 传输与 WorkManager 调度 kick）。
 *
 * Q2 裁决：OkHttp 无 iOS native 变体，Android 保持原传输路径零变化；
 * iOS 侧用同主体的 Ktor 实现（shared iosMain KtorServerApi）。
 */
class OkHttpServerApi(
    baseUrl: String,
    http: okhttp3.OkHttpClient,
    tokenProvider: () -> String?,
    outboxStore: OutboxStore,
    outboxScheduler: OutboxScheduler,
) : ServerApiBase(
    core = ApiCore(baseUrl, OkHttpApiTransport(http), tokenProvider),
    outboxStore = outboxStore,
    kickScheduler = outboxScheduler::kick,
)
