package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08] 随机 UUID v4（common）。
 * 替代 common 代码里的 java.util.UUID.randomUUID()。
 */
expect fun randomUuid(): String
