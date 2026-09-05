package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable

/**
 * [KMP K13c] 图片选择边界（替代 ActivityResult PickVisualMedia 直用）。
 *
 * 回调返回 PNG/原始字节流；调用方经 [ImageCodec.decode] 转 [PlatformImage] 走裁剪/落盘。
 * Android actual = PickVisualMedia + contentResolver 读字节；
 * iOS actual = PHPicker 骨架（K16 接线）。
 */
interface ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onPicked: (ByteArray?) -> Unit): ImagePickerLauncher

/**
 * [KMP K13c] 文档另存为边界（名片夹导出 JSON）。
 * Android actual = CreateDocument（SAF）；iOS actual = 分享面板/文件导出骨架（K16）。
 */
interface DocumentSaveLauncher {
    fun launch(content: String)
}

@Composable
expect fun rememberDocumentSaveLauncher(
    mime: String,
    suggestedName: String,
    onSaved: (Boolean) -> Unit,
): DocumentSaveLauncher

/**
 * [KMP K13c] 文档选取边界（备份/QAuxv 导入）。
 * Android actual = OpenDocument；iOS actual = UIDocumentPicker 骨架（K16）。
 */
interface DocumentPickLauncher {
    fun launch()
}

@Composable
expect fun rememberDocumentPickLauncher(
    mime: String,
    onPicked: (ByteArray?) -> Unit,
): DocumentPickLauncher
