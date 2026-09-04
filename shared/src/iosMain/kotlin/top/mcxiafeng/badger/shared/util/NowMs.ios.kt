package top.mcxiafeng.badger.shared.util

import kotlinx.datetime.Clock

/**
 * [KMP K08] iOS actual：kotlinx.datetime Clock（毫秒）。
 */
actual fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
