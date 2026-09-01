package org.koin.androidx.compose

import androidx.compose.runtime.Composable

/**
 * Compatibility bridge for the Koin Compose package move.
 * Existing screens can keep their old import during the incremental migration;
 * new code should import org.koin.compose.koinInject directly.
 */
@Deprecated(
    message = "Import org.koin.compose.koinInject directly.",
    level = DeprecationLevel.WARNING,
)
@Composable
inline fun <reified T : Any> koinInject(): T = org.koin.compose.koinInject()
