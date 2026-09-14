package top.mcxiafeng.badger.ocr

import top.mcxiafeng.badger.data.prefs.PrefsStore

object AiOcrConfig {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODEL = "model"
    private const val KEY_TAG_PRIVACY = "ai_tag_privacy_agreed"

    

    
    fun isConfigured(): Boolean =
        PrefsStore.readBoolean(KEY_ENABLED, false)

    
    fun isAiOcrEnabled(): Boolean = isConfigured()

    fun setEnabled(b: Boolean) {
        PrefsStore.writeBoolean(KEY_ENABLED, b)
    }

    

    fun hasVisionModel(): Boolean = true

    
    fun supportsVision(): Boolean = hasVisionModel()

    
    fun getModel(): String =
        PrefsStore.readString(KEY_MODEL) ?: "qwen-vl"

    fun setModel(v: String) {
        PrefsStore.writeString(KEY_MODEL, v)
    }

    
    

    fun isAiTagPrivacyAgreed(): Boolean =
        PrefsStore.readBoolean(KEY_TAG_PRIVACY, false)

    fun setAiTagPrivacyAgreed(b: Boolean) {
        PrefsStore.writeBoolean(KEY_TAG_PRIVACY, b)
    }

    
    fun setPrivacyAgreed(b: Boolean) = setAiTagPrivacyAgreed(b)
}
