package top.mcxiafeng.badger.ui.blur

import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [KMP K13c] GPU 兼容性检测平台边界（判断是否支持高级模糊/液态玻璃）。
 *
 * Android actual：API >= 33 + GPU 渲染器黑名单（原 app 实现平移，K14 特效重做时
 * 按 docs/effect-visual-spec.md §6 重新定义三档降级）。
 * iOS actual：设备档次映射骨架（A12 以下 → 标准磨砂，K16 真机验证）。
 *
 * [KMP K05] 结果缓存在 DataStore（经 PrefsStore），避免每次重组都检测。
 */
expect object GpuCompat {

    /** 检测当前设备是否支持高级模糊。首次调用执行检测并缓存。 */
    fun isAdvancedBlurSupported(): Boolean

    /** 清除缓存，强制下次重新检测（设置页面可调用）。 */
    fun clearCache()
}

private const val TAG_GPU = "GpuCompat"
private const val KEY_ADVANCED_BLUR_SUPPORTED = "advanced_blur_supported"
private const val KEY_HAS_CACHED = "has_cached"

/** common 侧共享的缓存读写（actual 的 detect 结果落同一份 DataStore）。 */
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
