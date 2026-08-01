package top.mcxiafeng.badger.ocr

import android.content.Context

/**
 * [§15 #4] Compat shim around the AI-OCR / AI-Tag config that used to live in
 * `ocr.AiOcrConfig` and the deleted `AiPresets`. With the move to
 * Badger-Server the actual LLM key never touches the device. The Settings UI
 * still flips "AI OCR enabled" + reads the configured model, so a thin
 * object wrapper around two SharedPreferences keys is all that's left.
 *
 * [修复防御]: Top-level functions on a non-class file would force callers to
 * write `readXxx(context)` instead of `AiOcrConfig.xxx(...)`. The object-style
 * shim keeps the existing `AiOcrConfig.isConfigured(context)` call shape
 * compiling without touching every caller.
 */
object AiOcrConfig {
    private const val PREFS = "badger_ai_ocr"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODEL = "model"
    private const val KEY_TAG_PRIVACY = "ai_tag_privacy_agreed"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- OCR ----

    /** True iff the user has flipped on the "AI 名片识别" switch. */
    fun isConfigured(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)

    /** Alias kept for legacy callers — same predicate as [isConfigured]. */
    fun isAiOcrEnabled(ctx: Context): Boolean = isConfigured(ctx)

    fun setEnabled(ctx: Context, b: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, b).apply()
    }

    /**
     * [修复防御]: Server-driven OCR model — the client does not pick a model
     * anymore; the server decides based on the user's API-key plan. Returns a
     * non-empty placeholder so the AI-model dropdown in Settings stays
     * populated even before the first server round-trip.
     */
    fun hasVisionModel(ctx: Context): Boolean = true

    /** Alias for [hasVisionModel] used by some UI call-sites. */
    fun supportsVision(ctx: Context): Boolean = hasVisionModel(ctx)

    /** Returns the human-readable name of the model currently in use. */
    fun getModel(ctx: Context): String = sp(ctx).getString(KEY_MODEL, "qwen-vl") ?: "qwen-vl"

    fun setModel(ctx: Context, v: String) {
        sp(ctx).edit().putString(KEY_MODEL, v).apply()
    }

    // ---- AI Tag privacy toggle ----
    // 客户端只保留「用户是否同意隐私协议」这一个开关;推荐 / 模型选择全在服务端。

    fun isAiTagPrivacyAgreed(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_TAG_PRIVACY, false)

    fun setAiTagPrivacyAgreed(ctx: Context, b: Boolean) {
        sp(ctx).edit().putBoolean(KEY_TAG_PRIVACY, b).apply()
    }

    /** Alias for [setAiTagPrivacyAgreed] used by [ScannerSubDialogs]. */
    fun setPrivacyAgreed(ctx: Context, b: Boolean) = setAiTagPrivacyAgreed(ctx, b)
}
