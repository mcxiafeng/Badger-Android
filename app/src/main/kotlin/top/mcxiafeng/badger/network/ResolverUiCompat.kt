package top.mcxiafeng.badger.network

/**
 * 临时的 UI 兼容模型：仅服务尚未迁移到 [IdentifyResponse] 的页面层代码。
 * 不对应任何服务端 V1 API，也不执行旧 endpoint 请求。
 *
 * 迁移完成后应删除本文件及所有调用方对 NetworkResolveResult/getResultInfo 的引用。
 */
data class NetworkResolveResult(
    val nickname: String?,
    val description: String?,
    val avatarUrl: String?,
    val contactMap: Map<String, String>,
    val type: ContactType,
)

/** 旧页面字段名兼容；canonical resolver 字段为 [IdentifyResponse.description]。 */
val IdentifyResponse.signature: String?
    get() = description

/**
 * 页面迁移期间的适配扩展：底层仍只调用 canonical /api/resolve/。
 */
fun ContactNetworkResolver.getResultInfo(
    content: String,
    headers: Map<String, String> = emptyMap(),
    type: ContactType? = null,
): NetworkResolveResult? {
    if (headers.isNotEmpty()) {
        // 服务端 resolver 的 canonical client 不接受页面自带 headers；保留参数仅用于兼容旧调用签名。
    }
    val response = identify(content) ?: return null
    val resolvedType = type ?: kindToContactType(response.kind) ?: ContactType.None
    return NetworkResolveResult(
        nickname = response.name,
        description = response.description,
        avatarUrl = response.avatarUrl,
        contactMap = response.contactMap,
        type = resolvedType,
    )
}
