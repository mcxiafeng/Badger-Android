package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.ocr.ExtractedContactInfo

/**
 * QR 码内容解析 UseCase
 *
 * 支持 vCard 格式、邮箱、手机号、普通文本的自动识别。
 * 纯解析逻辑，零外部依赖。
 *
 * [§14.2] Hilt `@Inject constructor` → Koin `factoryOf(::ParseQrCodeUseCase)`。
 */
class ParseQrCodeUseCase() {

    companion object {
        private val EMAIL_REGEX = Regex("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")
        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    }

    operator fun invoke(qrContent: String): ExtractedContactInfo {
        var name: String? = null
        var phone: String? = null
        var email: String? = null

        if (qrContent.contains("BEGIN:VCARD")) {
            qrContent.lines().forEach { line ->
                when {
                    line.startsWith("FN:") -> name = line.removePrefix("FN:")
                    line.startsWith("TEL:") -> phone = line.removePrefix("TEL:")
                    line.startsWith("EMAIL:") -> email = line.removePrefix("EMAIL:")
                    else -> {}
                }
            }
        } else if (EMAIL_REGEX.matches(qrContent)) {
            email = qrContent
        } else if (PHONE_REGEX.matches(qrContent)) {
            phone = qrContent
        } else {
            name = qrContent
        }

        return ExtractedContactInfo(
            name = name,
            phone = phone,
            email = email,
            rawText = qrContent
        )
    }
}
