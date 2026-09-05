package top.mcxiafeng.badger.platform

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "GallerySaver"

/** [KMP K13c] Android actual：MediaStore Pictures/Badger（PNG，语义对齐 Methods.saveBitmapToGallery）。 */
actual object GallerySaver {

    actual fun saveImagePng(bytes: ByteArray, displayName: String): Boolean {
        val context = SpikeContextHolder.appContext ?: run {
            BadgerLog.w(TAG, "saveImagePng: appContext 未初始化")
            return false
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Badger")
        }
        val resolver = context.contentResolver
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "saveImagePng insert failed", e)
            return false
        } ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Unable to open gallery output stream")
            BadgerLog.d(TAG, "saveImagePng 成功: $displayName (${bytes.size} bytes)")
            true
        } catch (e: Exception) {
            // 清理半成品 MediaStore 条目，避免相册残留空图
            runCatching { resolver.delete(uri, null, null) }
            BadgerLog.e(TAG, "saveImagePng failed", e)
            false
        }
    }
}
