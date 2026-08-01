package top.mcxiafeng.badger.network

/**
 * [§15 #7] Compat shim around the old `LinkResolver.resolve(...)` API. The
 * real work (short-link redirect + platform regex) now lives server-side at
 * `/v1/resolver/link`; this stub returns the raw input as the only field.
 * UI callers route to the upstream endpoint when they need actual work —
 * this placeholder keeps the type alive until those UI calls move.
 */
object LinkResolver {

    data class LinkResolveResult(
        val jumpLink: String?,
        val originalLink: String?,
        val value: String?,
        val displayName: String?,
        val avatarUrl: String?,
        val errorMessage: String?,
    )

    suspend fun resolve(
        @Suppress("UNUSED_PARAMETER") fieldKey: String,
        rawInput: String,
    ): LinkResolveResult {
        // [修复防御]: fieldKey is still passed by callers for API compatibility,
        // but the server-driven resolver ignores it. Keep it on the signature
        // so AddPlatformDialog / AddPlatformComponents don't churn.
        return LinkResolveResult(
            jumpLink = null,
            originalLink = rawInput,
            value = rawInput,
            displayName = null,
            avatarUrl = null,
            errorMessage = null,
        )
    }
}

/**
 * Compat shim for the deleted `PlatformIdExtractor`. Only [normalizeToKey]
 * is still called from [AddPlatformDialog].
 */
object PlatformIdExtractor {
    fun normalizeToKey(name: String): String = name
}
