package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.AppViewModel
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.CropConfig
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.ImageCropDialog
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.platform.downloadAndStoreAvatar
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddPlatformWindowDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.BirthdayPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.CountryPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.GenderPickerDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.ImportFromPlatformDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.PlatformDetailDialog
import top.mcxiafeng.badger.pages.person.contact.dialogs.RegionPickerDialog
import top.mcxiafeng.badger.pages.person.contact.detail.SyncOptionsBottomSheet
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.nowMs

private const val TAG = "UserProfileDetailPage"

/**
 * UserProfileDetailPage 对话框宿主（U18 下沉）。
 *
 * 覆盖编辑昵称/平台详情/添加编辑平台/同步/删除/基础信息/背景URL/裁剪/导入。
 */
@Composable
internal fun UserProfileDetailDialogs(
    profile: UserProfile?,
    viewModel: UserProfileDetailViewModel,
    userProfileRepository: UserProfileRepository,
    appViewModel: AppViewModel,
    scope: CoroutineScope,
    showEditNameDialog: Boolean,
    showAddPlatformDialog: Boolean,
    showPlatformDetailDialog: Boolean,
    selectedPlatformDetail: Pair<String, PlatformEntry>?,
    showEditPlatformDialog: Boolean,
    editingPlatform: Pair<String, PlatformEntry>?,
    showSyncOptionsSheet: Boolean,
    syncPlatformInfo: Pair<String, PlatformEntry>?,
    showDeleteConfirmDialog: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    basicInfoEditField: String?,
    basicInfoEditCurrent: String?,
    currentCountryName: String?,
    currentCountryExternalId: Long?,
    showBackgroundUrlEditor: Boolean,
    showImportFromPlatform: Boolean,
    showCropDialog: Boolean,
    cropSourceImage: PlatformImage?,
    isSettingAvatar: Boolean,
    avatarVersion: Int,
    onProfileChange: (UserProfile?) -> Unit,
    onAvatarVersionChange: (Int) -> Unit,
    onIsSettingAvatarChange: (Boolean) -> Unit,
    onShowEditNameDialogChange: (Boolean) -> Unit,
    onShowAddPlatformDialogChange: (Boolean) -> Unit,
    onShowPlatformDetailDialogChange: (Boolean) -> Unit,
    onSelectedPlatformDetailChange: (Pair<String, PlatformEntry>?) -> Unit,
    onShowEditPlatformDialogChange: (Boolean) -> Unit,
    onEditingPlatformChange: (Pair<String, PlatformEntry>?) -> Unit,
    onShowSyncOptionsSheetChange: (Boolean) -> Unit,
    onSyncPlatformInfoChange: (Pair<String, PlatformEntry>?) -> Unit,
    onShowDeleteConfirmDialogChange: (Boolean) -> Unit,
    onSelectedPlatformChange: (Pair<String, PlatformEntry>?) -> Unit,
    onBasicInfoEditFieldChange: (String?) -> Unit,
    onBasicInfoEditCurrentChange: (String?) -> Unit,
    onCurrentCountryNameChange: (String?) -> Unit,
    onCurrentCountryExternalIdChange: (Long?) -> Unit,
    onShowBackgroundUrlEditorChange: (Boolean) -> Unit,
    onShowImportFromPlatformChange: (Boolean) -> Unit,
    onShowCropDialogChange: (Boolean) -> Unit,
    onCropSourceImageChange: (PlatformImage?) -> Unit,
    onCropConfirm: (ByteArray) -> Unit,
    onRefreshData: (() -> Unit)?,
) {
    // 编辑昵称对话框
    if (showEditNameDialog) {
        WindowDialog(
            show = true,
            title = "编辑昵称",
            summary = "",
            onDismissRequest = { onShowEditNameDialogChange(false) },
        ) {
            var editName by remember(profile) { mutableStateOf(profile?.name ?: "") }
            var editBio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(value = editName, onValueChange = { editName = it }, label = "昵称", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = editBio, onValueChange = { editBio = it }, label = "简介", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { onShowEditNameDialogChange(false) },
                    onPositive = {
                        scope.launch(BadgerDispatchers.io) {
                            val current = userProfileRepository.getUserProfileOnce() ?: UserProfile(name = "用户", updateTime = nowMs())
                            val updated = current.copy(name = editName.ifBlank { "用户" }, bio = editBio.ifBlank { null }, updateTime = nowMs())
                            userProfileRepository.saveUserProfile(updated)
                            withContext(Dispatchers.Main) { onProfileChange(userProfileRepository.getUserProfileOnce() ?: updated) }
                        }
                        onShowEditNameDialogChange(false)
                    },
                )
            }
        }
    }

    // 平台详情弹窗
    if (showPlatformDetailDialog) selectedPlatformDetail?.let { (platformName, entry) ->
        PlatformDetailDialog(
            show = true,
            platformName = platformName,
            entry = entry,
            onDismiss = {
                onShowPlatformDetailDialogChange(false)
                onSelectedPlatformDetailChange(null)
            },
        )
    }

    // 添加平台对话框
    if (showAddPlatformDialog) AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.ADD,
        existingProfile = profile,
        onDismiss = { onShowAddPlatformDialogChange(false) },
        onConfirm = { fieldKey, entry ->
            onShowAddPlatformDialogChange(false)
            scope.launch(BadgerDispatchers.io) {
                userProfileRepository.updatePlatformField(fieldKey, entry.jumpLink, entry.value, entry.displayName, entry.avatarUrl, entry.originalLink)
                val updated = userProfileRepository.getUserProfileOnce() ?: profile ?: UserProfile(name = "用户", updateTime = nowMs())
                withContext(Dispatchers.Main) { onProfileChange(updated) }
                val currentProfile = userProfileRepository.getUserProfileOnce() ?: return@launch
                val needsAvatar = currentProfile.avatarPath.isNullOrBlank()
                val needsName = currentProfile.name.isBlank() || currentProfile.name == "用户"
                if (fieldKey.kindCanSync && (needsAvatar || needsName)) {
                    try {
                        val (resolvedName, resolvedAvatar) = resolvePlatformEntryForSync(userProfileRepository, fieldKey, entry)
                        var newProfile = userProfileRepository.getUserProfileOnce() ?: currentProfile
                        if (needsName && resolvedName != null) newProfile = newProfile.copy(name = resolvedName, updateTime = nowMs())
                        if (needsAvatar && resolvedAvatar != null) {
                            val savedPath = downloadAndStoreAvatar(resolvedAvatar, "user_avatar.webp")
                            if (savedPath != null) newProfile = newProfile.copy(avatarPath = savedPath, updateTime = nowMs())
                        }
                        if (newProfile != userProfileRepository.getUserProfileOnce()) {
                            userProfileRepository.saveUserProfile(newProfile)
                            withContext(Dispatchers.Main) {
                                onProfileChange(userProfileRepository.getUserProfileOnce() ?: newProfile)
                                onAvatarVersionChange(avatarVersion + 1)
                                appViewModel.refreshUserProfile()
                                onRefreshData?.invoke()
                            }
                        }
                    } catch (e: Exception) { BadgerLog.e(TAG, "Auto-sync failed from $fieldKey", e) }
                }
            }
        },
    )

    // 编辑平台对话框
    if (showEditPlatformDialog) editingPlatform?.let { (platformName, entry) ->
        AddPlatformWindowDialog(
            show = true,
            mode = AddEditMode.EDIT,
            editingEntry = platformName to entry,
            onDismiss = {
                onShowEditPlatformDialogChange(false)
                onEditingPlatformChange(null)
            },
            onConfirm = { fieldKey, newEntry ->
                scope.launch(BadgerDispatchers.io) {
                    userProfileRepository.updatePlatformField(fieldKey, newEntry.jumpLink, newEntry.value, newEntry.displayName, newEntry.avatarUrl, newEntry.originalLink)
                    withContext(Dispatchers.Main) {
                        val updated = userProfileRepository.getUserProfileOnce() ?: profile ?: UserProfile(name = "用户", updateTime = nowMs())
                        onProfileChange(updated)
                    }
                }
                onShowEditPlatformDialogChange(false)
                onEditingPlatformChange(null)
            },
        )
    }

    // 同步选项底部弹窗
    if (showSyncOptionsSheet && syncPlatformInfo != null) {
        val currentSyncInfo = syncPlatformInfo!!
        SyncOptionsBottomSheet(
            platformInfo = currentSyncInfo,
            currentProfile = profile,
            onDismiss = {
                onShowSyncOptionsSheetChange(false)
                onSyncPlatformInfoChange(null)
            },
            onConfirm = { syncName, syncAvatar ->
                onShowSyncOptionsSheetChange(false)
                onSyncPlatformInfoChange(null)
                scope.launch(Dispatchers.Main) {
                    try {
                        val (pName, pEntry) = currentSyncInfo
                        val (resolvedName, resolvedAvatar) = resolvePlatformEntryForSync(userProfileRepository, pName, pEntry)
                        val current = withContext(BadgerDispatchers.io) { userProfileRepository.getUserProfileOnce() } ?: UserProfile(name = "用户", updateTime = nowMs())
                        val newName = if (syncName) resolvedName ?: pEntry.displayName?.takeIf { it.isNotBlank() } ?: current.name else current.name
                        var newAvatarPath = current.avatarPath
                        val avatarToUse = resolvedAvatar ?: pEntry.avatarUrl
                        if (syncAvatar && !avatarToUse.isNullOrBlank()) {
                            onIsSettingAvatarChange(true)
                            val savedPath = downloadAndStoreAvatar(avatarToUse, "user_avatar.webp")
                            if (savedPath != null) newAvatarPath = savedPath
                            onIsSettingAvatarChange(false)
                        }
                        val updated = current.copy(name = newName, avatarPath = newAvatarPath, updateTime = nowMs())
                        withContext(BadgerDispatchers.io) { userProfileRepository.saveUserProfile(updated) }
                        onProfileChange(withContext(BadgerDispatchers.io) { userProfileRepository.getUserProfileOnce() } ?: updated)
                        onAvatarVersionChange(avatarVersion + 1)
                        appViewModel.refreshUserProfile()
                        onRefreshData?.invoke()
                        showToast("同步成功")
                    } catch (e: Exception) {
                        BadgerLog.e(TAG, "同步失败", e)
                        onIsSettingAvatarChange(false)
                        showToast("同步失败: ${e.message}")
                    }
                }
            },
        )
    }

    // 删除平台确认对话框
    if (showDeleteConfirmDialog) {
        WindowDialog(
            show = true,
            title = "删除平台",
            summary = "确定要删除 ${FIELD_DEF_MAP[selectedPlatform?.first]?.displayName ?: selectedPlatform?.first ?: ""} 吗？此操作不可撤销。",
            onDismissRequest = {
                onShowDeleteConfirmDialogChange(false)
                onSelectedPlatformChange(null)
            },
        ) {
            DialogButtonRow(
                positiveText = "删除",
                onNegative = {
                    onShowDeleteConfirmDialogChange(false)
                    onSelectedPlatformChange(null)
                },
                onPositive = {
                    onShowDeleteConfirmDialogChange(false)
                    val (pName, deletedEntry) = selectedPlatform ?: return@DialogButtonRow
                    val currentAvatarPath = profile?.avatarPath
                    val currentName = profile?.name ?: "用户"
                    val deletedDisplayName = deletedEntry.displayName
                    scope.launch(BadgerDispatchers.io) {
                        userProfileRepository.removePlatform(pName)
                        val updatedProfile = userProfileRepository.getUserProfileOnce() ?: profile
                        if (updatedProfile != null) {
                            val remainingPlatforms = ContactMapper.decodePlatformsMap(updatedProfile.platformsJson) ?: emptyMap()
                            var newAvatarPath = updatedProfile.avatarPath
                            if (currentAvatarPath != null) {
                                val fallbackEntry = remainingPlatforms.entries.firstOrNull { !it.value.avatarUrl.isNullOrBlank() }
                                if (fallbackEntry != null) {
                                    val savedPath = if (!fallbackEntry.value.avatarUrl.isNullOrBlank()) downloadAndStoreAvatar(fallbackEntry.value.avatarUrl!!, "user_avatar.webp") else null
                                    if (savedPath != null) newAvatarPath = savedPath else { ImageFiles.deleteImageFile(currentAvatarPath); newAvatarPath = null }
                                } else { ImageFiles.deleteImageFile(currentAvatarPath); newAvatarPath = null }
                            }
                            var newName = updatedProfile.name
                            if (deletedDisplayName != null && currentName == deletedDisplayName) {
                                val fallbackNameEntry = remainingPlatforms.entries.firstOrNull { !it.value.displayName.isNullOrBlank() }
                                newName = fallbackNameEntry?.value?.displayName ?: "用户"
                            }
                            userProfileRepository.saveUserProfile(updatedProfile.copy(name = newName, avatarPath = newAvatarPath, updateTime = nowMs()))
                        }
                        withContext(Dispatchers.Main) {
                            onProfileChange(userProfileRepository.getUserProfileOnce() ?: profile)
                            onAvatarVersionChange(avatarVersion + 1)
                            appViewModel.refreshUserProfile()
                            onRefreshData?.invoke()
                        }
                        onSelectedPlatformChange(null)
                        showToast("已删除 $pName")
                    }
                },
                isDestructive = true,
            )
        }
    }

    // 基础信息编辑 Dialogs（性别/生日/国家/地区）
    val onProfileFieldUpdated: (top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity) -> Unit = { fresh ->
        onProfileChange(fresh)
        appViewModel.refreshUserProfile()
        onRefreshData?.invoke()
    }
    GenderPickerDialog(show = basicInfoEditField == "gender", current = basicInfoEditCurrent, onDismiss = { onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null) }, onConfirm = { value -> onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null); viewModel.updateProfileField("sex", value, onProfileFieldUpdated) })
    BirthdayPickerDialog(show = basicInfoEditField == "birthday", current = basicInfoEditCurrent, onDismiss = { onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null) }, onConfirm = { value -> onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null); viewModel.updateProfileField("birthday", value, onProfileFieldUpdated) })
    CountryPickerDialog(show = basicInfoEditField == "country", current = basicInfoEditCurrent, onDismiss = { onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null) }, onConfirm = { name, externalId -> onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null); onCurrentCountryNameChange(name); onCurrentCountryExternalIdChange(externalId); viewModel.updateProfileField("country", name, onProfileFieldUpdated) })
    RegionPickerDialog(show = basicInfoEditField == "region", current = basicInfoEditCurrent, countryId = currentCountryExternalId, countryName = currentCountryName, onDismiss = { onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null) }, onConfirm = { value -> onBasicInfoEditFieldChange(null); onBasicInfoEditCurrentChange(null); viewModel.updateProfileField("region", value, onProfileFieldUpdated) })

    // 背景图 URL 手动编辑器
    if (showBackgroundUrlEditor) {
        var bgUrl by remember { mutableStateOf(profile?.backgroundURL ?: "") }
        WindowDialog(show = true, title = "背景图 URL", summary = "输入背景图网络地址，或点击清除移除当前背景", onDismissRequest = { onShowBackgroundUrlEditorChange(false) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(value = bgUrl, onValueChange = { bgUrl = it }, label = "背景图 URL", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(positiveText = "保存", onNegative = { onShowBackgroundUrlEditorChange(false) }, onPositive = { onShowBackgroundUrlEditorChange(false); viewModel.updateProfileField("backgroundURL", bgUrl.ifBlank { null }, onProfileFieldUpdated) })
            }
        }
    }

    // Avatar crop dialog
    if (showCropDialog && cropSourceImage != null) {
        Dialog(onDismissRequest = { onShowCropDialogChange(false); onCropSourceImageChange(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            ImageCropDialog(image = cropSourceImage!!, cropConfig = CropConfig(mode = CropMode.AVATAR, outputWidth = 256, outputHeight = 256), onConfirm = onCropConfirm, onDismiss = { onShowCropDialogChange(false); onCropSourceImageChange(null) })
        }
    }

    // 从平台解析导入弹窗
    if (showImportFromPlatform) {
        ImportFromPlatformDialog(
            show = true,
            onDismiss = { onShowImportFromPlatformChange(false) },
            onConfirm = { importedName, importedBio, importedAvatarPath ->
                onShowImportFromPlatformChange(false)
                viewModel.importFromPlatform(importedName, importedBio, importedAvatarPath) { fresh ->
                    onProfileChange(fresh)
                    if (importedAvatarPath != null) onAvatarVersionChange(avatarVersion + 1)
                    appViewModel.refreshUserProfile()
                    onRefreshData?.invoke()
                    showToast("已从平台导入")
                }
            },
        )
    }
}
