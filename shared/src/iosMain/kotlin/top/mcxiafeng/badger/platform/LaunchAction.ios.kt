package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.OpenKind
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "LaunchAction.ios"

actual suspend fun executeLaunchAction(action: LaunchAction): Boolean {
    return when (action) {
        is LaunchAction.OpenUrls -> {
            action.targets.firstOrNull()?.let { target ->
                UrlOpener.openUrl(target.uri)
            } ?: false
        }

        is LaunchAction.WechatQrScan -> {
            BadgerLog.w(TAG, "WechatQrScan: iOS 骨架未接线（K16 评估降级路径）")
            false
        }

        is LaunchAction.CopyAndOpen -> {
            PlatformClipboard.copy(action.copyText)
            when (action.kind) {
                OpenKind.MAIN_LAUNCHER -> {
                    BadgerLog.w(TAG, "CopyAndOpen MAIN_LAUNCHER: iOS 无通用拉起方案")
                    false
                }
                else -> action.uri?.let { UrlOpener.openUrl(it) } ?: false
            }
        }

        LaunchAction.None -> false
    }
}
