package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08-B] 静默删除文件（路径为空/不存在/失败均不抛）。
 */
expect fun deleteFileQuietly(path: String?)
