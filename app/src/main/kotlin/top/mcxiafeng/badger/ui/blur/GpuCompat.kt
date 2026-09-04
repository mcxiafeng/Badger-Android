package top.mcxiafeng.badger.ui.blur

import android.os.Build
import android.util.Log
import top.mcxiafeng.badger.data.prefs.PrefsStore

/**
 * GPU 兼容性检测：判断设备是否支持高级模糊（miuix-blur RuntimeShader / AGSL）。
 *
 * 条件：
 * - API >= 33（Android 13+，RuntimeShader 正式 API）
 * - GPU 渲染器不在黑名单中（已知 Adreno 6xx + VK-0.0 驱动会 SIGSEGV）
 *
 * [KMP K05] 结果缓存在 DataStore（经 PrefsStore），避免每次重组都检测。
 */
object GpuCompat {

    private const val TAG = "GpuCompat"
    private const val KEY_ADVANCED_BLUR_SUPPORTED = "advanced_blur_supported"
    private const val KEY_HAS_CACHED = "has_cached"

    /** 已知 SIGSEGV 的 GPU 渲染器关键词黑名单 */
    private val GPU_BLACKLIST = listOf(
        "Adreno (6[0-9]{2})",  // Adreno 6xx 系列
    )

    /**
     * 检测当前设备是否支持高级模糊。
     * 首次调用执行检测并缓存，后续直接读缓存。
     */
    fun isAdvancedBlurSupported(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean {
        if (PrefsStore.readBoolean(KEY_HAS_CACHED, false)) {
            return PrefsStore.readBoolean(KEY_ADVANCED_BLUR_SUPPORTED, false)
        }

        val result = detectAdvancedBlurSupport()
        PrefsStore.writeBoolean(KEY_ADVANCED_BLUR_SUPPORTED, result)
        PrefsStore.writeBoolean(KEY_HAS_CACHED, true)

        Log.d(TAG, "GpuCompat: advancedBlurSupported=$result, cached")
        return result
    }

    /**
     * 清除缓存，强制下次重新检测（设置页面可调用）。
     */
    fun clearCache(@Suppress("UNUSED_PARAMETER") context: android.content.Context) {
        PrefsStore.remove(KEY_HAS_CACHED)
        PrefsStore.remove(KEY_ADVANCED_BLUR_SUPPORTED)
        Log.d(TAG, "GpuCompat: cache cleared")
    }

    private fun detectAdvancedBlurSupport(): Boolean {
        // 1. API 级别检查
        if (Build.VERSION.SDK_INT < 33) {
            Log.d(TAG, "GpuCompat: API ${Build.VERSION.SDK_INT} < 33, not supported")
            return false
        }

        // 2. GPU 渲染器黑名单检查
        try {
            val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: ""
            Log.d(TAG, "GpuCompat: GPU renderer=$renderer")
            for (pattern in GPU_BLACKLIST) {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(renderer)) {
                    Log.d(TAG, "GpuCompat: GPU blacklisted by pattern=$pattern")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GpuCompat: GPU renderer check failed", e)
            // 无法检测 GPU 型号时保守返回 false
            return false
        }

        Log.d(TAG, "GpuCompat: API >= 33, GPU not blacklisted, supported")
        return true
    }
}
