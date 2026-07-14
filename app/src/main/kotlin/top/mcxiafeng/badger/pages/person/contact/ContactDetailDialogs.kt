package top.mcxiafeng.badger.pages.person.contact

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ========== 编辑姓名对话框 ==========
@Composable
internal fun ContactDetailEditNameDialog(
    show: Boolean,
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (newName: String) -> Unit,
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = "编辑姓名",
        summary = "",
        onDismissRequest = onDismiss,
    ) {
        var editName by remember(contact) { mutableStateOf(contact.name) }
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = editName,
                onValueChange = { editName = it },
                label = "姓名",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtonRow(
                positiveText = "保存",
                onNegative = onDismiss,
                onPositive = {
                    val newName = editName.trim()
                    if (newName.isNotBlank()) {
                        Log.d("ContactDetailPage", "Saving new name: $newName for contact ${contact.id}")
                        onSave(newName)
                    }
                    onDismiss()
                }
            )
        }
    }
}

// ========== 字段删除确认对话框 ==========
@Composable
internal fun ContactDetailFieldDeleteDialog(
    show: Boolean,
    field: ContactFieldDisplay?,
    onDismiss: () -> Unit,
    onDelete: (ContactFieldDisplay) -> Unit,
) {
    if (!show || field == null) return
    val currentField = field
    WindowDialog(
        show = true,
        title = "删除联系方式",
        summary = "确定要删除「${currentField.fieldName}」吗？此操作不可撤销。",
        onDismissRequest = onDismiss
    ) {
        DialogButtonRow(
            positiveText = "删除",
            onNegative = onDismiss,
            onPositive = {
                onDismiss()
                onDelete(currentField)
            },
            isDestructive = true
        )
    }
}

// ========== 编辑字段值对话框 ==========
@Composable
internal fun ContactDetailEditFieldDialog(
    show: Boolean,
    field: ContactFieldDisplay?,
    editFieldValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (!show || field == null) return
    WindowDialog(
        show = true,
        title = "编辑${field.fieldName}",
        summary = field.value,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            TextField(
                value = editFieldValue,
                onValueChange = onValueChange,
                label = field.fieldName,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            DialogButtonRow(
                positiveText = "保存",
                onNegative = onDismiss,
                onPositive = {
                    val newValue = editFieldValue.trim()
                    if (newValue.isNotBlank() && newValue != field.value) {
                        onSave(newValue)
                    }
                    onDismiss()
                }
            )
        }
    }
}

// ========== 同步选项底部弹窗包装 ==========
@Composable
internal fun ContactDetailSyncOptionsSheet(
    show: Boolean,
    platformInfo: Pair<String, PlatformEntry>?,
    onDismiss: () -> Unit,
    onConfirm: (syncName: Boolean, syncAvatar: Boolean) -> Unit,
) {
    if (!show || platformInfo == null) return
    SyncOptionsBottomSheet(
        platformInfo = platformInfo,
        currentProfile = null,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

// ========== 联系方式详情弹窗包装 ==========
@Composable
internal fun ContactDetailFieldDetailDialog(
    show: Boolean,
    field: ContactFieldDisplay?,
    onDismiss: () -> Unit,
) {
    if (!show || field == null) return
    FieldDetailDialog(
        field = field,
        show = true,
        onDismiss = onDismiss
    )
}

// ========== 社交平台详情弹窗包装 ==========
@Composable
internal fun ContactDetailPlatformDetailDialog(
    show: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    onDismiss: () -> Unit,
) {
    if (!show || selectedPlatform == null) return
    PlatformDetailDialog(
        show = true,
        platformName = selectedPlatform.first,
        entry = selectedPlatform.second,
        onDismiss = onDismiss
    )
}

// ========== 添加平台对话框包装 ==========
@Composable
internal fun ContactDetailAddPlatformDialog(
    show: Boolean,
    platformData: List<ContactPlatform>,
    mode: AddEditMode,
    onDismiss: () -> Unit,
    onConfirm: (fieldKey: String, entry: PlatformEntry) -> Unit,
) {
    if (!show) return
    AddPlatformWindowDialog(
        show = true,
        mode = mode,
        existingProfile = platformData.takeIf { it.isNotEmpty() }?.let { platforms ->
            UserProfile(platforms = platforms.associate { cp ->
                cp.platformKey to PlatformEntry(
                    value = cp.value,
                    displayName = cp.displayName,
                    jumpLink = cp.jumpLink,
                    originalLink = cp.originalLink,
                    avatarUrl = cp.avatarUrl
                )
            })
        },
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

// ========== 编辑平台对话框包装 ==========
@Composable
internal fun ContactDetailEditPlatformDialog(
    show: Boolean,
    editingEntry: Pair<String, PlatformEntry>?,
    onDismiss: () -> Unit,
    onConfirm: (fieldKey: String, entry: PlatformEntry) -> Unit,
) {
    if (!show || editingEntry == null) return
    AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.EDIT,
        editingEntry = editingEntry,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

// ========== 添加至名片夹弹窗包装 ==========
@Composable
internal fun ContactDetailCollectionPickerDialog(
    show: Boolean,
    collectionRepository: top.mcxiafeng.badger.data.repository.CollectionRepository,
    contactId: Long,
    currentCollectionIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (addedIds: Set<Long>, removedIds: Set<Long>) -> Unit,
) {
    if (!show) return
    CollectionPickerDialog(
        collectionRepository = collectionRepository,
        contactId = contactId,
        currentCollectionIds = currentCollectionIds,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

// ========== 联系人选择器包装 ==========
@Composable
internal fun ShowContactDetailPicker(
    show: Boolean,
    repository: top.mcxiafeng.badger.data.repository.ContactRepository,
    excludeContactId: Long,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit,
) {
    if (!show) return
    ContactDetailPickerDialog(
        repository = repository,
        excludeContactId = excludeContactId,
        onDismiss = onDismiss,
        onContactSelected = onContactSelected
    )
}

// ========== 附加到已有联系人对话框包装 ==========
@Composable
internal fun ContactDetailAttachFieldDialogWrapper(
    show: Boolean,
    sourceContact: Contact?,
    sourceFields: List<ContactFieldDisplay>?,
    existingContact: Contact?,
    repository: top.mcxiafeng.badger.data.repository.ContactRepository,
    onDismiss: () -> Unit,
    onConfirm: (selectedFieldKeys: List<String>, selectedCustomFieldIds: List<Long>) -> Unit,
) {
    if (!show || existingContact == null || sourceContact == null || sourceFields == null) return
    ContactDetailAttachFieldDialog(
        sourceContact = sourceContact,
        sourceFields = sourceFields,
        existingContact = existingContact,
        repository = repository,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

// ========== 裁剪对话框包装 ==========
@Composable
internal fun ContactDetailCropDialog(
    show: Boolean,
    cropSourceUri: Uri?,
    onCropConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show || cropSourceUri == null) return
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        ImageCropDialog(
            imageUri = cropSourceUri,
            cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256),
            onConfirm = onCropConfirm,
            onDismiss = onDismiss
        )
    }
}

// ========== 辅助函数 ==========

private fun sourceTypeDisplayName(sourceType: String): String = when (sourceType) {
    "scan" -> "扫码"
    "photo" -> "拍照"
    "manual" -> "手动添加"
    "import" -> "导入"
    else -> sourceType
}

// ========== 对话框包装器（提取自 ContactDetailPage） ==========

/**
 * 联系人详情页所有对话框的包装器。提取自 ContactDetailPage 以减少单文件体积。
 */
@Composable
internal fun ContactDetailPageDialogs(
    contactId: Long,
    viewModel: ContactDetailViewModel,
    contact: Contact?,
    contactWithFields: ContactWithFields?,
    platformData: List<ContactPlatform>,
    contactCollectionIds: Set<Long>,
    // 对话框显示状态
    showFieldDeleteDialog: Boolean,
    showEditFieldDialog: Boolean,
    showEditNameDialog: Boolean,
    showFieldDetailDialog: Boolean,
    showPlatformDetailDialog: Boolean,
    showAddPlatformDialog: Boolean,
    showEditPlatformDialog: Boolean,
    showCollectionPicker: Boolean,
    showContactPicker: Boolean,
    showCropDialog: Boolean,
    showSyncOptionsSheet: Boolean,
    // 对话框数据
    selectedField: ContactFieldDisplay?,
    editFieldValue: String,
    selectedPlatformDetail: Pair<String, PlatformEntry>?,
    editingPlatform: Pair<String, PlatformEntry>?,
    cropSourceUri: Uri?,
    syncPlatformInfo: Pair<String, PlatformEntry>?,
    selectedExistingContact: Contact?,
    // 回调
    onDismissFieldDelete: () -> Unit,
    onDeleteField: (ContactFieldDisplay) -> Unit,
    onEditFieldValueChange: (String) -> Unit,
    onDismissEditField: () -> Unit,
    onSaveEditField: (String) -> Unit,
    onDismissEditName: () -> Unit,
    onSaveEditName: (String) -> Unit,
    onDismissFieldDetail: () -> Unit,
    onDismissPlatformDetail: () -> Unit,
    onDismissAddPlatform: () -> Unit,
    onConfirmAddPlatform: (String, PlatformEntry) -> Unit,
    onDismissEditPlatform: () -> Unit,
    onConfirmEditPlatform: (String, PlatformEntry) -> Unit,
    onDismissCollectionPicker: () -> Unit,
    onConfirmCollectionPicker: (Set<Long>, Set<Long>) -> Unit,
    onDismissContactPicker: () -> Unit,
    onContactSelected: (Contact) -> Unit,
    onDismissAttachField: () -> Unit,
    onConfirmAttachField: (List<String>, List<Long>) -> Unit,
    onCropConfirm: (Bitmap) -> Unit,
    onDismissCrop: () -> Unit,
    onDismissSync: () -> Unit,
    onConfirmSync: (Boolean, Boolean) -> Unit,
) {
    // 字段删除确认对话框
    ContactDetailFieldDeleteDialog(
        show = showFieldDeleteDialog,
        field = selectedField,
        onDismiss = onDismissFieldDelete,
        onDelete = onDeleteField,
    )

    // 编辑字段值对话框
    ContactDetailEditFieldDialog(
        show = showEditFieldDialog,
        field = selectedField,
        editFieldValue = editFieldValue,
        onValueChange = onEditFieldValueChange,
        onDismiss = onDismissEditField,
        onSave = onSaveEditField,
    )

    // 编辑姓名对话框
    ContactDetailEditNameDialog(
        show = showEditNameDialog,
        contact = contact ?: Contact(name = ""),
        onDismiss = onDismissEditName,
        onSave = onSaveEditName,
    )

    // 联系方式详情弹窗
    ContactDetailFieldDetailDialog(
        show = showFieldDetailDialog,
        field = selectedField,
        onDismiss = onDismissFieldDetail,
    )

    // 社交平台详情弹窗
    ContactDetailPlatformDetailDialog(
        show = showPlatformDetailDialog,
        selectedPlatform = selectedPlatformDetail,
        onDismiss = onDismissPlatformDetail,
    )

    // 添加社交平台对话框
    ContactDetailAddPlatformDialog(
        show = showAddPlatformDialog,
        platformData = platformData,
        mode = AddEditMode.ADD,
        onDismiss = onDismissAddPlatform,
        onConfirm = onConfirmAddPlatform,
    )

    // 编辑平台对话框
    ContactDetailEditPlatformDialog(
        show = showEditPlatformDialog,
        editingEntry = editingPlatform,
        onDismiss = onDismissEditPlatform,
        onConfirm = onConfirmEditPlatform,
    )

    // 添加到名片夹弹窗
    ContactDetailCollectionPickerDialog(
        show = showCollectionPicker,
        collectionRepository = viewModel.collectionRepository,
        contactId = contactId,
        currentCollectionIds = contactCollectionIds,
        onDismiss = onDismissCollectionPicker,
        onConfirm = onConfirmCollectionPicker,
    )

    // 附加到已有联系人：联系人选择器
    ShowContactDetailPicker(
        show = showContactPicker,
        repository = viewModel.repository,
        excludeContactId = contactId,
        onDismiss = onDismissContactPicker,
        onContactSelected = onContactSelected,
    )

    // 附加到已有联系人：字段附加确认
    ContactDetailAttachFieldDialogWrapper(
        show = selectedExistingContact != null && contactWithFields != null,
        sourceContact = contactWithFields?.contact,
        sourceFields = contactWithFields?.fieldValues,
        existingContact = selectedExistingContact,
        repository = viewModel.repository,
        onDismiss = onDismissAttachField,
        onConfirm = onConfirmAttachField,
    )

    // 头像裁剪对话框
    ContactDetailCropDialog(
        show = showCropDialog,
        cropSourceUri = cropSourceUri,
        onCropConfirm = onCropConfirm,
        onDismiss = onDismissCrop,
    )

    // 同步选项底部弹窗
    ContactDetailSyncOptionsSheet(
        show = showSyncOptionsSheet,
        platformInfo = syncPlatformInfo,
        onDismiss = onDismissSync,
        onConfirm = onConfirmSync,
    )
}

/**
 * 下载头像图片并保存到本地文件。提取自 ContactDetailPage。
 * @return 保存成功返回文件绝对路径，失败返回 null
 */
internal suspend fun downloadAndSaveAvatar(
    url: String,
    context: Context,
    contactId: Long,
    headers: Map<String, String>? = null
): String? {
    return try {
        val bitmap = HttpUtil.downloadBitmap(url, headers = headers)
        if (bitmap != null) {
            val avatarFile = Methods.saveBitmapAsAvatar(context, bitmap, "contact_${contactId}_avatar.webp")
            Log.d("ContactDetailPage", "Avatar downloaded and saved: ${avatarFile.absolutePath}")
            avatarFile.absolutePath
        } else {
            Log.w("ContactDetailPage", "Failed to download avatar from: $url")
            null
        }
    } catch (e: Exception) {
        Log.e("ContactDetailPage", "Avatar download/save failed", e)
        null
    }
}
