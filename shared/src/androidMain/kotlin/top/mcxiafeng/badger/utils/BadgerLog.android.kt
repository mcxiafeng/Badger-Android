package top.mcxiafeng.badger.utils

import android.util.Log

/**
 * [KMP K06] Android actual：直接透传 android.util.Log，行为零变化。
 */
actual object BadgerLog {
    actual fun d(tag: String, message: String): Unit = Log.d(tag, message).let { Unit }
    actual fun i(tag: String, message: String): Unit = Log.i(tag, message).let { }
    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
