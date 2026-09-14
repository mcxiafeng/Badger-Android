package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImagePicking.ios"

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
