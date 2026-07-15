package top.mcxiafeng.badger.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.data.AuthPrefs

/**
 * Compatibility wrapper that exposes the old client-side
 * `ContactNetworkResolver.getResultInfo(...)` shape but performs the
 * actual upstream calls through [ServerApi] (server-side resolver).
 *
 * The return type matches what the existing UI expects.
 */
data class NetworkResolveResult(
    val nickname: String?,
    val description: String?,
    val avatarUrl: String?,
    val contactMap: Map<String, String>,
    val type: ContactType,
)

data class QQUser(val nickname: String?, val longNick: String?)

object ContactNetworkResolver {

    private const val TAG = "ContactNetworkResolver"

    private fun api(context: Context): ServerApi = ServerApi(
        baseUrl = AuthPrefs.readServerUrl(context),
        http = okhttp3.OkHttpClient(),
        tokenProvider = { AuthPrefs.readRefreshToken(context) },
    )

    /**
     * Legacy entry point. Decides the platform from the URL host and
     * delegates to the right `/v1/resolver/...` endpoint. Returns null
     * when the URL is unrecognised.
     */
    fun getResultInfo(
        content: String,
        @Suppress("UNUSED_PARAMETER") contactMap: Map<String, String>,
        type: ContactType? = null,
    ): NetworkResolveResult? {
        val ctx = currentContext()
        if (ctx == null) {
            Log.w(TAG, "no context set — call setContext() once from Application")
            return null
        }
        val a = api(ctx)
        val detected = type ?: PlatformAdapterRegistry.resolveContentType(content).first
        return try {
            val obj: JsonObject? = when (detected) {
                ContactType.GitHub -> {
                    val login = extractGitHubLogin(content) ?: return null
                    a.resolveGitHub(login)
                }
                ContactType.Bilibili -> {
                    val uid = extractBiliUid(content) ?: return null
                    a.resolveBili(uid)
                }
                ContactType.QQ -> {
                    val qq = extractQqNumber(content) ?: return null
                    a.resolveQq(qq)
                }
                ContactType.X -> {
                    val h = extractXHandle(content) ?: return null
                    a.resolveTwitter(h)
                }
                ContactType.Telegram -> {
                    val p = extractTelegramPath(content) ?: return null
                    a.resolveTelegram(p)
                }
                else -> null
            } ?: return NetworkResolveResult(
                nickname = null,
                description = null,
                avatarUrl = null,
                contactMap = emptyMap(),
                type = detected,
            )
            obj?.let { objToResult(it, detected) } ?: NetworkResolveResult(
                nickname = null,
                description = null,
                avatarUrl = null,
                contactMap = emptyMap(),
                type = detected,
            )
        } catch (e: Exception) {
            Log.w(TAG, "resolve failed for $content", e)
            null
        }
    }

    private fun objToResult(obj: JsonObject, type: ContactType): NetworkResolveResult {
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
        val sig = obj.get("signature")?.takeIf { !it.isJsonNull }?.asString
        val avatar = obj.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString
        val map = obj.getAsJsonObject("contact_map")?.entrySet()?.associate { it.key to it.value.asString } ?: emptyMap()
        return NetworkResolveResult(nickname = name, description = sig, avatarUrl = avatar, contactMap = map, type = type)
    }

    fun toContactAndInfo(
        @Suppress("UNUSED_PARAMETER") result: NetworkResolveResult?,
        @Suppress("UNUSED_PARAMETER") rawContent: String,
    ): Pair<Any, Any>? = null

    // ---- URL extraction (lightweight; heavy lifting lives server-side) ----

    private fun extractGitHubLogin(s: String): String? {
        val m = Regex("""github\.com/([a-zA-Z0-9_-]+)""").find(s) ?: return null
        val name = m.groupValues[1]
        if (name in setOf("orgs", "repos", "settings", "notifications", "explore",
                "trending", "search", "features", "marketplace", "pricing",
                "login", "signup", "topics", "collections", "events")) return null
        return name
    }

    private fun extractBiliUid(s: String): String? {
        val m = Regex("""space\.bilibili\.com/(\d+)""").find(s) ?: return null
        return m.groupValues[1]
    }

    private fun extractQqNumber(s: String): String? {
        val m = Regex("""(?:uin|qq)=(\d{5,12})""").find(s) ?: return null
        return m.groupValues[1]
    }

    private fun extractXHandle(s: String): String? {
        val m = Regex("""(?:x\.com|twitter\.com)/([A-Za-z0-9_]+)""").find(s) ?: return null
        return m.groupValues[1]
    }

    private fun extractTelegramPath(s: String): String? {
        val m = Regex("""t\.me/([A-Za-z0-9_]+)""").find(s) ?: return null
        return m.groupValues[1]
    }

    // ---- context plumbing for the static-object API ----

    @Volatile private var ctx: Context? = null
    fun setContext(c: Context) { ctx = c.applicationContext }
    private fun currentContext(): Context? = ctx
}