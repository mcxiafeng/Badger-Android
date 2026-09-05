package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.ocr.LaunchAction

/**
 * [KMP K13b] 执行跳转动作。返回是否成功（OpenUrls 的 fallback 链全部失败/无 Activity 时为 false）。
 *
 * Android actual：Intent 链 + 短链解析（HttpURLConnection）；
 * iOS actual：UrlOpener（tel:/mailto:/https 全部系统接管），微信扫码走降级提示。
 *
 * 注意：expect 与 actual 必须同包（Kotlin 规则）——类型与构建器在 `ocr/LaunchAction.kt`，
 * 执行边界在 platform 包。
 */
expect suspend fun executeLaunchAction(action: LaunchAction): Boolean
