package top.mcxiafeng.badger.ocr

/**
 * 从文本中提取出的结构化联系人信息
 *
 * 用于存储从 OCR/AI 识别结果中提取的联系人字段。
 *
 * - name/phone/email/avatarUrl/rawText/otherInfo 是系统级固定属性
 * - platforms 是社交平台字段的动态映射（fieldKey → value），
 *   新增平台只需在 PlatformFields 注册表添加一条记录，
 *   构造时往 platforms 里 put 即可，本类无需任何修改。
 */
data class ExtractedContactInfo(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val rawText: String = "",
    val otherInfo: List<String> = emptyList(),
    val platforms: Map<String, String> = emptyMap()
) {
    /**
     * 将所有非空字段映射为 fieldKey → value
     *
     * 合并系统字段（phone/email）和平台字段（platforms），
     * 所有调用方（保存、合并、重复检测）统一使用此方法。
     */
    fun toFieldValues(): Map<String, String> = buildMap {
        phone?.let { put("phone", it) }
        email?.let { put("email", it) }
        putAll(platforms)
    }
}
