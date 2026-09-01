package top.mcxiafeng.badger.di

import org.koin.java.KoinJavaComponent

/**
 * Temporary migration bridge for legacy constructor initialization.
 *
 * New UI/ViewModel code must use constructor injection or koinInject().
 */
@Deprecated("Migrate callers away from the service locator", level = DeprecationLevel.WARNING)
object KoinComponentBy {
    inline fun <reified T : Any> get(): T = KoinJavaComponent.get(T::class.java)
}
