package top.mcxiafeng.badger.pages.setupguide

import android.content.Context

internal const val TAG = "SetupGuide"

private const val HINT_PREFS = "badger_hints"
private const val KEY_SETUP_COMPLETED = "hint_shown_setup_guide_completed"

fun isSetupGuideCompleted(context: Context): Boolean {
    return context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SETUP_COMPLETED, false)
}

fun setSetupGuideCompleted(context: Context) {
    context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
}

data class AiProviderPreset(
    val name: String,
    val endpoint: String,
    val defaultModel: String,
    val supportsVision: Boolean,
    val description: String
)

val AI_PRESETS = listOf(
    AiProviderPreset("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", false, "国内常用，价格低"),
    AiProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", true, "阿里云出品，支持视觉"),
    AiProviderPreset("智谱清言", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash", true, "支持视觉，响应快"),
    AiProviderPreset("月之暗面", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", false, "长文本能力强"),
    AiProviderPreset("硅基流动", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-7B-Instruct", true, "聚合平台，模型多"),
    AiProviderPreset("自定义", "", "", false, "手动填写 API 地址和模型")
)