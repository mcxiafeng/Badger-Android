package top.mcxiafeng.badger.network

/**
 * Pure UI labels for platforms. **No recognition logic lives here.**
 *
 * The previous revisions of this file hosted a `PlatformAdapter`
 * interface and a `ServerBackedAdapter` shim that returned the last
 * cached resolve result via a `@Volatile` field. That shim is gone.
 *
 * The single authoritative recognition path is now [ContactNetworkResolver.identify],
 * which delegates URL/host parsing to the server's
 * `POST /v1/resolver/identify`. This file only knows how to *display*
 * a server-side kind — colour chips and tag labels.
 *
 * `kindToContactType(kind)` maps the server's kind string
 * ("github" | "qq" | "qqGroup" | …) to the corresponding [ContactType]
 * for UI rendering. The `canSync` predicate moved off [ContactType]
 * onto the raw `kind` string — see [SYNCABLE_KINDS] / [kindCanSync] —
 * because the server's `kind` is the source of truth for whether a
 * `/v1/resolver/<kind>/{id}` endpoint exists, not the UI label.
 */
enum class ContactType {
    QQ, QQGroup, Bilibili, WeChat, TikTok, Weibo, GitHub,
    Telegram, TelegramGroup, Xiaohongshu, Facebook, X, Website, None,
}

/**
 * Subset of server `kind` values that the server can sync via the
 * per-platform `/v1/resolver/<kind>/{id}` endpoints. Keeps in sync
 * with `Badger-Server/internal/resolver/resolver.go`.
 *
 * Note: this is the *server's* `kind` (string), not [ContactType].
 * Use `kind.kindCanSync` as the predicate.
 */
val SYNCABLE_KINDS: Set<String> = setOf(
    "github",
    "bilibili",
    "qq",
    "x",
    "telegram",
)

/** True iff this server-side kind has a working `/v1/resolver/<kind>/...` endpoint. */
val String.kindCanSync: Boolean
    get() = this in SYNCABLE_KINDS

/**
 * Map a server-side `kind` string to the [ContactType] used for UI
 * tagging. Returns `null` for unrecognised kinds (e.g. "unknown").
 *
 * Note: "qq" and "qqGroup" are distinguished here purely for UI
 * colour/label; the *recognised-fieldKey* decision is owned by the
 * server's `contact_map` and is consumed separately by scanners
 * (Phase 2).
 */
fun kindToContactType(kind: String): ContactType? = when (kind) {
    "qq" -> ContactType.QQ
    "qqGroup" -> ContactType.QQGroup
    "bilibili" -> ContactType.Bilibili
    "wechat" -> ContactType.WeChat
    "douyin" -> ContactType.TikTok
    "weibo" -> ContactType.Weibo
    "github" -> ContactType.GitHub
    "telegram" -> ContactType.Telegram
    "telegramGroup" -> ContactType.TelegramGroup
    "xiaohongshu" -> ContactType.Xiaohongshu
    "facebook" -> ContactType.Facebook
    "x" -> ContactType.X
    "website" -> ContactType.Website
    "unknown", "" -> null
    else -> null
}

/** Mirror of the server-side `/v1/resolver/identify` response (subset). */
data class PlatformResolveResult(
    val name: String?,
    val avatarUrl: String?,
    val description: String?,
    val contactMap: Map<String, String>,
)

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
}

/**
 * 扩展函数 PlatformFieldDef.resolve(value) 在 ocr/PlatformFields.kt 同包定义（保留 ocr 语义）。
 */
