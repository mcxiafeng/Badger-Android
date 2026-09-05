package top.mcxiafeng.badger.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImagePicking.android"

/** [KMP K13c] Android actual：PickVisualMedia（语义与原 rememberLauncherForActivityResult 调用一致）。 */
@Composable
actual fun rememberImagePickerLauncher(onPicked: (ByteArray?) -> Unit): ImagePickerLauncher {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        val context = SpikeContextHolder.appContext
        val bytes = runCatching {
            context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure { BadgerLog.e(TAG, "读取选中图片失败: $uri", it) }.getOrNull()
        onPicked(bytes)
    }
    return object : ImagePickerLauncher {
        override fun launch() {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** [KMP K13c] Android actual：CreateDocument（SAF 另存为）——用户选定位置后写入内容。 */
@Composable
actual fun rememberDocumentSaveLauncher(
    mime: String,
    suggestedName: String,
    onSaved: (Boolean) -> Unit,
): DocumentSaveLauncher {
    // pendingContent 在 launch 与回调之间跨生命周期持有（SAF 选择器可能长时间在前台）
    val pendingContent = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mime)
    ) { uri: Uri? ->
        val content = pendingContent.value
        pendingContent.value = null
        if (uri == null || content == null) {
            onSaved(false)
            return@rememberLauncherForActivityResult
        }
        val context = SpikeContextHolder.appContext
        val ok = runCatching {
            context?.contentResolver?.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        }.onFailure { BadgerLog.e(TAG, "写入导出文件失败: $uri", it) }.getOrDefault(false)
        BadgerLog.d(TAG, "documentSave: uri=$uri ok=$ok (${content.length} chars)")
        onSaved(ok)
    }
    return object : DocumentSaveLauncher {
        override fun launch(content: String) {
            pendingContent.value = content
            launcher.launch(suggestedName)
        }
    }
}

/** [KMP K13c] Android actual：OpenDocument（文本类文档选取）。 */
@Composable
actual fun rememberDocumentPickLauncher(
    mime: String,
    onPicked: (ByteArray?) -> Unit,
): DocumentPickLauncher {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        val context = SpikeContextHolder.appContext
        val bytes = runCatching {
            context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure { BadgerLog.e(TAG, "读取文档失败: $uri", it) }.getOrNull()
        onPicked(bytes)
    }
    return object : DocumentPickLauncher {
        override fun launch() {
            launcher.launch(arrayOf(mime))
        }
    }
}
