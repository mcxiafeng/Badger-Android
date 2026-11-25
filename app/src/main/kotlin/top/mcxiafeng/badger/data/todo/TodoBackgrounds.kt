package top.mcxiafeng.badger.data.todo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.atomicWrite

internal class TodoBackgrounds(private val context: Context) {
    fun file(id: String, staged: Boolean): File? {
        if (!imageId.matches(id)) return null
        val directory = if (staged) context.cacheDir else context.filesDir
        return File(directory, "todo_background_$id.webp")
    }

    fun stage(bitmap: Bitmap): String {
        val id = UUID.randomUUID().toString()
        val destination = checkNotNull(file(id, staged = true))
        // Methods writes to filesDir, so use the account's cache path for the unsaved image.
        atomicWrite(destination) { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, Methods.COLLECTION_BG_QUALITY, output))
        }
        check(destination.length() > 0L) { "Could not save cropped image" }
        Log.d(TAG, "Staged cropped task background")
        return id
    }

    fun promote(id: String): File {
        val source = checkNotNull(file(id, staged = true))
        val destination = checkNotNull(file(id, staged = false))
        if (!source.isFile || source.length() == 0L) {
            Log.d(TAG, "Staged task background no longer available")
            throw TodoBackgroundMissingException()
        }
        atomicWrite(destination) { output -> source.inputStream().use { it.copyTo(output) } }
        if (destination.length() != source.length()) throw IOException("Could not save task background")
        Log.d(TAG, "Persisted cropped task background")
        return destination
    }

    fun remove(file: File?) {
        if (file == null) return
        try {
            if (file.delete()) Log.d(TAG, "Removed unused task background")
            else if (file.exists()) Log.w(TAG, "Could not remove unused task background")
        } catch (error: SecurityException) {
            Log.e(TAG, "Could not remove unused task background", error)
        }
    }

    private companion object {
        const val TAG = "TodoBackgroundsTester"
        val imageId = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
    }
}

internal class TodoBackgroundMissingException : IOException("裁剪图片已失效，请重新选择背景图")
