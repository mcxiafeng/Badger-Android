package top.mcxiafeng.badger.di

import org.koin.core.Koin
import org.koin.core.error.KoinApplicationAlreadyStartedException
import org.koin.mp.KoinPlatformTools

object KoinComponentBy {

    
    inline fun <reified T : Any> get(): T =
        currentKoin().get<T>()

    
    val koin: Koin
        get() = currentKoin()

    @PublishedApi
    internal fun currentKoin(): Koin {
        return runCatching { KoinPlatformTools.defaultContext().get() }.getOrElse { err ->
            
            
            if (err is KoinApplicationAlreadyStartedException || err is IllegalStateException) {
                KoinPlatformTools.defaultContext().get()
            } else {
                throw err
            }
        }
    }
}