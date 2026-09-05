package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.data.prefs.PrefsStore

/**
 * [§15 #4] Compat shim around the AI-OCR / AI-Tag config that used to live in
 * `ocr.AiOcrConfig` and the deleted `AiPresets`. With the move to
 * Badger-Server the actual LLM key never touches the device. The Settings UI
 * still flips "AI OCR enabled" + reads the configured model, so a thin
 * object wrapper around two keys is all that's left.
 *
 * [KMP K05] Storage: DataStore Preferences（经 PrefsStore），原 badger_ai_ocr 文件。
 */
object AiOcrConfig {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODEL = "model"
    private const val KEY_TAG_PRIVACY = "ai_tag_privacy_agreed"

    // ---- OCR ----

    /** True iff the user has flipped on the "AI 名片识别" switch. */
    fun isConfigured(): Boolean =
        PrefsStore.readBoolean(KEY_ENABLED, false)

    /** Alias kept for legacy callers — same predicate as [isConfigured]. */
    fun isAiOcrEnabled(): Boolean = isConfigured()

    fun setEnabled(b: Boolean) {
        PrefsStore.writeBoolean(KEY_ENABLED, b)
    }

    /**
     * [修复防御]: Server-driven OCR model — the client does not pick a model
     * anymore; the server decides based on the user's API-key plan. Returns a
     * non-empty placeholder so the AI-model dropdown in Settings stays
     * populated even before the first server round-trip.
     */
    fun hasVisionModel(): Boolean = true

    /** Alias for [hasVisionModel] used by some UI call-sites. */
    fun supportsVision(): Boolean = hasVisionModel()

    /** Returns the human-readable name of the model currently in use. */
    fun getModel(): String =
        PrefsStore.readString(KEY_MODEL) ?: "qwen-vl"

    fun setModel(v: String) {
        PrefsStore.writeString(KEY_MODEL, v)
    }

    // ---- AI Tag privacy toggle ----
    // 客户端只保留「用户是否同意隐私协议」这一个开关;推荐 / 模型选择全在服务端。

    fun isAiTagPrivacyAgreed(): Boolean =
        PrefsStore.readBoolean(KEY_TAG_PRIVACY, false)

    fun setAiTagPrivacyAgreed(b: Boolean) {
        PrefsStore.writeBoolean(KEY_TAG_PRIVACY, b)
    }

    /** Alias for [setAiTagPrivacyAgreed] used by [ScannerSubDialogs]. */
    fun setPrivacyAgreed(b: Boolean) = setAiTagPrivacyAgreed(b)
}
