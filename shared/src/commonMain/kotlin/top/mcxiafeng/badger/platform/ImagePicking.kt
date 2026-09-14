package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable

interface ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onPicked: (ByteArray?) -> Unit): ImagePickerLauncher

interface DocumentSaveLauncher {
    fun launch(content: String)
}

@Composable
expect fun rememberDocumentSaveLauncher(
    mime: String,
    suggestedName: String,
    onSaved: (Boolean) -> Unit,
): DocumentSaveLauncher

interface DocumentPickLauncher {
    fun launch()
}

@Composable
expect fun rememberDocumentPickLauncher(
    mime: String,
    onPicked: (ByteArray?) -> Unit,
): DocumentPickLauncher
