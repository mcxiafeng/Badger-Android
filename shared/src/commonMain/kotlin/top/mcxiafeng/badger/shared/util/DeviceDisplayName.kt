package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08-B] 设备显示名（服务端 Device 行展示用）。
 * Android actual = Build.MANUFACTURER + Build.MODEL；iOS actual = 机型标识。
 */
expect fun deviceDisplayName(): String
