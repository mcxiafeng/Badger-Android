package top.mcxiafeng.badger.shared.util

import java.util.UUID

/**
 * [KMP K08] Android actual：java.util.UUID。
 */
actual fun randomUuid(): String = UUID.randomUUID().toString()
