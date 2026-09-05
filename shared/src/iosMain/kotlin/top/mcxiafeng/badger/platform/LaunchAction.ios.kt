package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.OpenKind
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "LaunchAction.ios"

/**
 * [KMP K13c] iOS actual 骨架：
 * - VIEW/DIAL/MAILTO → UrlOpener（tel:/mailto:/https 由系统接管，打开方式面板是 iOS 惯例）；
 * - MAIN_LAUNCHER（拉起其他 App）iOS 无通用方案 → false（K16 评估 universal link 降级）；
 * - WechatQrScan → 无微信 shortcut 通道，K16 评估「复制内容 + 提示」降级，当前仅记日志。
 */
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
