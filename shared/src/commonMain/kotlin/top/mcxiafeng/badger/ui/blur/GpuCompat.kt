package top.mcxiafeng.badger.ui.blur

import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.utils.BadgerLog

expect object GpuCompat {

    
    fun isAdvancedBlurSupported(): Boolean

    
    fun clearCache()
}

private const val TAG_GPU = "GpuCompat"
private const val KEY_ADVANCED_BLUR_SUPPORTED = "advanced_blur_supported"
private const val KEY_HAS_CACHED = "has_cached"

internal fun readCachedBlurSupport(): Boolean? =
    if (PrefsStore.readBoolean(KEY_HAS_CACHED, false)) {
        PrefsStore.readBoolean(KEY_ADVANCED_BLUR_SUPPORTED, false)
    } else {
        null
    }

internal fun writeCachedBlurSupport(result: Boolean) {
    PrefsStore.writeBoolean(KEY_ADVANCED_BLUR_SUPPORTED, result)
    PrefsStore.writeBoolean(KEY_HAS_CACHED, true)
    BadgerLog.d(TAG_GPU, "GpuCompat: advancedBlurSupported=$result, cached")
}

internal fun clearBlurSupportCache() {
    PrefsStore.remove(KEY_HAS_CACHED)
    PrefsStore.remove(KEY_ADVANCED_BLUR_SUPPORTED)
    BadgerLog.d(TAG_GPU, "GpuCompat: cache cleared")
}
