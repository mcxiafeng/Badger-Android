package top.mcxiafeng.badger.pages.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.NetworkResolveResult
import top.mcxiafeng.badger.ocr.ExtractedContactInfo

class ParseLocalContentTest {

    

    @Test
    fun pureQQNumber_5digits() {
        val result = parseLocalContent("12345")
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).containsEntry("qq", "12345")
        assertThat(result.name).isNull()
        assertThat(result.phone).isNull()
    }

    @Test
    fun pureQQNumber_10digits() {
        val result = parseLocalContent("1234567890")
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).containsEntry("qq", "1234567890")
    }

    @Test
    fun pureQQNumber_tooShort_4digits_returnsNull() {
        val result = parseLocalContent("1234")
        assertThat(result).isNull()
    }

    @Test
    fun pureQQNumber_tooLong_16digits_returnsNull() {
        val result = parseLocalContent("1234567890123456")
        assertThat(result).isNull()
    }

    @Test
    fun phoneNumber_valid() {
        val result = parseLocalContent("13800138000")
        assertThat(result).isNotNull()
        assertThat(result!!.phone).isEqualTo("13800138000")
        assertThat(result.platforms).isEmpty()
    }

    @Test
    fun phoneNumber_invalidPrefix_matchedAsQQ() {
        
        val result = parseLocalContent("10800138000")
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).containsEntry("qq", "10800138000")
    }

    @Test
    fun emailAddress() {
        val result = parseLocalContent("test@example.com")
        assertThat(result).isNotNull()
        assertThat(result!!.email).isEqualTo("test@example.com")
    }

    @Test
    fun vCard_full() {
        val vcard = """
            BEGIN:VCARD
            FN:张三
            TEL:13800138000
            EMAIL:zhangsan@example.com
            END:VCARD
        """.trimIndent()
        val result = parseLocalContent(vcard)
        assertThat(result).isNotNull()
        assertThat(result!!.name).isEqualTo("张三")
        assertThat(result.phone).isEqualTo("13800138000")
        assertThat(result.email).isEqualTo("zhangsan@example.com")
    }

    @Test
    fun vCard_nameOnly() {
        val vcard = "BEGIN:VCARD\nFN:李四\nEND:VCARD"
        val result = parseLocalContent(vcard)
        assertThat(result).isNotNull()
        assertThat(result!!.name).isEqualTo("李四")
        assertThat(result.phone).isNull()
        assertThat(result.email).isNull()
    }

    @Test
    fun qqUrl_withQqNumber() {
        val result = parseLocalContent("https://qm.qq.com/q/1234567890")
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).containsEntry("qq", "1234567890")
    }

    @Test
    fun qqUrl_qzone() {
        val result = parseLocalContent("https://qzone.qq.com/123456789")
        assertThat(result).isNotNull()
        assertThat(result!!.platforms).containsEntry("qq", "123456789")
    }

    @Test
    fun wechatUrl_cannotParseLocally() {
        val result = parseLocalContent("https://u.wechat.com/abc123")
        assertThat(result).isNull()
    }

    @Test
    fun randomUrl_cannotParseLocally() {
        val result = parseLocalContent("https://example.com/page")
        assertThat(result).isNull()
    }

    @Test
    fun plainText_cannotParse() {
        val result = parseLocalContent("Hello World")
        assertThat(result).isNull()
    }

    @Test
    fun bilibiliUrl_cannotParseLocally() {
        val result = parseLocalContent("https://space.bilibili.com/12345")
        assertThat(result).isNull()
    }

    @Test
    fun telegramUrl_cannotParseLocally() {
        val result = parseLocalContent("https://t.me/testuser")
        assertThat(result).isNull()
    }

    

    private val infoPriority = listOf(
        ContactType.QQ, ContactType.Bilibili, ContactType.WeChat, ContactType.Douyin,
        ContactType.Weibo, ContactType.GitHub, ContactType.Telegram, ContactType.Xiaohongshu,
        ContactType.X, ContactType.Facebook, ContactType.TelegramGroup, ContactType.QQGroup, ContactType.Website
    )
    private val fieldOrder = mapOf(
        "qq" to 0, "bilibili" to 1, "wechat" to 2, "phone" to 3, "email" to 4,
        "douyin" to 5, "weibo" to 6, "github" to 7, "telegram" to 8,
        "xiaohongshu" to 9, "x" to 10, "facebook" to 11, "telegramGroup" to 12, "qqGroup" to 13, "website" to 14
    )

    
    @Test
    fun mergeM1_pureQQ_noOcr() {
        val resolveStates = mapOf(
            "12345678" to QrResolveState(
                qrContent = "12345678",
                extractedInfo = ExtractedContactInfo(platforms = mapOf("qq" to "12345678")),
                loadFailed = true
            )
        )
        val name = computeMergedName(resolveStates, emptyMap(), null, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), null, fieldOrder)

        assertThat(name).isEqualTo("未知联系人")
        assertThat(fields).hasSize(1)
        assertThat(fields[0].key).isEqualTo("qq")
        assertThat(fields[0].value).isEqualTo("12345678")
    }

    
    @Test
    fun mergeM2_pureQQ_withOcrName() {
        val resolveStates = mapOf(
            "12345678" to QrResolveState(
                qrContent = "12345678",
                extractedInfo = ExtractedContactInfo(platforms = mapOf("qq" to "12345678")),
                loadFailed = true
            )
        )
        val ocrInfo = ExtractedContactInfo(name = "张三", phone = "13800138000")
        val name = computeMergedName(resolveStates, emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), ocrInfo, fieldOrder)

        assertThat(name).isEqualTo("张三")
        assertThat(fields.map { it.key }).containsExactly("qq", "phone")
    }

    
    @Test
    fun mergeM3_wechatUrl_withOcrName() {
        val resolveStates = mapOf(
            "https://u.wechat.com/abc" to QrResolveState(
                qrContent = "https://u.wechat.com/abc",
                networkResult = NetworkResolveResult(
                    nickname = "微信用户A",
                    description = "",
                    avatarUrl = "",
                    contactMap = mutableMapOf("wechat" to "wxid_abc"),
                    type = ContactType.WeChat
                ),
                isLoading = false
            )
        )
        val ocrInfo = ExtractedContactInfo(name = "张三", phone = "13800138000")
        val name = computeMergedName(resolveStates, emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), ocrInfo, fieldOrder)

        
        assertThat(name).isEqualTo("微信用户A")
        assertThat(fields.map { it.key }).containsExactly("wechat", "phone")
    }

    
    @Test
    fun mergeM4_noQr_ocrWithInfo() {
        val ocrInfo = ExtractedContactInfo(name = "李四", phone = "13900139000", email = "lisi@test.com")
        val name = computeMergedName(emptyMap(), emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(emptyMap(), emptyMap(), ocrInfo, fieldOrder)

        assertThat(name).isEqualTo("李四")
        assertThat(fields.map { it.key }).containsExactly("phone", "email")
    }

    
    @Test
    fun mergeM5_noQr_ocrNoNameButHasPhone() {
        val ocrInfo = ExtractedContactInfo(phone = "13900139000")
        val name = computeMergedName(emptyMap(), emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(emptyMap(), emptyMap(), ocrInfo, fieldOrder)

        assertThat(name).isEqualTo("未知联系人")
        assertThat(fields).hasSize(1)
        assertThat(fields[0].key).isEqualTo("phone")
    }

    
    @Test
    fun mergeM6_noQr_ocrEmpty() {
        val ocrInfo = ExtractedContactInfo()
        val name = computeMergedName(emptyMap(), emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(emptyMap(), emptyMap(), ocrInfo, fieldOrder)

        assertThat(name).isEqualTo("未知联系人")
        assertThat(fields).isEmpty()
    }

    
    @Test
    fun mergeM7_vCardQr() {
        val resolveStates = mapOf(
            "vcard" to QrResolveState(
                qrContent = "vcard",
                extractedInfo = ExtractedContactInfo(
                    name = "王五",
                    phone = "13700137000",
                    email = "wangwu@test.com"
                ),
                loadFailed = true
            )
        )
        val name = computeMergedName(resolveStates, emptyMap(), null, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), null, fieldOrder)

        assertThat(name).isEqualTo("王五")
        assertThat(fields.map { it.key }).containsExactly("phone", "email")
    }

    
    @Test
    fun mergeM8_multipleQr_withOcr() {
        val resolveStates = mapOf(
            "12345678" to QrResolveState(
                qrContent = "12345678",
                extractedInfo = ExtractedContactInfo(platforms = mapOf("qq" to "12345678")),
                loadFailed = true
            ),
            "https://space.bilibili.com/99999" to QrResolveState(
                qrContent = "https://space.bilibili.com/99999",
                networkResult = NetworkResolveResult(
                    nickname = "B站用户B",
                    description = "",
                    avatarUrl = "",
                    contactMap = mutableMapOf("bilibili" to "99999"),
                    type = ContactType.Bilibili
                ),
                isLoading = false
            )
        )
        val ocrInfo = ExtractedContactInfo(name = "张三", phone = "13800138000")
        val name = computeMergedName(resolveStates, emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), ocrInfo, fieldOrder)

        
        assertThat(name).isEqualTo("B站用户B")
        assertThat(fields.map { it.key }).containsExactly("qq", "bilibili", "phone")
    }

    
    @Test
    fun mergeM9_wechatUrl_networkFailed_noOcr() {
        val resolveStates = mapOf(
            "https://u.wechat.com/abc" to QrResolveState(
                qrContent = "https://u.wechat.com/abc",
                extractedInfo = ExtractedContactInfo(
                    rawText = "https://u.wechat.com/abc",
                    platforms = mapOf("website" to "https://u.wechat.com/abc"),
                    otherInfo = listOf("https://u.wechat.com/abc")
                ),
                loadFailed = true
            )
        )
        val name = computeMergedName(resolveStates, emptyMap(), null, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), null, fieldOrder)

        assertThat(name).isEqualTo("未知联系人")
        assertThat(fields).hasSize(1)
        assertThat(fields[0].key).isEqualTo("website")
    }

    
    @Test
    fun mergeM10_ocrOtherInfo_mappableToPlatform() {
        val ocrInfo = ExtractedContactInfo(
            name = "赵六",
            otherInfo = listOf("微信：wxid_abc", "QQ：12345678")
        )
        val name = computeMergedName(emptyMap(), emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(emptyMap(), emptyMap(), ocrInfo, fieldOrder)

        assertThat(name).isEqualTo("赵六")
        assertThat(fields.map { it.key }).containsExactly("qq", "wechat")
    }

    
    @Test
    fun mergeM11_qqUrl_networkSuccess_withOcrName() {
        val resolveStates = mapOf(
            "https://qm.qq.com/q/abc" to QrResolveState(
                qrContent = "https://qm.qq.com/q/abc",
                networkResult = NetworkResolveResult(
                    nickname = "QQ用户A",
                    description = "",
                    avatarUrl = "https://qlogo.cn/xxx",
                    contactMap = mutableMapOf("qq" to "12345678"),
                    type = ContactType.QQ
                ),
                isLoading = false
            )
        )
        val ocrInfo = ExtractedContactInfo(name = "张三", phone = "13800138000")
        val name = computeMergedName(resolveStates, emptyMap(), ocrInfo, infoPriority)
        val fields = computeMergedFields(resolveStates, emptyMap(), ocrInfo, fieldOrder)

        
        assertThat(name).isEqualTo("QQ用户A")
        assertThat(fields.map { it.key }).containsExactly("qq", "phone")
    }

    
    @Test
    fun mergeM12_pureQQ_noOcr_secondaryResolveSuccess() {
        val resolveStates = mapOf(
            "12345678" to QrResolveState(
                qrContent = "12345678",
                extractedInfo = ExtractedContactInfo(platforms = mapOf("qq" to "12345678")),
                loadFailed = true
            )
        )
        
        val ocrResolveStates = mapOf(
            "qr_local:12345678:qq" to QrResolveState(
                qrContent = "qr_local:12345678:qq",
                networkResult = NetworkResolveResult(
                    nickname = "QQ用户B",
                    description = "",
                    avatarUrl = "https://qlogo.cn/yyy",
                    contactMap = mutableMapOf("qq" to "12345678"),
                    type = ContactType.QQ
                ),
                isLoading = false
            )
        )
        val name = computeMergedName(resolveStates, ocrResolveStates, null, infoPriority)
        val fields = computeMergedFields(resolveStates, ocrResolveStates, null, fieldOrder)

        assertThat(name).isEqualTo("QQ用户B")
        assertThat(fields).hasSize(1)
        assertThat(fields[0].key).isEqualTo("qq")
    }

    
    @Test
    fun mergeM13_pureQQ_noOcr_secondaryResolveFailed() {
        val resolveStates = mapOf(
            "12345678" to QrResolveState(
                qrContent = "12345678",
                extractedInfo = ExtractedContactInfo(platforms = mapOf("qq" to "12345678")),
                loadFailed = true
            )
        )
        val ocrResolveStates = mapOf(
            "qr_local:12345678:qq" to QrResolveState(
                qrContent = "qr_local:12345678:qq",
                loadFailed = true
            )
        )
        val name = computeMergedName(resolveStates, ocrResolveStates, null, infoPriority)
        val fields = computeMergedFields(resolveStates, ocrResolveStates, null, fieldOrder)

        
        assertThat(name).isEqualTo("未知联系人")
        assertThat(fields).hasSize(1)
        assertThat(fields[0].key).isEqualTo("qq")
    }
}
