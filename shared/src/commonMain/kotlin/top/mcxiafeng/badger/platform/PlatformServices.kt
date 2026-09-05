package top.mcxiafeng.badger.platform

/**
 * [KMP K12] 系统剪贴板边界（expect）。
 *
 * Android actual：`android.content.ClipboardManager`（context 经 PlatformContextHolder）；
 * iOS actual：`UIPasteboard.generalPasteboard`。
 */
expect object PlatformClipboard {
    /** 复制文本到剪贴板；返回是否成功（失败已记日志，不抛出）。 */
    fun copy(text: String): Boolean
}

/**
 * [KMP K12] 系统分享边界（expect）。
 *
 * Android actual：`Intent.ACTION_SEND` + `createChooser`；文件分享经 FileProvider
 * （authority = `${packageName}.fileprovider`，与现状一致）。
 * iOS actual：UIActivityViewController 骨架（需窗口宿主，实接登记 K16）。
 */
expect object SystemShare {
    /** 分享纯文本（原「分享名片/联系方式」语义）。 */
    fun shareText(title: String, text: String): Boolean

    /**
     * 分享本地文件（绝对路径，调用方负责临时文件生命周期——chooser 异步读取，
     * 分享后不得立即删除，见 CardPage 分享名片夹注释）。
     */
    fun shareFile(filePath: String, mimeType: String, title: String): Boolean
}

/**
 * [KMP K12] 外部浏览器/应用打开边界（expect）。
 *
 * Android actual：`Intent.ACTION_VIEW`（NEW_TASK，无处理方返回 false）；
 * 仅承接「打开链接」这类简单跳转，LaunchActionHandler 的平台定向 Intent 体系
 * （微信 shortcut、深链 fallback 链）属 Android 专属业务，随 UI 层迁移另行处理。
 */
expect object UrlOpener {
    /** 用外部应用打开 URL；返回是否成功（无处理方/失败均记日志返回 false）。 */
    fun openUrl(url: String): Boolean
}
