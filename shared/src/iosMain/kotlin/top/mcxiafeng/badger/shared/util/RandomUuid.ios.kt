package top.mcxiafeng.badger.shared.util

import platform.Foundation.NSUUID

actual fun randomUuid(): String = NSUUID().UUIDString.lowercase()
