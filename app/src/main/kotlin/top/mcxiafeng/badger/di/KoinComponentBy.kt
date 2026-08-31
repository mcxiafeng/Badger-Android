package top.mcxiafeng.badger.di

import org.koin.core.context.GlobalContext

/**
 * Transitional dependency bridge for legacy UI/ViewModel call sites.
 *
 * New code must use constructor injection (ViewModel) or [org.koin.compose.koinInject]
 * (Composable). This exists only while the remaining legacy callers are migrated.
 */
@Deprecated(
    message = "Use constructor injection or koinInject() instead",
    level = DeprecationLevel.WARNING,
)
object KoinComponentBy {
    inline fun <reified T : Any> get(): T = GlobalContext.get().get()
}
