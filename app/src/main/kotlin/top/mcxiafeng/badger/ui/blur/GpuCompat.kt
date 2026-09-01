package top.mcxiafeng.badger.ui.blur

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.util.Log

/**
 * GPU 兼容性检测：判断设备是否支持高级模糊（miuix-blur RuntimeShader / AGSL）。
 *
 * 条件：
 * - API >= 33（Android 13+，RuntimeShader 正式 API）
 * - GPU 渲染器不在黑名单中（已知 Adreno 6xx + VK-0.0 驱动会 SIGSEGV）
 *
 * 结果缓存在 SharedPreferences，避免每次重组都检测。
 */
object GpuCompat {

    private const val TAG = "GpuCompat"
    private const val PREFS_NAME = "badger_gpu_compat"
    private const val KEY_ADVANCED_BLUR_SUPPORTED = "advanced_blur_supported"
    private const val KEY_HAS_CACHED = "has_cached"

    /** 已知 SIGSEGV 的 GPU 渲染器关键词黑名单 */
    private val GPU_BLACKLIST = listOf(
        Regex("Adreno (6[0-9]{2})", RegexOption.IGNORE_CASE),
    )

    /**
     * 检测当前设备是否支持高级模糊。
     * 首次调用执行检测并缓存，后续直接读缓存。
     */
    fun isAdvancedBlurSupported(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_CACHED, false)) {
            return prefs.getBoolean(KEY_ADVANCED_BLUR_SUPPORTED, false)
        }

        val result = detectAdvancedBlurSupport()
        prefs.edit()
            .putBoolean(KEY_ADVANCED_BLUR_SUPPORTED, result)
            .putBoolean(KEY_HAS_CACHED, true)
            .apply()

        Log.d(TAG, "GpuCompat: advancedBlurSupported=$result, cached")
        return result
    }

    /**
     * 清除缓存，强制下次重新检测（设置页面可调用）。
     */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HAS_CACHED)
            .remove(KEY_ADVANCED_BLUR_SUPPORTED)
            .apply()
        Log.d(TAG, "GpuCompat: cache cleared")
    }

    private fun detectAdvancedBlurSupport(): Boolean {
        // 1. API 级别检查
        if (Build.VERSION.SDK_INT < 33) {
            Log.d(TAG, "GpuCompat: API ${Build.VERSION.SDK_INT} < 33, not supported")
            return false
        }

        // 2. GLES20.glGetString() 依赖当前 EGL/GL context；Application/Compose 启动早期可能没有 context。
        // 无 context 时不能把空 renderer 当作“支持”，否则会错误放行高级模糊路径。
        return try {
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            if (renderer.isNullOrBlank()) {
                Log.w(TAG, "GpuCompat: GPU renderer unavailable, conservatively disabling advanced blur")
                false
            } else {
                Log.d(TAG, "GpuCompat: GPU renderer=$renderer")
                val blacklisted = GPU_BLACKLIST.firstOrNull { it.containsMatchIn(renderer) }
                if (blacklisted != null) {
                    Log.d(TAG, "GpuCompat: GPU blacklisted by pattern=${blacklisted.pattern}")
                    false
                } else {
                    Log.d(TAG, "GpuCompat: API >= 33, GPU not blacklisted, supported")
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GpuCompat: GPU renderer check failed, disabling advanced blur", e)
            false
        }
    }
}