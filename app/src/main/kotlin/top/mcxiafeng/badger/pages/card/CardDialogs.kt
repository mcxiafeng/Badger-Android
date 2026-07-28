package top.mcxiafeng.badger.pages.card

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.extractDominantColor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, backgroundImagePath: String?, dominantColor: Long?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var bgImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var dominantColor by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingBg by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cropSourceUri = uri
            showCropDialog = true
        }
    }

    if (showCropDialog && cropSourceUri != null) {
        Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropDialog(
                imageUri = cropSourceUri!!,
                cropConfig = CropConfig(mode = CropMode.COLLECTION_BG, outputWidth = 1080, outputHeight = 0),
                onConfirm = { croppedBitmap ->
                    showCropDialog = false
                    cropSourceUri = null
                    isProcessingBg = true
                    scope.launch {
                        try {
                            val bgFile = Methods.saveBitmapAsCollectionBg(context, croppedBitmap, "collection_bg_${System.currentTimeMillis()}.webp")
                            val style = extractDominantColor(croppedBitmap)
                            val oldPath = bgImagePath
                            bgImagePath = bgFile.absolutePath
                            dominantColor = style?.themeColor
                            isProcessingBg = false
                            if (oldPath != null) Methods.deleteFileIfExists(oldPath)
                            Log.d("Tester", "CreateCollectionDialog: bg saved to ${bgFile.absolutePath}, dominantColor=${style?.themeColor}")
                        } catch (e: Exception) {
                            isProcessingBg = false
                            Log.e("Tester", "CreateCollectionDialog: bg save failed", e)
                            Toast.makeText(context, "设置背景图失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showCropDialog = false; cropSourceUri = null }
            )
        }
    }

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

        // 背景图选择器
        Text("背景图片", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        if (isProcessingBg) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (bgImagePath != null) {
            var bgPreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(bgImagePath) {
                bgPreview = Methods.loadBackgroundBitmap(bgImagePath) }
            if (bgPreview != null) {
                Image(
                    bitmap = bgPreview!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "更换图片",
                    onClick = { pickBgLauncher.launch(androidx.activity.result.PickVisualMediaRequest()) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "移除背景",
                    onClick = {
                        Methods.deleteFileIfExists(bgImagePath)
                        bgImagePath = null
                        dominantColor = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        } else {
            TextButton(
                text = "选择图片",
                onClick = { pickBgLauncher.launch(androidx.activity.result.PickVisualMediaRequest()) },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingBg by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cropSourceUri = uri
            showCropDialog = true
        }
    }

    if (showCropDialog && cropSourceUri != null) {
        Dialog(
            onDismissRequest = { showCropDialog = false; cropSourceUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropDialog(
                imageUri = cropSourceUri!!,
                cropConfig = CropConfig(mode = CropMode.COLLECTION_BG, outputWidth = 1080, outputHeight = 0),
                onConfirm = { croppedBitmap ->
                    showCropDialog = false
                    cropSourceUri = null
                    isProcessingBg = true
                    scope.launch {
                        try {
                            val bgFile = Methods.saveBitmapAsCollectionBg(context, croppedBitmap, "collection_bg_${System.currentTimeMillis()}.webp")
                            val style = extractDominantColor(croppedBitmap)
                            val oldPath = bgImagePath
                            bgImagePath = bgFile.absolutePath
                            dominantColor = style?.themeColor
                            isProcessingBg = false
                            if (oldPath != null) Methods.deleteFileIfExists(oldPath)
                            Log.d("Tester", "EditCollectionDialog: bg saved to ${bgFile.absolutePath}, dominantColor=${style?.themeColor}")
                        } catch (e: Exception) {
                            isProcessingBg = false
                            Log.e("Tester", "EditCollectionDialog: bg save failed", e)
                            Toast.makeText(context, "设置背景图失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showCropDialog = false; cropSourceUri = null }
            )
        }
    }

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

        // 背景图选择器
        Text("背景图片", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        if (isProcessingBg) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (bgImagePath != null) {
            var bgPreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(bgImagePath) {
                bgPreview = Methods.loadBackgroundBitmap(bgImagePath) }
            if (bgPreview != null) {
                Image(
                    bitmap = bgPreview!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "更换图片",
                    onClick = { pickBgLauncher.launch(androidx.activity.result.PickVisualMediaRequest()) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "移除背景",
                    onClick = {
                        Methods.deleteFileIfExists(bgImagePath)
                        bgImagePath = null
                        dominantColor = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        } else {
            TextButton(
                text = "选择图片",
                onClick = { pickBgLauncher.launch(androidx.activity.result.PickVisualMediaRequest()) },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
