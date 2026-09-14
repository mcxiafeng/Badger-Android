package top.mcxiafeng.badger.network

import io.ktor.client.engine.HttpClientEngine
import top.mcxiafeng.badger.sync.OutboxStore

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
