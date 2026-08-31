package top.mcxiafeng.badger.di

import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.error.KoinApplicationAlreadyStartedException
import org.koin.mp.KoinPlatformTools

/** Static Koin lookup helper used by legacy ViewModel field injection paths. */
object KoinComponentBy {
    inline fun <reified T : Any> get(): T = currentKoin().get<T>()

    val koin: Koin
        get() = currentKoin()

    @PublishedApi
    internal fun currentKoin(): Koin =
        runCatching { GlobalContext.get() }.getOrElse { err ->
            if (err is KoinApplicationAlreadyStartedException || err is IllegalStateException) {
                KoinPlatformTools.defaultContext().get()
            } else {
                throw err
            }
        }
}
