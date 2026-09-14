package top.mcxiafeng.badger.ui.blur

import android.opengl.GLES20
import android.os.Build
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GpuCompat.android"

private val GPU_BLACKLIST = listOf(
    "Adreno (6[0-9]{2})",  
)

actual object GpuCompat {

    actual fun isAdvancedBlurSupported(): Boolean {
        readCachedBlurSupport()?.let { return it }

        val result = detectAdvancedBlurSupport()
        writeCachedBlurSupport(result)
        return result
    }

    actual fun clearCache() = clearBlurSupportCache()

    private fun detectAdvancedBlurSupport(): Boolean {
        
        if (Build.VERSION.SDK_INT < 33) {
            BadgerLog.d(TAG, "GpuCompat: API ${Build.VERSION.SDK_INT} < 33, not supported")
            return false
        }

        
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
            
            false
        }
    }
}
