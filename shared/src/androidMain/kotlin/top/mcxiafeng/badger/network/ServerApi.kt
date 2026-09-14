package top.mcxiafeng.badger.network

import top.mcxiafeng.badger.sync.OutboxScheduler
import top.mcxiafeng.badger.sync.OutboxStore

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
