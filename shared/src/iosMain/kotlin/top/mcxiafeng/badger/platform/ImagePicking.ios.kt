package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImagePicking.ios"

/**
 * [KMP K13c] iOS actual 骨架：
 * - 图片选择 = PHPickerViewController（K16：presentUIViewController 接线，回调转 UIImage→PNG 字节）
 * - 文档另存 = UIDocumentPickerViewController（export mode）或 UIActivityViewController（K16）
 * - 文档选取 = UIDocumentPickerViewController（import mode，K16）
 * 当前 launch() 记日志即返回（调用方已有空值降级路径）。
 */
@Composable
actual fun rememberImagePickerLauncher(onPicked: (ByteArray?) -> Unit): ImagePickerLauncher {
    return object : ImagePickerLauncher {
        override fun launch() {
            BadgerLog.w(TAG, "rememberImagePickerLauncher: iOS 骨架未接线（K16 PHPicker）")
        }
    }
}

@Composable
actual fun rememberDocumentSaveLauncher(
    mime: String,
    suggestedName: String,
    onSaved: (Boolean) -> Unit,
): DocumentSaveLauncher {
    return object : DocumentSaveLauncher {
        override fun launch(content: String) {
            BadgerLog.w(TAG, "rememberDocumentSaveLauncher: iOS 骨架未接线（K16）: $suggestedName")
        }
    }
}

@Composable
actual fun rememberDocumentPickLauncher(
    mime: String,
    onPicked: (ByteArray?) -> Unit,
): DocumentPickLauncher {
    return object : DocumentPickLauncher {
        override fun launch() {
            BadgerLog.w(TAG, "rememberDocumentPickLauncher: iOS 骨架未接线（K16）")
        }
    }
}
