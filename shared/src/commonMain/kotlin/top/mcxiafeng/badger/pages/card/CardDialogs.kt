package top.mcxiafeng.badger.pages.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity as CardCollection
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.extractDominantColor
import top.mcxiafeng.badger.platform.loadOrientedImage
import top.mcxiafeng.badger.platform.rememberImagePickerLauncher
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.deleteFileQuietly
import top.mcxiafeng.badger.shared.util.nowMs

private const val TAG = "CardDialogs"

/**
 * 名片夹背景图选择器 —— Create / EditCollectionDialog 共用的"选图 + 裁剪 + 上传"组合。
 *
 * 把 crop Dialog、PickVisualMedia launcher、processing 状态、上传后的文件清理都集中在一处。
 * 调用方只关心 onBgChanged(path, color) 回调 —— 选/裁完图后用它把最新值写回自己的 state。
 */
@Composable
private fun CollectionBgPicker(
    bgImagePath: String?,
    dominantColor: Long?,
    onBgChanged: (path: String?, color: Long?) -> Unit,
    contentDescription: String,
) {
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }
    var isProcessingBg by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // [KMP K13c] 选图走平台边界（bytes → EXIF 校正解码 → 裁剪）
    val pickBgLauncher = rememberImagePickerLauncher { bytes ->
        if (bytes != null) {
            scope.launch {
                val image = loadOrientedImage(bytes)
                if (image != null) {
                    cropSourceImage = image
                    showCropDialog = true
                } else {
                    showToast("图片读取失败")
                }
            }
        }
    }

    if (showCropDialog && cropSourceImage != null) {
        Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceImage = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropDialog(
                image = cropSourceImage!!,
                cropConfig = CropConfig(mode = CropMode.COLLECTION_BG, outputWidth = ImageCodec.COLLECTION_BG_SIZE, outputHeight = 0),
                onConfirm = { croppedBytes ->
                    showCropDialog = false
                    cropSourceImage?.close()
                    cropSourceImage = null
                    isProcessingBg = true
                    scope.launch {
                        try {
                            val bgPath = ImageFiles.saveCollectionBackground(
                                croppedBytes,
                                "collection_bg_${nowMs()}.webp"
                            )
                            if (bgPath == null) error("bg save failed")
                            // 主色从裁剪产物采样（落盘前字节 → 解码）
                            val style = ImageCodec.decode(croppedBytes)?.let { img ->
                                try { extractDominantColor(img) } finally { img.close() }
                            }
                            val oldPath = bgImagePath
                            isProcessingBg = false
                            onBgChanged(bgPath, style)
                            if (oldPath != null) deleteFileQuietly(oldPath)
                        } catch (e: Exception) {
                            isProcessingBg = false
                            BadgerLog.e(TAG, "$contentDescription: bg save failed", e)
                            showToast("设置背景图失败")
                        }
                    }
                },
                onDismiss = {
                    showCropDialog = false
                    cropSourceImage?.close()
                    cropSourceImage = null
                }
            )
        }
    }

    Text("背景图片", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Spacer(modifier = Modifier.height(8.dp))
    if (isProcessingBg) {
        Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else if (bgImagePath != null) {
        coil3.compose.AsyncImage(
            model = bgImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "更换图片",
                onClick = { pickBgLauncher.launch() },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "移除背景",
                onClick = {
                    deleteFileQuietly(bgImagePath)
                    onBgChanged(null, null)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    } else {
        TextButton(
            text = "选择图片",
            onClick = { pickBgLauncher.launch() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, backgroundImagePath: String?, dominantColor: Long?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var bgImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var dominantColor by rememberSaveable { mutableStateOf<Long?>(null) }

    WindowDialog(
        show = true,
        title = "新建名片夹",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = description,
            onValueChange = { description = it },
            label = "描述（可选）",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        CollectionBgPicker(
            bgImagePath = bgImagePath,
            dominantColor = dominantColor,
            onBgChanged = { p, c ->
                bgImagePath = p
                dominantColor = c
            },
            contentDescription = "CreateCollectionDialog",
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = "创建",
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), description.trim().ifBlank { null }, bgImagePath, dominantColor)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
fun EditCollectionDialog(
    collection: CardCollection,
    onDismiss: () -> Unit,
    onConfirm: (CardCollection) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(collection.name) }
    var description by rememberSaveable { mutableStateOf(collection.description.orEmpty()) }
    var bgImagePath by rememberSaveable { mutableStateOf(collection.backgroundImagePath) }
    var dominantColor by rememberSaveable { mutableStateOf(collection.dominantColor) }

    WindowDialog(
        show = true,
        title = "编辑名片夹",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = description,
            onValueChange = { description = it },
            label = "描述（可选）",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        CollectionBgPicker(
            bgImagePath = bgImagePath,
            dominantColor = dominantColor,
            onBgChanged = { p, c ->
                bgImagePath = p
                dominantColor = c
            },
            contentDescription = "EditCollectionDialog",
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = "保存",
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(collection.copy(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            backgroundImagePath = bgImagePath,
                            dominantColor = dominantColor
                        ))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
fun ContactSelectDialog(
    searchContacts: (String) -> kotlinx.coroutines.flow.Flow<List<Contact>>,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val available by remember(searchQuery) { searchContacts(searchQuery) }
        .collectAsState(initial = emptyList())

    WindowDialog(
        show = true,
        title = "添加联系人",
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "搜索",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            items(available, key = { it.id }) { contact ->
                BasicComponent(
                    title = contact.name,
                    summary = contact.note,
                    onClick = { onContactSelected(contact) },
                    startAction = {
                        ContactAvatar(name = contact.name, avatarUrl = contact.avatarUrl, size = 36)
                    }
                )
            }
            if (available.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "暂无联系人" else "未找到匹配的联系人",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
