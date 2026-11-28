package top.mcxiafeng.badger.pages.setupguide

import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing


import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.pages.person.contact.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.AddPlatformWindowDialog
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SetupStepPlatforms(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onNavigateToAddPlatform: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setupGuideViewModel: SetupGuideViewModel = hiltViewModel()
    val userProfileRepository = setupGuideViewModel.userProfileRepository
    val contactRepository = setupGuideViewModel.contactRepository
    val isSyncing by setupGuideViewModel.isSyncing.collectAsState()

    val profile by setupGuideViewModel.platformProfile.collectAsState()
    val platforms = remember(profile) { buildPlatformList(profile) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPlatformName by remember { mutableStateOf<String?>(null) }

    // [修复防御]: 锁定"下一步"和"暂不填写"——sync 进行中禁止翻页，
    // 避免 SetupStepProfile 在 displayName/avatarUrl 尚未落库前被渲染，导致默认值回填。
    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            if (isSyncing) {
                Log.d(TAG, "Platforms step skip blocked: isSyncing=true")
                return@SetupStepScaffold
            }
            Log.d(TAG, "Platforms step skipped")
            onSkip()
        },
        onNext = {
            if (isSyncing) {
                Log.d(TAG, "Platforms step next blocked: isSyncing=true")
                return@SetupStepScaffold
            }
            Log.d(TAG, "Platforms step completed, ${platforms.size} platforms added")
            onNext()
        },
        nextEnabled = platforms.isNotEmpty() && !isSyncing,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().scrollTopFade().verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 32.dp + BadgerSpacing.scrollContentTopGap, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "添加社交平台",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "让别人找到你",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                if (platforms.isEmpty()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "暂未添加社交平台",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                platforms.forEach { (name, entry) ->
                    ArrowPreference(
                        title = name,
                        summary = entry.value ?: entry.jumpLink,
                        onClick = {
                            // [修复防御]: sync 中禁止打开编辑/添加对话框，避免用户再次提交导致 runSync 重入。
                            if (isSyncing) return@ArrowPreference
                            editingPlatform = name to entry
                            showEditDialog = true
                            Log.d(TAG, "Platform edit: $name")
                        }
                    )
                }
                if (isSyncing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        Text(
                            text = "正在获取信息…完成后才能继续",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                ArrowPreference(
                    title = "添加社交平台",
                    summary = "添加你的社交账号",
                    onClick = {
                        if (isSyncing) return@ArrowPreference
                        Log.d("SetupStepPlatformsTester", "打开添加平台页面")
                        onNavigateToAddPlatform()
                    }
                )
            }
        }
    }

    // 编辑平台对话框
    if (showEditDialog) editingPlatform?.let { (platformName, entry) ->
        AddPlatformWindowDialog(
            show = true,
            mode = AddEditMode.EDIT,
            editingEntry = platformName to entry,
            onDismiss = {
                showEditDialog = false
                editingPlatform = null
            },
            onConfirm = { fieldKey, newEntry ->
                showEditDialog = false
                editingPlatform = null
                val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
                // [修复防御]: 编辑时若标识符（value/jumpLink）变化，即使 displayName/avatarUrl 已有值也必须重 sync，
                // 因为改 QQ 号可能指向不同账户，旧 name/avatar 不再有效。
                val identifierChanged = newEntry.value != entry.value || newEntry.jumpLink != entry.jumpLink
                val shouldSync = adapter?.canSync == true && (
                    newEntry.displayName.isNullOrBlank() || newEntry.avatarUrl.isNullOrBlank() || identifierChanged
                )
                setupGuideViewModel.runSync {
                    withContext(Dispatchers.IO) {
                        // [修复防御]: 同添加逻辑——捕获 preProfile 判定名字是否 auto-fill 产物，
                        // 避免编辑平台时名字停滞在旧 auto-fill 值。
                        val preProfile = userProfileRepository.getUserProfileOnce()
                        val nameWasAutoFilled = preProfile == null ||
                            preProfile.name.isBlank() || preProfile.name == "用户" ||
                            preProfile.platforms?.entries?.any { (_, e) ->
                                !e.displayName.isNullOrBlank() && e.displayName == preProfile.name
                            } == true

                        contactRepository.updateContactPlatform(MY_CONTACT_ID, fieldKey, newEntry)
                        if (shouldSync) {
                            try {
                                val resolveContent = newEntry.jumpLink.ifBlank { newEntry.value ?: "" }
                                val result = adapter.resolve(resolveContent)
                                if (result != null) {
                                    contactRepository.updateContactPlatform(
                                        MY_CONTACT_ID, fieldKey,
                                        newEntry.copy(
                                            displayName = result.name ?: newEntry.displayName,
                                            avatarUrl = result.avatarUrl ?: newEntry.avatarUrl
                                        )
                                    )
                                    Log.d(TAG, "Auto-fetched info for $fieldKey: name=${result.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Auto-fetch failed for $fieldKey", e)
                            }
                        }
                        if (nameWasAutoFilled) {
                            val p = loadMyProfile(userProfileRepository, contactRepository)
                            val canSyncEntry = p?.platforms?.entries?.firstOrNull { e ->
                                val ct = FIELD_DEF_MAP[e.key]?.contactType
                                val adp = ct?.let { PlatformAdapterRegistry.getAdapter(it) }
                                adp?.canSync == true && !e.value.displayName.isNullOrBlank()
                            }
                            val fb = p?.platforms?.entries?.firstOrNull { !it.value.displayName.isNullOrBlank() }
                            val chosen = canSyncEntry ?: fb
                            if (chosen != null) {
                                contactRepository.getContactById(MY_CONTACT_ID)?.let { me ->
                                    contactRepository.updateContact(me.copy(name = chosen.value.displayName!!, updateTime = System.currentTimeMillis()))
                                }
                                Log.d(TAG, "Profile name auto-filled: ${chosen.value.displayName} from ${chosen.key}")
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Platform updated: $fieldKey")
                    }
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) WindowDialog(
        show = true,
        title = "删除平台",
        summary = "确定要删除 ${deletingPlatformName ?: ""} 吗？此操作不可撤销。",
        onDismissRequest = {
            showDeleteDialog = false
            deletingPlatformName = null
        },
    ) {
        DialogButtonRow(
            positiveText = "删除",
            onNegative = {
                showDeleteDialog = false
                deletingPlatformName = null
            },
            onPositive = {
                showDeleteDialog = false
                val name = deletingPlatformName ?: return@DialogButtonRow
                scope.launch(Dispatchers.IO) {
                    contactRepository.removeContactPlatform(MY_CONTACT_ID, name)
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Platform deleted: $name")
                    }
                }
                deletingPlatformName = null
            },
            isDestructive = true
        )
    }
}

private fun buildPlatformList(profile: UserProfile?): List<Pair<String, PlatformEntry>> {
    if (profile == null) return emptyList()
    return profile.platforms
        ?.filter { it.value.jumpLink.isNotBlank() || !it.value.value.isNullOrBlank() }
        ?.map { (key, entry) ->
            val displayName = FIELD_DEF_MAP[key]?.displayName ?: key
            displayName to entry
        }
        ?.toList() ?: emptyList()
}
