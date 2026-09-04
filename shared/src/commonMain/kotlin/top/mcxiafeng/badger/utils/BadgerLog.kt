package top.mcxiafeng.badger.utils

/**
 * [KMP K06] 日志底座 expect/actual。
 *
 * Android actual → android.util.Log（保持现状零行为变化）；
 * iOS actual → NSLog / println。
 *
 * 业务代码（含 SafeLog 调用方）不得直接 import 平台 Log，统一走这里。
 */
expect object BadgerLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
