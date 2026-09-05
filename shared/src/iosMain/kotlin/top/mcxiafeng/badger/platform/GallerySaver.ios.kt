package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GallerySaver.ios"

/**
 * [KMP K13c] iOS actual 骨架：PhotoKit 保存需 NSPhotoLibraryAddUsageDescription +
 * 权限弹窗，K16 接线（真机 K17 验证）。当前返回 false 并记日志（调用方已有失败降级路径）。
 */
actual object GallerySaver {

    actual fun saveImagePng(bytes: ByteArray, displayName: String): Boolean {
        BadgerLog.w(TAG, "saveImagePng: iOS 骨架未接线（K16 PhotoKit）: $displayName")
        return false
    }
}
