package top.mcxiafeng.badger.ocr

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * AI OCR 服务配置管理
 *
 * 存储 AI 文字识别服务所需的配置信息：
 * - 功能开关
 * - API Endpoint
 * - API Key
 * - Model 名称
 * - 隐私同意状态
 */
object AiOcrConfig {
    private const val PREFS_NAME = "ai_ocr_config"
    private const val ENCRYPTED_PREFS_NAME = "ai_ocr_credentials"
    
    private const val KEY_API_ENDPOINT = "api_endpoint"
    private const val KEY_API_PATH = "api_path"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_SUPPORTS_VISION = "supports_vision"
    private const val KEY_PRIVACY_AGREED = "privacy_agreed"
    private const val KEY_AUTO_FALLBACK = "auto_fallback"
    private const val KEY_AI_OCR_ENABLED = "ai_ocr_enabled"

    // === AI Tag 槽（与 OCR 共享 endpoint/api_key/privacy，但 model 独立）===
    private const val KEY_TAG_MODEL = "tag_model"
    private const val KEY_AI_TAG_ENABLED = "ai_tag_enabled"
    private const val KEY_TAG_PRIVACY_AGREED = "tag_privacy_agreed"

    // 默认值（base URL，不含 /chat/completions）
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val DEFAULT_MODEL = "gpt-4o"
    private const val DEFAULT_API_PATH = "/chat/completions"
    // AI Tag 推荐更适合"JSON 稳定 + 便宜"的模型（OCR 多用 vision 模型）
    private const val DEFAULT_TAG_MODEL = "deepseek-chat"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 从用户输入中提取 base URL（去掉末尾的 /chat/completions） */
    private fun normalizeToBaseUrl(input: String): String {
        if (input.isBlank()) return ""
        return input.trimEnd('/').replace(Regex("/chat/completions$"), "")
    }

    /** 将 base URL + path 解析为完整的端点 */
    fun resolveEndpoint(baseUrl: String, apiPath: String = getApiPathDefault()): String {
        if (baseUrl.isBlank()) return ""
        val trimmed = baseUrl.trimEnd('/')
        val path = apiPath.trim()
        if (path.isBlank()) return trimmed
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return if (trimmed.endsWith(normalizedPath)) trimmed
        else trimmed + normalizedPath
    }

    /**
     * API Endpoint（完整地址，含路径）
     *
     * 内部存储 base URL + path，调用时自动拼合。
     */
    fun getApiEndpoint(context: Context): String {
        val baseUrl = getPrefs(context).getString(KEY_API_ENDPOINT, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        val apiPath = getPrefs(context).getString(KEY_API_PATH, DEFAULT_API_PATH) ?: DEFAULT_API_PATH
        return resolveEndpoint(baseUrl, apiPath)
    }

    /**
     * API Base URL（不含 /chat/completions，用于界面显示和编辑）
     */
    fun getApiBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_API_ENDPOINT, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    /**
     * 设置 API 地址（自动归一化：去掉末尾 /chat/completions 存储）
     */
    fun setApiEndpoint(context: Context, endpoint: String) {
        getPrefs(context).edit { putString(KEY_API_ENDPOINT, normalizeToBaseUrl(endpoint)) }
    }

    fun getApiPath(context: Context): String {
        return getPrefs(context).getString(KEY_API_PATH, DEFAULT_API_PATH) ?: DEFAULT_API_PATH
    }

    fun setApiPath(context: Context, path: String) {
        getPrefs(context).edit { putString(KEY_API_PATH, path) }
    }

    private fun getApiPathDefault(): String = DEFAULT_API_PATH
    
    /**
     * API Key
     */
    fun getApiKey(context: Context): String {
        return encryptedPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, apiKey: String) {
        encryptedPrefs(context).edit { putString(KEY_API_KEY, apiKey) }
    }
    
    /**
     * 模型名称
     */
    fun getModel(context: Context): String {
        return getPrefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }
    
    fun setModel(context: Context, model: String) {
        getPrefs(context).edit { putString(KEY_MODEL, model) }
    }
    
    /**
     * 用户是否已同意隐私政策（上传图片到 AI 服务器）
     */
    fun isPrivacyAgreed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRIVACY_AGREED, false)
    }
    
    fun setPrivacyAgreed(context: Context, agreed: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_PRIVACY_AGREED, agreed) }
    }
    
    /**
     * 检查是否已配置好 AI 服务
     */
    fun isConfigured(context: Context): Boolean {
        return getApiEndpoint(context).isNotBlank() && getApiKey(context).isNotBlank()
    }

    /**
     * 当前模型是否支持图片输入
     *
     * 如果支持，拍照时会将图片直接发到 AI（Vision API），
     * 否则先用 ML Kit OCR 提取文字再发给纯文本模型。
     */
    fun supportsVision(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SUPPORTS_VISION, false)
    }

    fun setSupportsVision(context: Context, supports: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_SUPPORTS_VISION, supports) }
    }

    /**
     * 是否支持 Vision 模式（已配置支持图片的模型）
     */
    fun hasVisionModel(context: Context): Boolean {
        return supportsVision(context) && getModel(context).isNotBlank()
    }

    /**
     * 模型失败时是否自动降级到下一个可用模型
     */
    fun isAutoFallback(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_FALLBACK, true)
    }

    fun setAutoFallback(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AUTO_FALLBACK, enabled) }
    }

    fun isAiOcrEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AI_OCR_ENABLED, false)
    }

    fun setAiOcrEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AI_OCR_ENABLED, enabled) }
    }

    // ============ AI Tag 槽 ============
    // AI Tag 与 AI OCR 共享 endpoint / api_key / privacy 配置，
    // 但 model 独立（OCR 用 vision 模型如 gpt-4o，Tag 用 JSON 稳定便宜的如 deepseek-chat）。

    /**
     * AI Tag 专用模型。默认 deepseek-chat（JSON 输出稳定 + 价格低）。
     * 与 OCR 的 [getModel] 独立存储。
     */
    fun getTagModel(context: Context): String {
        return getPrefs(context).getString(KEY_TAG_MODEL, DEFAULT_TAG_MODEL) ?: DEFAULT_TAG_MODEL
    }

    fun setTagModel(context: Context, model: String) {
        getPrefs(context).edit { putString(KEY_TAG_MODEL, model) }
    }

    /** 是否启用 AI 标签生成（默认关闭，用户主动开启） */
    fun isAiTagEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AI_TAG_ENABLED, false)
    }

    fun setAiTagEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AI_TAG_ENABLED, enabled) }
    }

    /**
     * 是否同意把联系人 bio / 自我介绍发送给 AI 服务以生成标签。
     * 关闭时不读取任何 tag_ 开头的字段，AI Tag 调用入口直接 fail-fast。
     */
    fun isAiTagPrivacyAgreed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TAG_PRIVACY_AGREED, false)
    }

    fun setAiTagPrivacyAgreed(context: Context, agreed: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_TAG_PRIVACY_AGREED, agreed) }
    }

    /**
     * AI Tag 是否真正可用：配置好 + 启用 + 同意隐私。
     * 三者缺一即视为不可用，调用方应在入口 fail-fast。
     */
    fun isAiTagReady(context: Context): Boolean {
        return isConfigured(context)
            && isAiTagEnabled(context)
            && isAiTagPrivacyAgreed(context)
            && getTagModel(context).isNotBlank()
    }
}