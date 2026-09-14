package top.mcxiafeng.badger.ocr

data class ExtractedContactInfo(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val rawText: String = "",
    val otherInfo: List<String> = emptyList(),
    val platforms: Map<String, String> = emptyMap()
) {
    

    fun toFieldValues(): Map<String, String> = buildMap {
        phone?.let { put("phone", it) }
        email?.let { put("email", it) }
        putAll(platforms)
    }
}
