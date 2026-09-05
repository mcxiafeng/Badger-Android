package top.mcxiafeng.badger.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "PlatformServices"

actual object PlatformClipboard {
    actual fun copy(text: String): Boolean {
        return try {
            UIPasteboard.generalPasteboard.string = text
            true
        } catch (e: Exception) {
            BadgerLog.e(TAG, "copy: 写入剪贴板失败", e)
            false
        }
    }
}

actual object SystemShare {
    actual fun shareText(title: String, text: String): Boolean {
        // 骨架：UIActivityViewController 需要挂到 keyWindow rootViewController，
        // 随 K16（iosApp 工程）窗口宿主一并落
        BadgerLog.w(TAG, "iOS 骨架：shareText 需 UIActivityViewController，实接登记 K16", null)
        return false
    }

    actual fun shareFile(filePath: String, mimeType: String, title: String): Boolean {
        BadgerLog.w(TAG, "iOS 骨架：shareFile 需 UIActivityViewController，实接登记 K16", null)
        return false
    }
}

actual object UrlOpener {
    actual fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: run {
            BadgerLog.w(TAG, "openUrl: 非法 URL=$url", null)
            return false
        }
        return try {
            UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
            true
        } catch (e: Exception) {
            BadgerLog.e(TAG, "openUrl: 打开失败 url=$url", e)
            false
        }
    }
}
