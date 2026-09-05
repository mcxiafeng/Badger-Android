package top.mcxiafeng.badger.ui.blur

import android.opengl.GLES20
import android.os.Build
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GpuCompat.android"

/** 已知 SIGSEGV 的 GPU 渲染器关键词黑名单 */
private val GPU_BLACKLIST = listOf(
    "Adreno (6[0-9]{2})",  // Adreno 6xx 系列
)

/**
 * [KMP K13c] Android actual：API >= 33（RuntimeShader 正式 API）+ 渲染器黑名单
 * （已知 Adreno 6xx + VK-0.0 驱动会 SIGSEGV）。原 app 实现平移。
 */
actual object GpuCompat {

    actual fun isAdvancedBlurSupported(): Boolean {
        readCachedBlurSupport()?.let { return it }

        val result = detectAdvancedBlurSupport()
        writeCachedBlurSupport(result)
        return result
    }

    actual fun clearCache() = clearBlurSupportCache()

    private fun detectAdvancedBlurSupport(): Boolean {
        // 1. API 级别检查
        if (Build.VERSION.SDK_INT < 33) {
            BadgerLog.d(TAG, "GpuCompat: API ${Build.VERSION.SDK_INT} < 33, not supported")
            return false
        }

        // 2. GPU 渲染器黑名单检查
        return try {
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            BadgerLog.d(TAG, "GpuCompat: GPU renderer=$renderer")
            for (pattern in GPU_BLACKLIST) {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(renderer)) {
                    BadgerLog.d(TAG, "GpuCompat: GPU blacklisted by pattern=$pattern")
                    return false
                }
            }
            BadgerLog.d(TAG, "GpuCompat: API >= 33, GPU not blacklisted, supported")
            true
        } catch (e: Exception) {
            BadgerLog.w(TAG, "GpuCompat: GPU renderer check failed", e)
            // 无法检测 GPU 型号时保守返回 false
            false
        }
    }
}
