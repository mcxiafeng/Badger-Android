package top.mcxiafeng.badger.ocr

import android.content.Intent
import androidx.core.net.toUri

/**
 * [KMP K08-B] 平台字段注册表的 Android 平台层：跳转 Intent 构建。
 * 纯数据层（PlatformFieldDef/SYSTEM_FIELDS/PLATFORM_FIELDS/FIELD_DEF_MAP/ALL_FIELDS/
 * buildPlatformLink 等）已迁 shared commonMain。
 */

/** 跳转动作类型 */
sealed class LaunchAction {
    data class Intents(val intents: List<Intent>) : LaunchAction()
    data class WechatQrScan(val qrContent: String) : LaunchAction()
    data class CopyAndOpen(val copyText: String, val intent: Intent) : LaunchAction()
    data object None : LaunchAction()
}

/**
 * 构建跳转动作。
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
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage("com.tencent.mm")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    if (fieldKey == "phone") {
        val phone = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = phone,
            intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    if (fieldKey == "email") {
        val email = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = email,
            intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    val clean = value.trim().removePrefix("@")
    val intents = mutableListOf<Intent>()

    def.deepLinkTemplate?.replace("%s", clean)?.let { uri ->
        intents.add(Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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
        intents.add(Intent(Intent.ACTION_VIEW, webUri.toUri()).apply {
            setPackage(def.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    if (webUri != null) {
        intents.add(Intent(Intent.ACTION_VIEW, webUri.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    return if (intents.isNotEmpty()) LaunchAction.Intents(intents) else LaunchAction.None
}
