package top.mcxiafeng.badger.ocr

import com.google.gson.annotations.SerializedName

/**
 * AI OCR 识别结果
 *
 * Gson 反序列化的数据类，字段名须与 AI 返回的 JSON key 完全一致。
 * 平台字段通过 platforms Map 动态映射，新增平台无需修改本类。
 *
 * @property name 联系人姓名
 * @property phone 手机号码
 * @property email 电子邮箱
 * @property wechat 微信号
 * @property qq QQ 号
 * @property bilibili Bilibili UID 或空间 URL
 * @property weibo 微博用户标识
 * @property douyin 抖音用户标识
 * @property github GitHub 用户名
 * @property telegram Telegram 用户名
 * @property xiaohongshu 小红书用户标识
 * @property facebook Facebook 用户标识
 * @property x X (Twitter) 用户名
 * @property website 个人网站 URL
 * @property other 无法归入以上字段的额外信息列表
 */
data class AiOcrResult(
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("wechat") val wechat: String? = null,
    @SerializedName("qq") val qq: String? = null,
    @SerializedName("bilibili") val bilibili: String? = null,
    @SerializedName("weibo") val weibo: String? = null,
    @SerializedName("douyin") val douyin: String? = null,
    @SerializedName("github") val github: String? = null,
    @SerializedName("telegram") val telegram: String? = null,
    @SerializedName("xiaohongshu") val xiaohongshu: String? = null,
    @SerializedName("facebook") val facebook: String? = null,
    @SerializedName("x") val x: String? = null,
    @SerializedName("website") val website: String? = null,
    @SerializedName("other") val other: List<String> = emptyList()
) {
    /**
     * 转换为 ExtractedContactInfo 格式（兼容现有流程）
     *
     * 通过直接字段映射将非空平台属性收集到 platforms Map，
     * other 仅存放无法归入上述字段的额外信息
     */
    fun toExtractedContactInfo(rawText: String): ExtractedContactInfo {
        val platforms = mutableMapOf<String, String>()
        qq?.takeIf { it.isNotBlank() }?.let { platforms["qq"] = it }
        wechat?.takeIf { it.isNotBlank() }?.let { platforms["wechat"] = it }
        bilibili?.takeIf { it.isNotBlank() }?.let { platforms["bilibili"] = it }
        weibo?.takeIf { it.isNotBlank() }?.let { platforms["weibo"] = it }
        douyin?.takeIf { it.isNotBlank() }?.let { platforms["douyin"] = it }
        github?.takeIf { it.isNotBlank() }?.let { platforms["github"] = it }
        telegram?.takeIf { it.isNotBlank() }?.let { platforms["telegram"] = it }
        xiaohongshu?.takeIf { it.isNotBlank() }?.let { platforms["xiaohongshu"] = it }
        facebook?.takeIf { it.isNotBlank() }?.let { platforms["facebook"] = it }
        x?.takeIf { it.isNotBlank() }?.let { platforms["x"] = it }
        website?.takeIf { it.isNotBlank() }?.let { platforms["website"] = it }
        return ExtractedContactInfo(
            name = name,
            phone = phone,
            email = email,
            platforms = platforms,
            rawText = rawText,
            otherInfo = other
        )
    }
}

/**
 * AI OCR 服务调用结果
 */
sealed class AiOcrServiceResult {
    data class Success(val data: AiOcrResult, val rawText: String) : AiOcrServiceResult()
    data class Error(val message: String) : AiOcrServiceResult()
}

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val supportsVision: Boolean = false
)
