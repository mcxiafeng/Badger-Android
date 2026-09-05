package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.platform.GallerySaver
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.shared.util.nowMs

/**
 * [KMP K13b] 跳转动作的平台中性模型（原 app 侧 PlatformActions.kt 的 Intent 版退役）。
 *
 * - [OpenUrls]：跳转 fallback 链（deepLink → 包名定向 → 浏览器），顺序即尝试顺序；
 * - [WechatQrScan]：保存 QR 到相册 + 唤起微信扫一扫（Android 专属交互，iOS actual 走降级）；
 * - [CopyAndOpen]：复制文本 + 打开（拨号/mailto/应用主页/通用 VIEW）。
 *
 * 执行入口 [executeLaunchAction]（expect/actual）；构建入口 [buildLaunchAction] 纯 common。
 */
sealed class LaunchAction {
    data class OpenUrls(val targets: List<OpenTarget>) : LaunchAction()
    data class WechatQrScan(val qrContent: String) : LaunchAction()
    data class CopyAndOpen(
        val copyText: String,
        val uri: String?,
        val pkg: String?,
        val kind: OpenKind,
    ) : LaunchAction()

    data object None : LaunchAction()
}

/** 单个跳转目标（uri + 可选包名定向）。 */
data class OpenTarget(val uri: String, val pkg: String? = null)

/** CopyAndOpen 的 Intent action 语义枚举。 */
enum class OpenKind { VIEW, DIAL, MAILTO, MAIN_LAUNCHER }

/**
 * 构建跳转动作（语义逐行对齐原 PlatformActions.buildLaunchAction）：
 * qrcodeToScan → 微信扫码；phone → 拨号；email → mailto；
 * 其余 → deepLinkTemplate 优先 + 包名定向 + 浏览器兜底。
 */
fun buildLaunchAction(fieldKey: String, value: String, jumpLink: String = ""): LaunchAction {
    val def = FIELD_DEF_MAP[fieldKey] ?: return LaunchAction.None

    if (def.qrcodeToScan) {
        val content = jumpLink.ifBlank { value }
        return if (isUrlInput(content) || content.startsWith("weixin://")) {
            LaunchAction.WechatQrScan(qrContent = content)
        } else {
            LaunchAction.CopyAndOpen(
                copyText = content,
                uri = null,
                pkg = "com.tencent.mm",
                kind = OpenKind.MAIN_LAUNCHER,
            )
        }
    }

    if (fieldKey == "phone") {
        val phone = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = phone,
            uri = "tel:$phone",
            pkg = null,
            kind = OpenKind.DIAL,
        )
    }

    if (fieldKey == "email") {
        val email = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = email,
            uri = "mailto:$email",
            pkg = null,
            kind = OpenKind.MAILTO,
        )
    }

    val clean = value.trim().removePrefix("@")
    val targets = mutableListOf<OpenTarget>()

    def.deepLinkTemplate?.replace("%s", clean)?.let { uri ->
        targets.add(OpenTarget(uri))
    }

    val webUri = when {
        isUrlInput(jumpLink) -> jumpLink
        isUrlInput(clean) -> clean
        clean.isNotBlank() -> {
            val link = def.linkTemplate?.replace("%s", clean) ?: clean
            link.takeIf(::isUrlInput)
        }
        else -> null
    }

    if (webUri != null && def.packageName != null) {
        targets.add(OpenTarget(webUri, def.packageName))
    }
    if (webUri != null) {
        targets.add(OpenTarget(webUri))
    }

    return if (targets.isNotEmpty()) LaunchAction.OpenUrls(targets) else LaunchAction.None
}

/** 微信扫一扫 QR 落盘尺寸（原 QrUtils.QR_SIZE）。 */
private const val WECHAT_QR_SIZE = 512

/** [KMP K13c] 微信扫一扫前的 QR 落盘（Android actual：生成 → PNG → 相册；供 executeLaunchAction 用）。 */
internal suspend fun saveQrImageForWechatScan(content: String): Boolean {
    val image = QrCodeGenerator.generate(
        content = content,
        sizePx = WECHAT_QR_SIZE,
        foregroundColor = 0xFF000000.toInt(),
        backgroundColor = 0xFFFFFFFF.toInt(),
    ) ?: return false
    return try {
        val bytes = ImageCodec.encodePng(image)
        if (bytes == null) {
            false
        } else {
            GallerySaver.saveImagePng(bytes, "wechat_qr_${nowMs()}.png")
        }
    } finally {
        image.close()
    }
}
