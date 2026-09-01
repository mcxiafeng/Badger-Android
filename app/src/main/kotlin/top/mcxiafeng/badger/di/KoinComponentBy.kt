package top.mcxiafeng.badger.di

import org.koin.java.KoinJavaComponent

/**
 * Compatibility bridge for UI helpers that have not yet been migrated to constructor injection.
 * ViewModel and new UI code should prefer constructor injection or koinInject().
 */
@Deprecated("Migrate callers away from the service locator", level = DeprecationLevel.WARNING)
object KoinComponentBy {
    inline fun <reified T : Any> get(): T = KoinJavaComponent.get(T::class.java)
}
