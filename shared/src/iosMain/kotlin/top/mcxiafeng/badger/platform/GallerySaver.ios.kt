package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Photos.PHPhotoLibrary
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GallerySaver.ios"

/**
 * [KMP K13c→K16] iOS actual：PhotoKit PHPhotoLibrary 同步保存 PNG 到相亩。
 *
 * 使用 performChangesAndWait（同步阻塞——与 Android MediaStore insert 语义对齐）。
 * 需 Info.plist NSPhotoLibraryAddUsageDescription 权限文案（K17 已就位）。
 *
 * 注意：PHAssetCreationRequest.creationRequestForAssetFromImageData 的 K/N klib
 * 映射名缺失——当前 changeBlock 为空（不创建 asset），完整实接需 Swift wrapper 或
 * PHAssetCreationRequest 替代 API 探索（K17 真机阶段）。当前仅验证权限链 + API 编译。
 */
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
