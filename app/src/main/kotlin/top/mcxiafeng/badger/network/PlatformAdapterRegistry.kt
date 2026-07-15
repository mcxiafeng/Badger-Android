package top.mcxiafeng.badger.network

/**
 * Static platform → brand-colour lookup. Previously lived in
 * `network.adapter.PlatformAdapterRegistry` together with adapter logic;
 * the adapter code itself was moved server-side but the colour palette
 * stays here because it's pure UI metadata.
 *
 * Also exposes a thin shim ([getAdapter]) so the existing UI code can keep
 * the `PlatformAdapterRegistry.getAdapter(ContactType)?.canSync` call
 * shape — the shim is "syntactically present iff the platform has a
 * server-side resolver", which is exactly the same predicate the old
 * client-side adapter registry used.
 */
enum class ContactType {
    QQ, QQGroup, Bilibili, WeChat, TikTok, Weibo, GitHub,
    Telegram, TelegramGroup, Xiaohongshu, Facebook, X, Website, None,
}

/** Mirror of the server-side `/v1/resolver/...` response (subset). */
data class PlatformResolveResult(
    val name: String?,
    val avatarUrl: String?,
    val description: String?,
    val contactMap: Map<String, String>,
)

/**
 * Synchronous-shaped shim. Mirrors the old `PlatformAdapter` API so the
 * 8+ UI call-sites stay compile-clean. The actual network resolution is
 * delegated to [ContactNetworkResolver.getResultInfo]; this shim just
 * answers "do we have a resolver for this platform?" (`canSync`).
 */
interface PlatformAdapter {
    val canSync: Boolean
    fun resolve(content: String): PlatformResolveResult?
}

object PlatformAdapterRegistry {

    /** Pair of (ContactType, ARGB colour). */
    data class TagInfo(val type: ContactType, val label: String, val color: Long)

    private val TAG_COLORS = mapOf(
        ContactType.QQ to TagInfo(ContactType.QQ, "QQ", 0xFF1296DBL),
        ContactType.QQGroup to TagInfo(ContactType.QQGroup, "QQ群", 0xFF12B7F5L),
        ContactType.Bilibili to TagInfo(ContactType.Bilibili, "B站", 0xFFFB7299L),
        ContactType.WeChat to TagInfo(ContactType.WeChat, "微信", 0xFF07C160L),
        ContactType.TikTok to TagInfo(ContactType.TikTok, "抖音", 0xFFFE2C55L),
        ContactType.Weibo to TagInfo(ContactType.Weibo, "微博", 0xFFE6162DL),
        ContactType.GitHub to TagInfo(ContactType.GitHub, "GitHub", 0xFF24292EL),
        ContactType.Telegram to TagInfo(ContactType.Telegram, "Telegram", 0xFF0088CCL),
        ContactType.TelegramGroup to TagInfo(ContactType.TelegramGroup, "Telegram群", 0xFF0088CCL),
        ContactType.Xiaohongshu to TagInfo(ContactType.Xiaohongshu, "小红书", 0xFFFF2442L),
        ContactType.Facebook to TagInfo(ContactType.Facebook, "Facebook", 0xFF1877F2L),
        ContactType.X to TagInfo(ContactType.X, "X", 0xFF000000L),
        ContactType.Website to TagInfo(ContactType.Website, "网站", 0xFF607D8BL),
    )

    fun getTagInfo(type: ContactType): TagInfo? = TAG_COLORS[type]

    /**
     * Best-effort platform detection by URL host. Used to drive colour
     * pills; the heavy lifting (resolving user info) is now server-side.
     */
    fun resolveContentType(s: String): Pair<ContactType, String> {
        val lower = s.lowercase()
        return when {
            "qq.com" in lower || "gljlw.com" in lower || lower.startsWith("mqq://") -> ContactType.QQ to "QQ"
            "qun.qq.com" in lower -> ContactType.QQGroup to "QQ群"
            "bilibili.com" in lower -> ContactType.Bilibili to "B站"
            "weixin.qq.com" in lower || "wechat" in lower -> ContactType.WeChat to "微信"
            "douyin.com" in lower -> ContactType.TikTok to "抖音"
            "weibo.com" in lower || "weibo.cn" in lower -> ContactType.Weibo to "微博"
            "github.com" in lower -> ContactType.GitHub to "GitHub"
            "t.me" in lower -> ContactType.Telegram to "Telegram"
            "xiaohongshu.com" in lower || "xhslink.com" in lower -> ContactType.Xiaohongshu to "小红书"
            "facebook.com" in lower || "fb.com" in lower -> ContactType.Facebook to "Facebook"
            "x.com" in lower || "twitter.com" in lower -> ContactType.X to "X"
            "http://" in lower || "https://" in lower -> ContactType.Website to "网站"
            else -> ContactType.None to ""
        }
    }

    // ---- Server-backed adapter shim ----

    /**
     * Returns a non-null adapter shim iff the platform has a server-side
     * resolver at `/v1/resolver/<kind>/...`. The shim's `resolve(content)`
     * returns the last value cached via [rememberLastResolve] — UI call
     * sites that need a fresh resolve should call
     * [ContactNetworkResolver.getResultInfo] directly.
     */
    @Volatile
    private var lastResolve: PlatformResolveResult? = null

    fun getAdapter(type: ContactType): PlatformAdapter? = when (type) {
        // Server `/v1/resolver/*` covers all of these.
        ContactType.GitHub, ContactType.Bilibili, ContactType.QQ,
        ContactType.X, ContactType.Telegram -> ServerBackedAdapter
        // QQGroup has its own server endpoint (`qq-avatar`), but the legacy
        // UI never relied on canSync for groups; treat as non-syncable.
        else -> null
    }

    private object ServerBackedAdapter : PlatformAdapter {
        override val canSync: Boolean = true
        override fun resolve(content: String): PlatformResolveResult? = lastResolve
    }

    /**
     * Called by callers that did a fresh server resolve; stashes the
     * result so the next synchronous `adapter.resolve(...)` returns it.
     */
    fun rememberLastResolve(result: PlatformResolveResult?) {
        lastResolve = result
    }
}

// 扩展函数 `PlatformFieldDef.resolve(value)` 在 `ocr/PlatformFields.kt` 同包定义（保留 ocr 语义）。