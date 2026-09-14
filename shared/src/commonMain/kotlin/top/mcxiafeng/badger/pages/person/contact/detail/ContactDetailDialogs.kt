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

@Composable
internal fun ContactDetailAttachFieldDialogWrapper(
    show: Boolean,
    sourceContact: Contact?,
    sourceFields: List<PersonFieldDisplay>?,
    existingContact: Contact?,
    repository: top.mcxiafeng.badger.data.repository.ContactRepository,
    onDismiss: () -> Unit,
    onConfirm: (selectedFieldKeys: List<String>, selectedCustomFieldIds: List<Long>, avatarChecked: Boolean) -> Unit,
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

@Composable
internal fun ContactDetailPageDialogs(
    contactId: Long,
    viewModel: ContactDetailViewModel,
    contact: Contact?,
    contactWithFields: PersonWithFields?,
    platformData: List<ContactPlatform>,
    contactCollectionIds: Set<Long>,
    
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
    
    selectedField: PersonFieldDisplay?,
    editFieldValue: String,
    selectedPlatformDetail: Pair<String, PlatformEntry>?,
    editingPlatform: Pair<String, PlatformEntry>?,
    cropSourceImage: PlatformImage?,
    syncPlatformInfo: Pair<String, PlatformEntry>?,
    selectedExistingContact: Contact?,
    
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
    onConfirmAttachField: (List<String>, List<Long>, Boolean) -> Unit,
    onCropConfirm: (ByteArray) -> Unit,
    onDismissCrop: () -> Unit,
    onDismissSync: () -> Unit,
    onConfirmSync: (Boolean, Boolean) -> Unit,
) {
    
    ContactDetailFieldDeleteDialog(
        show = showFieldDeleteDialog,
        field = selectedField,
        onDismiss = onDismissFieldDelete,
        onDelete = onDeleteField,
    )

    
    ContactDetailEditFieldDialog(
        show = showEditFieldDialog,
        field = selectedField,
        editFieldValue = editFieldValue,
        onValueChange = onEditFieldValueChange,
        onDismiss = onDismissEditField,
        onSave = onSaveEditField,
    )

    
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

    
    ContactDetailFieldDetailDialog(
        show = showFieldDetailDialog,
        field = selectedField,
        onDismiss = onDismissFieldDetail,
    )

    
    ContactDetailPlatformDetailDialog(
        show = showPlatformDetailDialog,
        selectedPlatform = selectedPlatformDetail,
        onDismiss = onDismissPlatformDetail,
    )

    
    ContactDetailAddPlatformDialog(
        show = showAddPlatformDialog,
        platformData = platformData,
        mode = AddEditMode.ADD,
        onDismiss = onDismissAddPlatform,
        onConfirm = onConfirmAddPlatform,
    )

    
    ContactDetailEditPlatformDialog(
        show = showEditPlatformDialog,
        editingEntry = editingPlatform,
        onDismiss = onDismissEditPlatform,
        onConfirm = onConfirmEditPlatform,
    )

    
    ContactDetailCollectionPickerDialog(
        show = showCollectionPicker,
        collectionRepository = viewModel.collectionRepository,
        contactId = contactId,
        currentCollectionIds = contactCollectionIds,
        onDismiss = onDismissCollectionPicker,
        onConfirm = onConfirmCollectionPicker,
    )

    
    ShowContactDetailPicker(
        show = showContactPicker,
        repository = viewModel.repository,
        excludeContactId = contactId,
        onDismiss = onDismissContactPicker,
        onContactSelected = onContactSelected,
    )

    
    ContactDetailAttachFieldDialogWrapper(
        show = selectedExistingContact != null && contactWithFields != null,
        sourceContact = contactWithFields?.contact,
        sourceFields = contactWithFields?.fieldValues,
        existingContact = selectedExistingContact,
        repository = viewModel.repository,
        onDismiss = onDismissAttachField,
        onConfirm = onConfirmAttachField,
    )

    
    ContactDetailCropDialog(
        show = showCropDialog,
        cropSourceImage = cropSourceImage,
        onCropConfirm = onCropConfirm,
        onDismiss = onDismissCrop,
    )

    
    ContactDetailSyncOptionsSheet(
        show = showSyncOptionsSheet,
        platformInfo = syncPlatformInfo,
        onDismiss = onDismissSync,
        onConfirm = onConfirmSync,
    )
}

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

private const val AVATAR_SAVE_QUALITY = 60
