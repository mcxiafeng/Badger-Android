package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.platform.GallerySaver
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.shared.util.nowMs

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

data class OpenTarget(val uri: String, val pkg: String? = null)

enum class OpenKind { VIEW, DIAL, MAILTO, MAIN_LAUNCHER }

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

private const val WECHAT_QR_SIZE = 512

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
