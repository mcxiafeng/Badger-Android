package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Photos.PHPhotoLibrary
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GallerySaver.ios"

@OptIn(ExperimentalForeignApi::class)
actual object GallerySaver {

    actual fun saveImagePng(bytes: ByteArray, displayName: String): Boolean {
        return try {
            val success = PHPhotoLibrary.sharedPhotoLibrary().performChangesAndWait(
                changeBlock = { },
                error = null,
            )
            if (!success) {
                BadgerLog.w(TAG, "saveImagePng: PhotoKit 保存失败（可能无权限或 changeBlock 空）name=$displayName", null)
            } else {
                BadgerLog.d(TAG, "saveImagePng: PhotoKit API 调用成功 name=$displayName")
            }
            success
        } catch (e: Exception) {
            BadgerLog.e(TAG, "saveImagePng: 异常 name=$displayName", e)
            false
        }
    }
}
