package top.mcxiafeng.badger.platform

import android.content.Intent
import androidx.core.net.toUri
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.OpenKind
import top.mcxiafeng.badger.ocr.OpenTarget
import top.mcxiafeng.badger.ocr.saveQrImageForWechatScan
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog
import top.mcxiafeng.badger.utils.isShortLink
import top.mcxiafeng.badger.utils.resolveRedirect

private const val TAG = "LaunchAction.android"
private const val WECHAT_PACKAGE = "com.tencent.mm"
private const val WECHAT_SCAN_ACTION = "com.tencent.mm.action.BIZSHORTCUT"

private fun startActivity(uri: String, pkg: String?, action: String = Intent.ACTION_VIEW): Boolean {
    val activity = ActivityHost.activity ?: run {
        BadgerLog.w(TAG, "startActivity: ActivityHost 未挂载")
        return false
    }
    return try {
        val intent = Intent(action, uri.toUri()).apply {
            pkg?.let { setPackage(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        true
    } catch (e: Exception) {
        BadgerLog.w(TAG, "startActivity 失败: uri=${SafeLog.url(uri)} pkg=$pkg (${e.message})")
        false
    }
}

private fun startMainLauncher(pkg: String): Boolean {
    val activity = ActivityHost.activity ?: return false
    return try {
        activity.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (e: Exception) {
        BadgerLog.w(TAG, "startMainLauncher 失败: $pkg", e)
        false
    }
}

private fun startWechatScanShortcut(): Boolean {
    val activity = ActivityHost.activity ?: return false
    return try {
        activity.startActivity(Intent().apply {
            setPackage(WECHAT_PACKAGE)
            action = WECHAT_SCAN_ACTION
            putExtra("LauncherUI.From.Scaner.Shortcut", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (e: Exception) {
        BadgerLog.w(TAG, "微信扫一扫 shortcut 启动失败，尝试主界面兜底", e)
        false
    }
}

/** [KMP K13b] Android actual：Intent fallback 链（语义对齐原 LaunchActionHandler.handleIntents）。 */
actual suspend fun executeLaunchAction(action: LaunchAction): Boolean {
    return when (action) {
        is LaunchAction.OpenUrls -> {
            val first = action.targets.firstOrNull()
            // 短链先解析，解析成功则重建「包名定向 + 裸链接」两级链
            val targets: List<OpenTarget> = if (first != null && isShortLink(first.uri)) {
                val resolved = resolveRedirect(first.uri)
                if (resolved != null && resolved != first.uri) {
                    BadgerLog.d(TAG, "短链解析: ${SafeLog.url(first.uri)} -> ${SafeLog.url(resolved)}")
                    listOfNotNull(
                        first.pkg?.let { OpenTarget(resolved, it) },
                        OpenTarget(resolved),
                    )
                } else {
                    action.targets
                }
            } else {
                action.targets
            }
            targets.forEachIndexed { idx, target ->
                if (startActivity(target.uri, target.pkg)) {
                    BadgerLog.d(TAG, "跳转[$idx]成功")
                    return true
                }
            }
            false
        }

        is LaunchAction.WechatQrScan -> {
            val saved = saveQrImageForWechatScan(action.qrContent)
            if (!saved) return false
            // 先试微信扫一扫 shortcut，失败降级微信主界面
            if (startWechatScanShortcut()) return true
            startMainLauncher(WECHAT_PACKAGE)
        }

        is LaunchAction.CopyAndOpen -> {
            PlatformClipboard.copy(action.copyText)
            when (action.kind) {
                OpenKind.MAIN_LAUNCHER -> action.pkg?.let { startMainLauncher(it) } ?: false
                else -> action.uri?.let { startActivity(it, action.pkg) } ?: false
            }
        }

        LaunchAction.None -> false
    }
}
