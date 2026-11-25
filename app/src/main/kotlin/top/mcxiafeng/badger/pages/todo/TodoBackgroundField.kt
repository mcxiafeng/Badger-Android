package top.mcxiafeng.badger.pages.todo

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.ExifOrientationStrategy
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import top.mcxiafeng.badger.data.todo.TodoBackgroundScale
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.OverlayDropdownPreference
import top.mcxiafeng.badger.ui.glass.TextButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

private const val TAG = "TodoBackgroundFieldTester"
private const val TODO_CARD_WIDTH_PX = 1080
private const val TODO_CARD_HEIGHT_PX = 230
private const val CROP_SOURCE_MAX_EDGE_PX = 2048
internal const val TODO_CARD_ASPECT_RATIO = 1f * TODO_CARD_WIDTH_PX / TODO_CARD_HEIGHT_PX

private val todoCropConfig = CropConfig(
    outputWidth = TODO_CARD_WIDTH_PX,
    outputHeight = TODO_CARD_HEIGHT_PX,
    lockAspectRatio = true,
)
private val todoCropDecoder = BitmapFactoryDecoder.Factory(
    exifOrientationStrategy = ExifOrientationStrategy.RESPECT_ALL,
)

/** The caller owns the cropped bitmap and can release it when persistence finishes. */
@Composable
internal fun TodoBackgroundField(
    file: File?,
    enabled: Boolean,
    scale: TodoBackgroundScale,
    onScaleChange: (TodoBackgroundScale) -> Unit,
    onCropped: (Bitmap) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var cropUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        Log.d(TAG, "Photo picker finished, accepted=${uri != null && enabled}")
        if (enabled && uri != null) cropUri = uri
    }
    TodoBackgroundControls(file, enabled, scale, onScaleChange, onPick = {
        focusManager.clearFocus()
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Photo picker unavailable", e)
            Toast.makeText(context, "无法打开相册", Toast.LENGTH_SHORT).show()
        }
    }, onRemove = onRemove)
    cropUri?.let { uri ->
        TodoCropWindow(uri, onDismiss = {
            Log.d(TAG, "Dismissed background crop")
            cropUri = null
        }, onCropped = { bitmap ->
            cropUri = null
            onCropped(bitmap)
        })
    }
}

@Composable
private fun TodoBackgroundControls(
    file: File?, enabled: Boolean, scale: TodoBackgroundScale, onScaleChange: (TodoBackgroundScale) -> Unit,
    onPick: () -> Unit, onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text("背景图片", style = MiuixTheme.textStyles.subtitle)
        if (file != null) {
            TodoBackgroundPreview(file, scale)
            OverlayDropdownPreference(items = backgroundScaleLabels, selectedIndex = scale.ordinal,
                title = "背景显示方式", enabled = enabled, onSelectedIndexChange = {
                    Log.d(TAG, "Changed task background scale")
                    onScaleChange(TodoBackgroundScale.entries[it])
                })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            TextButton(
                text = if (file == null) "从相册选择" else "更换图片",
                onClick = onPick,
                modifier = Modifier.weight(1f, fill = false),
                enabled = enabled,
                minHeight = BadgerSize.minimumTouchTarget,
            )
            if (file != null) TextButton(
                text = "移除背景",
                onClick = {
                    Log.d(TAG, "Requested background removal")
                    onRemove()
                },
                modifier = Modifier.weight(1f, fill = false),
                enabled = enabled,
                minHeight = BadgerSize.minimumTouchTarget,
            )
        }
    }
}

@Composable
private fun TodoBackgroundPreview(file: File, scale: TodoBackgroundScale) {
    var failed by remember(file) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().aspectRatio(TODO_CARD_ASPECT_RATIO)
        .clip(BadgerShapes.Surface).background(MiuixTheme.colorScheme.surfaceContainer)) {
        TodoCardBackground(file, scale, Modifier.matchParentSize(), "待办背景预览", onError = { failed = true })
        if (failed) Text("无法预览背景图片", modifier = Modifier.align(Alignment.Center),
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

private val backgroundScaleLabels = TodoBackgroundScale.entries.map { it.label }

@Composable
private fun TodoCropWindow(uri: Uri, onDismiss: () -> Unit, onCropped: (Bitmap) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        ImageCropDialog(
            imageUri = uri,
            onConfirm = onCropped,
            onDismiss = onDismiss,
            cropConfig = todoCropConfig,
            bitmapLoader = ::loadTodoCropSource,
        )
    }
}

private suspend fun loadTodoCropSource(context: Context, uri: Uri): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(CROP_SOURCE_MAX_EDGE_PX)
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        .allowHardware(false)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .decoderFactory(todoCropDecoder)
        .build()
    return when (val result = context.imageLoader.execute(request)) {
        is SuccessResult -> result.image.toBitmap().also {
            Log.d(TAG, "Decoded crop source ${it.width}x${it.height}")
        }
        is ErrorResult -> {
            Log.e(TAG, "Photo decode failed, type=${result.throwable.javaClass.simpleName}")
            null
        }
    }
}
