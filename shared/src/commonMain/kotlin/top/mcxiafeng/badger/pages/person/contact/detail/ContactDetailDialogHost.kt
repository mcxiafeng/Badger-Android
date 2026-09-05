package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity as ContactPlatform
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.model.PersonWithFields
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.BILIBILI_HEADERS
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.pages.person.contact.dialogs.BatchImportPlatformsDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.BirthdayPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.CountryPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.GenderPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.RegionPickerDialog
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.GallerySaver
import top.mcxiafeng.badger.platform.downloadImageAsPng
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 联系人详情页对话框宿主。
 *
 * 将所有对话框调用及其回调从 [ContactDetailPage] 中提取，保持主页面 Scaffold 部分简洁。
 * 纯粹的位置搬迁，无任何行为变更。
 */
@Composable
internal fun ContactDetailDialogHost(
    contactId: Long,
    viewModel: ContactDetailViewModel,
    contact: Contact?,
    contactWithFields: PersonWithFields?,
    platformData: List<ContactPlatform>,
    contactCollectionIds: Set<Long>,
    tags: List<Tag>,
    // AI 标签推荐
    aiTagCandidates: List<AiTagGenerator.TagCandidate>,
    aiTagLoading: Boolean,
    aiTagError: String?,
    // 基础信息编辑状态
    basicInfoEditField: String?,
    basicInfoEditCurrent: String?,
    currentCountryName: String?,
    currentCountryExternalId: Long?,
    onBasicInfoEditFieldChange: (String?) -> Unit,
    onCurrentCountryNameChange: (String?) -> Unit,
    onCurrentCountryExternalIdChange: (Long?) -> Unit,
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
    showBatchImportDialog: Boolean,
    showBioEdit: Boolean,
    showTagPicker: Boolean,
    showTagManager: Boolean,
    showAiTagPreview: Boolean,
    showAvatarPreview: Boolean,
    // 对话框数据
    selectedField: PersonFieldDisplay?,
    editFieldValue: String,
    selectedPlatformDetail: Pair<String, PlatformEntry>?,
    editingPlatform: Pair<String, PlatformEntry>?,
    cropSourceImage: PlatformImage?,
    syncPlatformInfo: Pair<String, PlatformEntry>?,
    selectedExistingContact: Contact?,
    avatarImageBitmap: ImageBitmap?,
    avatarVersion: Int,
    isSettingAvatar: Boolean,
    // 状态变更回调
    onShowFieldDeleteDialogChange: (Boolean) -> Unit,
    onShowEditFieldDialogChange: (Boolean) -> Unit,
    onShowEditNameDialogChange: (Boolean) -> Unit,
    onShowFieldDetailDialogChange: (Boolean) -> Unit,
    onShowPlatformDetailDialogChange: (Boolean) -> Unit,
    onShowAddPlatformDialogChange: (Boolean) -> Unit,
    onShowEditPlatformDialogChange: (Boolean) -> Unit,
    onShowCollectionPickerChange: (Boolean) -> Unit,
    onShowContactPickerChange: (Boolean) -> Unit,
    onShowCropDialogChange: (Boolean) -> Unit,
    onShowSyncOptionsSheetChange: (Boolean) -> Unit,
    onSelectedFieldChange: (PersonFieldDisplay?) -> Unit,
    onEditFieldValueChange: (String) -> Unit,
    onSelectedPlatformDetailChange: (Pair<String, PlatformEntry>?) -> Unit,
    onEditingPlatformChange: (Pair<String, PlatformEntry>?) -> Unit,
    onSyncPlatformInfoChange: (Pair<String, PlatformEntry>?) -> Unit,
    onCropSourceImageChange: (PlatformImage?) -> Unit,
    onSelectedExistingContactChange: (Contact?) -> Unit,
    onShowBatchImportDialogChange: (Boolean) -> Unit,
    onShowBioEditChange: (Boolean) -> Unit,
    onShowTagPickerChange: (Boolean) -> Unit,
    onShowTagManagerChange: (Boolean) -> Unit,
    onShowAiTagPreviewChange: (Boolean) -> Unit,
    onShowAvatarPreviewChange: (Boolean) -> Unit,
    onAvatarVersionIncrement: () -> Unit,
    onIsSettingAvatarChange: (Boolean) -> Unit,
    // 动作回调
    onCropConfirm: (ByteArray) -> Unit,
    onPickNewAvatar: () -> Unit,
    onRefreshData: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()

    // PR2 fix:基础信息编辑 Dialog(性别 / 生日 / 国家 / 地区)
    GenderPickerDialog(
        show = basicInfoEditField == "gender",
        current = basicInfoEditCurrent,
        onDismiss = { onBasicInfoEditFieldChange(null) },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "gender", value)
            onBasicInfoEditFieldChange(null)
        },
    )
    BirthdayPickerDialog(
        show = basicInfoEditField == "birthday",
        current = basicInfoEditCurrent,
        onDismiss = { onBasicInfoEditFieldChange(null) },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "birthday", value)
            onBasicInfoEditFieldChange(null)
        },
    )
    CountryPickerDialog(
        show = basicInfoEditField == "country",
        current = basicInfoEditCurrent,
        onDismiss = { onBasicInfoEditFieldChange(null) },
        onConfirm = { name, externalId ->
            viewModel.updateBasicInfoField(contactId, "country", name)
            onCurrentCountryNameChange(name)
            onCurrentCountryExternalIdChange(externalId)
            // 选中国家同时清空地区(避免地区不匹配新国家)
            viewModel.updateBasicInfoField(contactId, "region", "")
            onBasicInfoEditFieldChange(null)
        },
    )
    RegionPickerDialog(
        show = basicInfoEditField == "region",
        current = basicInfoEditCurrent,
        countryId = currentCountryExternalId,
        countryName = currentCountryName,
        onDismiss = { onBasicInfoEditFieldChange(null) },
        onConfirm = { value ->
            viewModel.updateBasicInfoField(contactId, "region", value)
            onBasicInfoEditFieldChange(null)
        },
    )

    ContactDetailPageDialogs(
        contactId = contactId,
        viewModel = viewModel,
        contact = contact,
        contactWithFields = contactWithFields,
        platformData = platformData,
        contactCollectionIds = contactCollectionIds,
        // 对话框显示状态
        showFieldDeleteDialog = showFieldDeleteDialog,
        showEditFieldDialog = showEditFieldDialog,
        showEditNameDialog = showEditNameDialog,
        showFieldDetailDialog = showFieldDetailDialog,
        showPlatformDetailDialog = showPlatformDetailDialog,
        showAddPlatformDialog = showAddPlatformDialog,
        showEditPlatformDialog = showEditPlatformDialog,
        showCollectionPicker = showCollectionPicker,
        showContactPicker = showContactPicker,
        showCropDialog = showCropDialog,
        showSyncOptionsSheet = showSyncOptionsSheet,
        // 对话框数据
        selectedField = selectedField,
        editFieldValue = editFieldValue,
        selectedPlatformDetail = selectedPlatformDetail,
        editingPlatform = editingPlatform,
        cropSourceImage = cropSourceImage,
        syncPlatformInfo = syncPlatformInfo,
        selectedExistingContact = selectedExistingContact,
        // 回调
        onDismissFieldDelete = { onShowFieldDeleteDialogChange(false); onSelectedFieldChange(null) },
        onDeleteField = { field ->
            viewModel.deleteFieldAndReload(contactId, field.valueId)
        },
        onEditFieldValueChange = onEditFieldValueChange,
        onDismissEditField = { onShowEditFieldDialogChange(false) },
        onSaveEditField = { newValue ->
            selectedField?.valueId?.let { fid ->
                viewModel.updateFieldAndReload(contactId, fid, newValue)
            }
            onSelectedFieldChange(null)
        },
        onDismissEditName = { onShowEditNameDialogChange(false) },
        onSaveEditName = { newName ->
            BadgerLog.d("ContactDetailPage", "Saving new name: $newName for contact ${contact?.id}")
            viewModel.updateName(contactId, newName)
        },
        onDismissFieldDetail = { onShowFieldDetailDialogChange(false); onSelectedFieldChange(null) },
        onDismissPlatformDetail = { onShowPlatformDetailDialogChange(false); onSelectedPlatformDetailChange(null) },
        onDismissAddPlatform = { onShowAddPlatformDialogChange(false) },
        onConfirmAddPlatform = { fieldKey, entry ->
            onShowAddPlatformDialogChange(false)
            scope.launch(BadgerDispatchers.io) {
                viewModel.addOrUpdatePlatform(contactId, fieldKey, entry)
                val freshContact = viewModel.getContactById(contactId)
                val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                // 平台支持同步时，解析昵称/头像并写回
                if (fieldKey.kindCanSync) {
                    BadgerLog.d("ContactDetailPage", "Auto-sync from new platform $fieldKey (needsAvatar=$needsAvatar)")
                    try {
                        val content = entry.jumpLink.ifBlank { entry.value ?: "" }
                        val resolveResult = withContext(BadgerDispatchers.io) {
                            KoinComponentBy.get<ContactNetworkResolver>().identify(content)
                        }
                        val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                        val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                        if (resolvedName != null || resolvedAvatar != null) {
                            viewModel.addOrUpdatePlatform(contactId, fieldKey, entry.copy(
                                displayName = resolvedName ?: entry.displayName,
                                avatarUrl = resolvedAvatar ?: entry.avatarUrl
                            ))
                        }
                        if (needsAvatar && resolvedAvatar != null) {
                            val newAvatarPath = downloadAndSaveAvatar(resolvedAvatar, contactId)
                            if (newAvatarPath != null) {
                                val latestContact = viewModel.getContactById(contactId) ?: freshContact
                                viewModel.updateContact(latestContact!!.copy(
                                    avatarPath = newAvatarPath,
                                    updateTime = nowMs()
                                ))
                                BadgerLog.d("ContactDetailPage", "Auto-sync avatar success from $fieldKey")
                            }
                        } else if (resolvedName != null) {
                            val latestContact = viewModel.getContactById(contactId) ?: freshContact
                            viewModel.updateContact(latestContact!!.copy(
                                updateTime = nowMs()
                            ))
                        }
                    } catch (e: Exception) {
                        BadgerLog.e("ContactDetailPage", "Auto-sync avatar failed from $fieldKey", e)
                    }
                }
                viewModel.reloadContact(contactId)
            }
            onRefreshData?.invoke()
        },
        onDismissEditPlatform = { onShowEditPlatformDialogChange(false); onEditingPlatformChange(null) },
        onConfirmEditPlatform = { fieldKey, newEntry ->
            onShowEditPlatformDialogChange(false)
            onEditingPlatformChange(null)
            viewModel.addOrUpdatePlatformAndReload(contactId, fieldKey, newEntry)
            onRefreshData?.invoke()
        },
        onDismissCollectionPicker = { onShowCollectionPickerChange(false) },
        onConfirmCollectionPicker = { addedIds, removedIds ->
            viewModel.updateCollections(contactId, addedIds.toList(), removedIds.toList())
            onRefreshData?.invoke()
            onShowCollectionPickerChange(false)
            val msg = when {
                addedIds.isNotEmpty() && removedIds.isNotEmpty() -> "名片夹已更新"
                addedIds.isNotEmpty() -> "已添加至 ${addedIds.size} 个名片夹"
                removedIds.isNotEmpty() -> "已从 ${removedIds.size} 个名片夹移除"
                else -> null
            }
            if (msg != null) {
                showToast(msg)
            }
        },
        onDismissContactPicker = { onShowContactPickerChange(false) },
        onContactSelected = { targetContact ->
            onSelectedExistingContactChange(targetContact)
            onShowContactPickerChange(false)
        },
        onDismissAttachField = { onSelectedExistingContactChange(null) },
        onConfirmAttachField = { selectedFieldKeys, selectedCustomFieldIds ->
            val sourceData = contactWithFields
            val existing = selectedExistingContact
            if (sourceData == null || existing == null) return@ContactDetailPageDialogs
            viewModel.attachToExisting(
                sourceContact = sourceData.contact,
                sourceFields = sourceData.fieldValues,
                existingContact = existing,
                selectedFieldKeys = selectedFieldKeys,
                selectedCustomFieldIds = selectedCustomFieldIds
            )
            onSelectedExistingContactChange(null)
            viewModel.reloadContact(contactId)
            showToast("已成功附加到 ${existing.name}")
        },
        onCropConfirm = onCropConfirm,
        onDismissCrop = { onCropSourceImageChange(null) },
        onDismissSync = { onShowSyncOptionsSheetChange(false); onSyncPlatformInfoChange(null) },
        onConfirmSync = { syncName, syncAvatar ->
            val platformInfo = syncPlatformInfo
            onShowSyncOptionsSheetChange(false)
            onSyncPlatformInfoChange(null)
            if (platformInfo == null) {
                BadgerLog.w("ContactDetailPage", "onConfirmSync: syncPlatformInfo is null, aborting")
                return@ContactDetailPageDialogs
            }
            scope.launch {
                try {
                    val (pName, pEntry) = platformInfo
                    val resolveResult = withContext(BadgerDispatchers.io) {
                        try {
                            val content = pEntry.jumpLink.ifBlank { pEntry.value ?: "" }
                            val ct = FIELD_DEF_MAP[pName]?.contactType
                            KoinComponentBy.get<ContactNetworkResolver>().identify(content)
                        } catch (e: Exception) {
                            BadgerLog.w("ContactDetailPage", "平台信息解析失败", e)
                            null
                        }
                    }
                    val resolvedName = resolveResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                    val resolvedAvatar = resolveResult?.avatarUrl?.takeIf { it.isNotBlank() }
                    if (resolvedName != null || resolvedAvatar != null) {
                        viewModel.addOrUpdatePlatform(contactId, pName, pEntry.copy(
                            displayName = resolvedName ?: pEntry.displayName,
                            avatarUrl = resolvedAvatar ?: pEntry.avatarUrl
                        ))
                    }
                    val freshContact = viewModel.getContactById(contactId) ?: return@launch
                    var newName: String? = null
                    if (syncName) {
                        newName = resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() }
                    }
                    var avatarPath: String? = null
                    if (syncAvatar) {
                        val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                        if (!avatarToUse.isNullOrBlank()) {
                            onIsSettingAvatarChange(true)
                            val headers = if (avatarToUse.contains("hdslb.com") || avatarToUse.contains("bilibili.com"))
                                BILIBILI_HEADERS else null
                            avatarPath = withContext(BadgerDispatchers.io) {
                                downloadAndSaveAvatar(avatarToUse, contactId, headers ?: emptyMap())
                            }
                            onIsSettingAvatarChange(false)
                        }
                    }
                    var updated = freshContact
                    if (newName != null) updated = updated.copy(name = newName)
                    if (avatarPath != null) updated = updated.copy(avatarPath = avatarPath)
                    if (newName != null || avatarPath != null) {
                        updated = updated.copy(updateTime = nowMs())
                        viewModel.updateContact(updated)
                        onAvatarVersionIncrement()
                        showToast("同步成功")
                        BadgerLog.d("ContactDetailPage", "Sync success for $pName: name=${updated.name}")
                    } else {
                        showToast("未获取到可同步的信息")
                    }
                } catch (e: Exception) {
                    BadgerLog.e("ContactDetailPage", "同步失败", e)
                    onIsSettingAvatarChange(false)
                    showToast("同步失败")
                }
            }
        },
    )

    // ====== 批量导入平台 Dialog ======
    BatchImportPlatformsDialog(
        show = showBatchImportDialog,
        onDismiss = { onShowBatchImportDialogChange(false) },
        onBatchResolve = { urls -> viewModel.batchResolvePlatforms(urls) },
        onConfirm = { selectedItems ->
            scope.launch(BadgerDispatchers.io) {
                selectedItems.forEach { item ->
                    val entry = PlatformEntry(
                        displayName = item.resolved?.name,
                        jumpLink = item.url,
                        value = null,
                        avatarUrl = item.resolved?.avatarUrl,
                    )
                    viewModel.addOrUpdatePlatform(contactId, item.fieldKey, entry)
                    // 自动同步头像（如果平台支持且联系人无头像）
                    if (item.fieldKey.kindCanSync) {
                        val freshContact = viewModel.getContactById(contactId)
                        val needsAvatar = freshContact?.avatarPath.isNullOrBlank() && freshContact?.avatarUrl.isNullOrBlank()
                        if (needsAvatar && item.resolved?.avatarUrl != null) {
                            try {
                                val avatarPath = downloadAndSaveAvatar(
                                    item.resolved.avatarUrl!!, contactId,
                                )
                                if (avatarPath != null) {
                                    val latestContact = viewModel.getContactById(contactId) ?: freshContact
                                    viewModel.updateContact(latestContact!!.copy(
                                        avatarPath = avatarPath,
                                        updateTime = nowMs(),
                                    ))
                                }
                            } catch (e: Exception) {
                                BadgerLog.e("ContactDetailPage", "批量导入头像下载失败: ${item.url}", e)
                            }
                        }
                    }
                }
                viewModel.reloadContact(contactId)
            }
            onRefreshData?.invoke()
            showToast("已添加 ${selectedItems.size} 个平台")
        },
    )

    // ====== 个人介绍 / 标签 / AI 预览 Dialogs ======
    ContactDetailBioEditDialog(
        show = showBioEdit,
        currentBio = contact?.bio,
        onDismiss = { onShowBioEditChange(false) },
        onSave = { newBio ->
            viewModel.updateBio(contactId, newBio)
        },
    )
    TagPickerDialog(
        show = showTagPicker,
        tagRepository = viewModel.tagRepository,
        currentTagIds = tags.map { it.id }.toSet(),
        onDismiss = { onShowTagPickerChange(false) },
        onConfirm = { addedIds, removedIds ->
            viewModel.updateTags(contactId, addedIds, removedIds)
            onShowTagPickerChange(false)
        },
        onManageTags = {
            onShowTagPickerChange(false)
            onShowTagManagerChange(true)
        },
    )
    TagQuickManageDialog(
        show = showTagManager,
        contactId = contactId,
        tagRepository = viewModel.tagRepository,
        onDismiss = { onShowTagManagerChange(false) },
        onOpenFullManager = {
            onShowTagManagerChange(false)
            showToast("请到 设置 → 标签管理 完成全局操作")
        },
    )

    // AI 推荐标签预览
    val aiCandidatesNonEmpty = aiTagCandidates.isNotEmpty() || aiTagLoading || aiTagError != null
    AiTagPreviewDialog(
        show = showAiTagPreview && aiCandidatesNonEmpty,
        candidates = aiTagCandidates,
        isLoading = aiTagLoading,
        errorMessage = aiTagError,
        onDismiss = {
            onShowAiTagPreviewChange(false)
            viewModel.clearAiTagCandidates()
        },
        onConfirm = { selected ->
            viewModel.applyAiTagCandidates(contactId, selected)
            onShowAiTagPreviewChange(false)
        },
    )

    // 头像大图预览
    if (showAvatarPreview) {
        AvatarPreviewDialog(
            contactId = contact?.id ?: -1L,
            avatarUrl = contact?.avatarUrl,
            fallbackImageBitmap = avatarImageBitmap,
            show = true,
            onDismiss = { onShowAvatarPreviewChange(false) },
            onSaveOriginal = {
                onShowAvatarPreviewChange(false)
                scope.launch {
                    try {
                        val c = contact
                        if (c == null) {
                            showToast("无联系人数据")
                            return@launch
                        }
                        val original = it
                            ?: run {
                                val url = c.avatarUrl?.takeIf { it.isNotBlank() }
                                    ?: return@run null
                                val hdUrl = upgradeAvatarUrlToHd(url)
                                val headers = if (hdUrl.contains("hdslb.com") || hdUrl.contains("bilibili.com"))
                                    BILIBILI_HEADERS else emptyMap()
                                downloadImageAsPng(hdUrl, timeoutMs = 8000, headers = headers)
                                    ?: if (hdUrl != url) downloadImageAsPng(url, timeoutMs = 8000, headers = headers) else null
                            }
                        if (original == null) {
                            showToast("无法获取原图,请检查网络")
                            return@launch
                        }
                        val ok = GallerySaver.saveImagePng(
                            original,
                            "badger_avatar_${c.id}_${nowMs()}.png"
                        )
                        val msg = if (ok) "原图已保存到相册" else "保存失败"
                        showToast(msg)
                    } catch (e: Exception) {
                        BadgerLog.e("ContactDetailPage", "保存原图失败", e)
                        showToast("保存失败")
                    }
                }
            },
            onPickNewAvatar = onPickNewAvatar,
        )
    }
}
