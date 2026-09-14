package top.mcxiafeng.badger.shared.util

import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()
