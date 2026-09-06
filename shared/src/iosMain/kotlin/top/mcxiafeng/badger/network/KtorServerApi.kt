package top.mcxiafeng.badger.network

import io.ktor.client.engine.HttpClientEngine
import top.mcxiafeng.badger.sync.OutboxStore

/**
 * [KMP K16] `/api` 契约的 Ktor 实现（iOS 传输；主体逻辑在 common 的 [ServerApiBase]，
 * 本类只装配 Ktor Darwin 传输与 [IosTokenRefresher] 的 401 刷新钩子）。
 *
 * 与 Android OkHttpServerApi 的行为差异面（超时/异常体系）见 [KtorApiTransport] 注释；
 * Outbox 重放调度经 [kickScheduler] 注入（iOS = SyncDispatcher.kick，BGTask + 前台时机）。
 */
class KtorServerApi(
    baseUrl: String,
    tokenHolder: TokenHolder,
    outboxStore: OutboxStore,
    kickScheduler: () -> Unit,
    engine: HttpClientEngine? = null,
) : ServerApiBase(
    core = buildCore(baseUrl, tokenHolder, engine),
    outboxStore = outboxStore,
    kickScheduler = kickScheduler,
) {
    private companion object {
        /** 刷新器与裸 client 在构造期一次性创建（每次 401 新建会泄漏连接）。 */
        fun buildCore(baseUrl: String, tokenHolder: TokenHolder, engine: HttpClientEngine?): ApiCore {
            val refresher = IosTokenRefresher(engine)
            return ApiCore(
                baseUrl = baseUrl,
                transport = KtorApiTransport(engine) { failedToken ->
                    refresher.refresh(failedToken, tokenHolder)
                },
                tokenProvider = tokenHolder::get,
            )
        }
    }
}
