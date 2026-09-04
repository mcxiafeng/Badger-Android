package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08] 毫秒级当前时间（common）。
 * Android/iOS actual 各自实现；替代 common 代码里的 System.currentTimeMillis()。
 */
expect fun nowMs(): Long
