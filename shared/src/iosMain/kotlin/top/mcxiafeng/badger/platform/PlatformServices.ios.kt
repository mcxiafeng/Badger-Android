package top.mcxiafeng.badger.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import platform.UIKit.UIWindow
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
    /**
     * 获取当前活跃的 rootViewController 用于 present UIActivityViewController。
     * iOS 13+ keyWindow 已废弃，走 connectedScenes → keyWindow 兜底。
     */
    private fun rootViewController(): platform.UIKit.UIViewController? {
        val app = UIApplication.sharedApplication
        // keyWindow 兜底（iOS 13 前主路径；iOS 13+ 多场景下可能为 null）
        val keyWindow = app.keyWindow
        if (keyWindow != null) {
            return keyWindow.rootViewController
        }
        // iOS 13+ connectedScences 兜底
        val window = app.windows.firstOrNull() as? UIWindow
        return window?.rootViewController
    }

    actual fun shareText(title: String, text: String): Boolean {
        return try {
            val rootVC = rootViewController()
            if (rootVC == null) {
                BadgerLog.w(TAG, "shareText: 无可用 rootViewController", null)
                return false
            }
            val activityItems = listOf<Any?>(text)
            val activityVC = UIActivityViewController(activityItems = activityItems, applicationActivities = null)
            rootVC.presentViewController(activityVC, animated = true, completion = null)
            BadgerLog.d(TAG, "shareText: 已弹出分享面板")
            true
        } catch (e: Exception) {
            BadgerLog.e(TAG, "shareText 失败", e)
            false
        }
    }

    actual fun shareFile(filePath: String, mimeType: String, title: String): Boolean {
        return try {
            val rootVC = rootViewController()
            if (rootVC == null) {
                BadgerLog.w(TAG, "shareFile: 无可用 rootViewController", null)
                return false
            }
            val fileUrl = NSURL.fileURLWithPath(filePath)
            val activityItems = listOf<Any?>(fileUrl)
            val activityVC = UIActivityViewController(activityItems = activityItems, applicationActivities = null)
            rootVC.presentViewController(activityVC, animated = true, completion = null)
            BadgerLog.d(TAG, "shareFile: 已弹出分享面板 path=$filePath")
            true
        } catch (e: Exception) {
            BadgerLog.e(TAG, "shareFile 失败 path=$filePath", e)
            false
        }
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
