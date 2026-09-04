package top.mcxiafeng.badger.utils

import platform.Foundation.NSLog

/**
 * [KMP K06] iOS actual：NSLog（真机走 unified logging，控制台可过滤）。
 */
actual object BadgerLog {
    actual fun d(tag: String, message: String) = NSLog("[D][%@] %@", tag, message)
    actual fun i(tag: String, message: String) = NSLog("[I][%@] %@", tag, message)
    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) NSLog("[W][%@] %@ — %@", tag, message, throwable.message ?: "<no-msg>")
        else NSLog("[W][%@] %@", tag, message)
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) NSLog("[E][%@] %@ — %@", tag, message, throwable.message ?: "<no-msg>")
        else NSLog("[E][%@] %@", tag, message)
    }
}
