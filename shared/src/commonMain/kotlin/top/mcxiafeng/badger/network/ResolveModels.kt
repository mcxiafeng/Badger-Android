package top.mcxiafeng.badger.network

/**
 * [KMP K08-B] resolver 域的纯数据模型（commonMain）。
 * ContactNetworkResolver 实现（Koin 静态 compat + OkHttp）留 app。
 */

data class IdentifyResponse(
    val kind: String,
    val name: String?,
    val avatarUrl: String?,
    val description: String?,
    val contactMap: Map<String, String>,
) {
    val nickname: String? get() = name
    val signature: String? get() = description
    val type: ContactType get() = kindToContactType(kind) ?: ContactType.None
}

data class NetworkResolveResult(
    val nickname: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val contactMap: Map<String, String> = emptyMap(),
    val type: ContactType = ContactType.None,
    val kind: String? = null,
    val name: String? = null,
) {
    val signature: String? get() = description
}
