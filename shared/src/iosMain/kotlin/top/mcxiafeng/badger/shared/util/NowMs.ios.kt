package top.mcxiafeng.badger.shared.util

import kotlin.time.Clock

actual fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
