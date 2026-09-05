package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.ui.components.BadgerConfirmDialog
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.downloadImage
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddPlatformWindowDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.CollectionPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.ContactDetailAttachFieldDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.ContactDetailPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.FieldDetailDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.PlatformDetailDialog
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.platform.PlatformImage

// ========== 编辑姓名对话框 ==========
@Composable
internal fun ContactDetailEditNameDialog(
    show: Boolean,
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (newName: String) -> Unit,
) {
    var editName by remember(contact) { mutableStateOf(contact.name) }
    BadgerInputDialog(
        show = show,
        title = "编辑姓名",
        value = editName,
        onValueChange = { editName = it },
        label = "姓名",
        confirmText = "保存",
        onConfirm = { value ->
            val newName = value.trim()
            if (newName.isNotBlank()) {
                BadgerLog.d("ContactDetailPage", "Saving new name: $newName for contact ${contact.id}")
                onSave(newName)
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

// ========== 字段删除确认对话框 ==========
@Composable
internal fun ContactDetailFieldDeleteDialog(
    show: Boolean,
    field: PersonFieldDisplay?,
    onDismiss: () -> Unit,
    onDelete: (PersonFieldDisplay) -> Unit,
) {
    if (field == null) return
    BadgerConfirmDialog(
        show = show,
        title = "删除联系方式",
        message = "确定要删除「${field.fieldName}」吗？此操作不可撤销。",
        confirmText = "删除",
        isDestructive = true,
        onConfirm = {
            onDismiss()
            onDelete(field)
        },
        onDismiss = onDismiss,
    )
}

// ========== 编辑字段值对话框 ==========
@Composable
internal fun ContactDetailEditFieldDialog(
    show: Boolean,
    field: PersonFieldDisplay?,
    editFieldValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (field == null) return
    BadgerDialog(
        show = show,
        title = "编辑${field.fieldName}",
        onDismissRequest = onDismiss,
        positiveText = "保存",
        onPositive = {
            val newValue = editFieldValue.trim()
            if (newValue.isNotBlank() && newValue != field.value) {
                onSave(newValue)
            }
            onDismiss()
        },
    ) {
        // 当前值提示
        if (field.value.isNotBlank()) {
            Text(
                text = field.value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        TextField(
            value = editFieldValue,
            onValueChange = onValueChange,
            label = field.fieldName,
            modifier = Modifier.fillMaxWidth()
        )
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
    field: PersonFieldDisplay?,
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
            UserProfile(
                platformsJson = ContactMapper.encodePlatformsMap(platforms.associate { cp ->
                    cp.platformKey to PlatformEntry(
                        value = cp.value,
                        displayName = cp.displayName,
                        jumpLink = cp.jumpLink,
                        originalLink = cp.originalLink,
                        avatarUrl = cp.avatarUrl
                    )
                }),
                updateTime = nowMs(),
            )
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
    sourceFields: List<PersonFieldDisplay>?,
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
    cropSourceImage: PlatformImage?,
    onCropConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show || cropSourceImage == null) return
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        ImageCropDialog(
            image = cropSourceImage,
            cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256),
            onConfirm = onCropConfirm,
            onDismiss = onDismiss
        )
    }
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
    contactWithFields: PersonWithFields?,
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
    selectedField: PersonFieldDisplay?,
    editFieldValue: String,
    selectedPlatformDetail: Pair<String, PlatformEntry>?,
    editingPlatform: Pair<String, PlatformEntry>?,
    cropSourceImage: PlatformImage?,
    syncPlatformInfo: Pair<String, PlatformEntry>?,
    selectedExistingContact: Contact?,
    // 回调
    onDismissFieldDelete: () -> Unit,
    onDeleteField: (PersonFieldDisplay) -> Unit,
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
    onCropConfirm: (ByteArray) -> Unit,
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
        contact = contact ?: Contact(
            id = 0L,
            name = "",
            createTime = nowMs(),
            updateTime = nowMs(),
        ),
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
        cropSourceImage = cropSourceImage,
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
    contactId: Long,
    headers: Map<String, String> = emptyMap()
): String? {
    return try {
        val image = downloadImage(url, headers = headers)
        if (image != null) {
            val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
            val bytes = ImageCodec.encodeWebp(scaled, AVATAR_SAVE_QUALITY)
            val savedPath = bytes?.let { ImageFiles.saveAvatarImage(it, "contact_${contactId}_avatar.webp") }
            if (scaled !== image) scaled.close()
            image.close()
            BadgerLog.d("ContactDetailPage", "Avatar downloaded and saved: $savedPath")
            savedPath
        } else {
            BadgerLog.w("ContactDetailPage", "Failed to download avatar from: $url")
            null
        }
    } catch (e: Exception) {
        BadgerLog.e("ContactDetailPage", "Avatar download/save failed", e)
        null
    }
}

/** 头像落盘 WEBP 压缩质量（对齐原 Methods.AVATAR_QUALITY = 60）。 */
private const val AVATAR_SAVE_QUALITY = 60
