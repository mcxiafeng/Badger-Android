package top.mcxiafeng.badger.shared.util

import platform.Foundation.NSUUID

/**
 * [KMP K08] iOS actual：NSUUID（小写化对齐 java.util.UUID.toString）。
 */
actual fun randomUuid(): String = NSUUID().UUIDString.lowercase()
