package top.mcxiafeng.badger.ocr

import android.content.Context

/**
 * Lightweight shim around the AI-OCR / AI-Tag config that used to live in
 * `ocr.AiOcrConfig` and the deleted `AiPresets`. With the move to
 * Badger-Server the actual LLM key never touches the device, so all that's
 * left here is "is the user opted in" + a couple of deprecated accessors
 * the Settings page still calls.
 *
 * [修复防御]: Top-level functions on a non-class file means callers that
 * import the file have to write `readXxx(context)` instead of
 * `AiOcrConfig.xxx(...)`. We expose object-style shims so the existing
 * `AiOcrConfig.isConfigured(context)` call shape keeps compiling.
 */
object AiOcrConfig {
    private const val PREFS = "badger_ai_ocr"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODEL = "model"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- OCR ----

    /** True iff the user has flipped on the "AI 名片识别" switch. */
    fun isConfigured(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)

    /** Alias kept for legacy callers — same predicate as [isConfigured]. */
    fun isAiOcrEnabled(ctx: Context): Boolean = isConfigured(ctx)

    fun setEnabled(ctx: Context, b: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, b).apply()
    }

    /** Whether the configured model supports vision (image) input. */
    fun hasVisionModel(ctx: Context): Boolean = true

    /** Alias for [hasVisionModel] used by some UI call-sites. */
    fun supportsVision(ctx: Context): Boolean = hasVisionModel(ctx)

    /** Returns the human-readable name of the model currently in use. */
    fun getModel(ctx: Context): String = sp(ctx).getString(KEY_MODEL, "qwen-vl") ?: "qwen-vl"

    // ---- Deprecated accessors (stubs for migration period) ----

    /** Server-side now; user no longer needs an endpoint. */
    fun getApiBaseUrl(ctx: Context): String = ""

    fun getApiPath(ctx: Context): String = ""

    fun getApiKey(ctx: Context): String = ""

    fun isAutoFallback(ctx: Context): Boolean = false

    /** Alias for [setApiBaseUrl] used by Settings UI. */
    fun setApiEndpoint(ctx: Context, v: String) = setApiBaseUrl(ctx, v)

    fun setApiBaseUrl(ctx: Context, @Suppress("UNUSED_PARAMETER") v: String) {}
    fun setApiPath(ctx: Context, @Suppress("UNUSED_PARAMETER") v: String) {}
    fun setApiKey(ctx: Context, @Suppress("UNUSED_PARAMETER") v: String) {}
    fun setModel(ctx: Context, v: String) {
        sp(ctx).edit().putString(KEY_MODEL, v).apply()
    }
    fun setAutoFallback(ctx: Context, @Suppress("UNUSED_PARAMETER") v: Boolean) {}
    fun setSupportsVision(ctx: Context, @Suppress("UNUSED_PARAMETER") v: Boolean) {}

    // ---- AI Tag ----

    fun isAiTagEnabled(ctx: Context): Boolean = false
    fun setAiTagEnabled(ctx: Context, @Suppress("UNUSED_PARAMETER") v: Boolean) {}
    fun getTagModel(ctx: Context): String = "deepseek-chat"
    fun setTagModel(ctx: Context, @Suppress("UNUSED_PARAMETER") v: String) {}
    fun isAiTagPrivacyAgreed(ctx: Context): Boolean = sp(ctx).getBoolean("ai_tag_privacy_agreed", false)
    fun setAiTagPrivacyAgreed(ctx: Context, b: Boolean) = setPrivacyAgreed(ctx, b)
    fun setPrivacyAgreed(ctx: Context, b: Boolean) {
        sp(ctx).edit().putBoolean("ai_tag_privacy_agreed", b).apply()
    }
}

/**
 * Stub [ModelInfo] data class kept so [AiOcrService] compiles. The
 * preset list itself is empty under the new server-driven model — users
 * don't pick models client-side anymore. AI toggles for OCR / Tag
 * recommendations are configured in Settings → General → AI.
 */
data class ModelInfo(
    val id: String = "",
    val name: String,
    val endpoint: String = "",
    val supportsVision: Boolean = false,
    val autoFallback: Boolean = false,
    val apiPath: String = "",
    val requiresApiKey: Boolean = true,
) {
    /** Convenience accessor — equivalent to [id] when callers want a non-null display id. */
    val defaultModel: String get() = id.ifBlank { name }
}

/** Empty preset list. The server picks the model now. */
val AI_PRESETS: List<ModelInfo> = listOf(ModelInfo(name = "自定义", id = "custom"))