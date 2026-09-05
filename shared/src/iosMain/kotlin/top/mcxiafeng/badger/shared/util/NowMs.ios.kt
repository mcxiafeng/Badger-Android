package top.mcxiafeng.badger.shared.util

// [KMP K13] kotlinx.datetime.Clock 已随 0.7 线废弃（CMP 1.11.1 传递升版）——std 自带 kotlin.time.Clock
import kotlin.time.Clock

/**
 * [KMP K08] iOS actual：kotlin.time.Clock（毫秒）。
 */
actual fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
