package top.mcxiafeng.badger.ocr

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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
    
    private const val KEY_API_ENDPOINT = "api_endpoint"
    private const val KEY_API_PATH = "api_path"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_SUPPORTS_VISION = "supports_vision"
    private const val KEY_PRIVACY_AGREED = "privacy_agreed"
    private const val KEY_AUTO_FALLBACK = "auto_fallback"
    private const val KEY_AI_OCR_ENABLED = "ai_ocr_enabled"
    
    // 默认值（base URL，不含 /chat/completions）
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val DEFAULT_MODEL = "gpt-4o"
    private const val DEFAULT_API_PATH = "/chat/completions"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }
    
    fun setApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit { putString(KEY_API_KEY, apiKey) }
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
}