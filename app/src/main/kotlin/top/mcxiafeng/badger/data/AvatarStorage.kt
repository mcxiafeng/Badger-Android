package top.mcxiafeng.badger.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.utils.Methods
import java.io.File

/** Owns local avatar persistence so Compose does not perform file IO directly. */
class AvatarStorage(
    private val context: Context,
) {
    suspend fun saveContactAvatar(contactId: Long, bitmap: Bitmap): File =
        withContext(Dispatchers.IO) {
            Methods.saveBitmapAsAvatar(
                context = context,
                bitmap = bitmap,
                fileName = "contact_${contactId}_avatar.webp",
            )
        }
}
