package top.mcxiafeng.badger.di

import org.koin.core.context.GlobalContext

/**
 * Temporary migration shim for legacy call sites.
 *
 * New code must use constructor injection (ViewModel/service) or [org.koin.compose.koinInject]
 * at the Compose boundary. This shim exists only to keep the migration incremental and is
 * deliberately deprecated so new usages are rejected during review.
 */
@Deprecated(
    message = "Use constructor injection or org.koin.compose.koinInject instead.",
    level = DeprecationLevel.WARNING,
)
object KoinComponentBy {
    inline fun <reified T : Any> get(): T = GlobalContext.get().get()
}
