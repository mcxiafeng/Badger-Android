package top.mcxiafeng.badger.pages.setupguide

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
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.network.adapter.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.pages.person.contact.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.AddPlatformWindowDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun SetupStepPlatforms(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setupGuideViewModel: SetupGuideViewModel = hiltViewModel()
    val userProfileRepository = setupGuideViewModel.userProfileRepository
    val isSyncing by setupGuideViewModel.isSyncing.collectAsState()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var platforms by remember { mutableStateOf<List<Pair<String, PlatformEntry>>>(emptyList()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPlatformName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = userProfileRepository.getUserProfileOnce()
        profile = p
        platforms = buildPlatformList(p)
        Log.d(TAG, "Platforms step: loaded ${platforms.size} platforms")
    }

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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
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
                        showAddDialog = true
                        Log.d(TAG, "Add platform dialog opened")
                    }
                )
            }
        }
    }

    // 添加平台对话框
    if (showAddDialog) AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.ADD,
        existingProfile = profile,
        onDismiss = { showAddDialog = false },
        onConfirm = { fieldKey, entry ->
            showAddDialog = false
            val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
            val adapter = contactType?.let { PlatformAdapterRegistry.getAdapter(it) }
            val shouldSync = adapter?.canSync == true &&
                (entry.displayName.isNullOrBlank() || entry.avatarUrl.isNullOrBlank())
            // [修复防御]: 用 ViewModel.runSync 统一管理同步状态，使"下一步"按钮与翻页手势都能感知到锁。
            setupGuideViewModel.runSync {
                withContext(Dispatchers.IO) {
                    userProfileRepository.updatePlatformField(fieldKey, entry.jumpLink, entry.value, entry.displayName, entry.avatarUrl, entry.originalLink)
                    if (shouldSync) {
                        try {
                            val resolveContent = entry.jumpLink.ifBlank { entry.value ?: "" }
                            val result = adapter.resolve(resolveContent)
                            if (result != null) {
                                userProfileRepository.updatePlatformField(
                                    fieldKey, entry.jumpLink, entry.value,
                                    result.name ?: entry.displayName,
                                    result.avatarUrl ?: entry.avatarUrl,
                                    entry.originalLink
                                )
                                Log.d(TAG, "Auto-fetched info for $fieldKey: name=${result.name}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Auto-fetch failed for $fieldKey", e)
                        }
                    }
                    // [修复防御]: 将 name 自动填充提前到 Platforms sync 阶段完成，
                    // 写入 UserProfile.name，Profile 页不再需要 LaunchedEffect 做 auto-fill，
                    // 彻底消除"空字段闪现后覆盖手动输入"的竞态。
                    val p = userProfileRepository.getUserProfileOnce()
                    if (p != null && (p.name.isBlank() || p.name == "用户")) {
                        val canSyncEntry = p.platforms?.entries?.firstOrNull { e ->
                            val ct = FIELD_DEF_MAP[e.key]?.contactType
                            val adp = ct?.let { PlatformAdapterRegistry.getAdapter(it) }
                            adp?.canSync == true && !e.value.displayName.isNullOrBlank()
                        }
                        val fb = p.platforms?.entries?.firstOrNull { !it.value.displayName.isNullOrBlank() }
                        val chosen = canSyncEntry ?: fb
                        if (chosen != null) {
                            userProfileRepository.saveUserProfile(p.copy(name = chosen.value.displayName!!, updateTime = System.currentTimeMillis()))
                            Log.d(TAG, "Profile name auto-filled: ${chosen.value.displayName} from ${chosen.key}")
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    profile = userProfileRepository.getUserProfileOnce()
                    platforms = buildPlatformList(profile)
                    Log.d(TAG, "Platform added: $fieldKey")
                }
            }
        }
    )

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
                val shouldSync = adapter?.canSync == true &&
                    (newEntry.displayName.isNullOrBlank() || newEntry.avatarUrl.isNullOrBlank())
                setupGuideViewModel.runSync {
                    withContext(Dispatchers.IO) {
                        userProfileRepository.updatePlatformField(fieldKey, newEntry.jumpLink, newEntry.value, newEntry.displayName, newEntry.avatarUrl, newEntry.originalLink)
                        if (shouldSync) {
                            try {
                                val resolveContent = newEntry.jumpLink.ifBlank { newEntry.value ?: "" }
                                val result = adapter.resolve(resolveContent)
                                if (result != null) {
                                    userProfileRepository.updatePlatformField(
                                        fieldKey, newEntry.jumpLink, newEntry.value,
                                        result.name ?: newEntry.displayName,
                                        result.avatarUrl ?: newEntry.avatarUrl,
                                        newEntry.originalLink
                                    )
                                    Log.d(TAG, "Auto-fetched info for $fieldKey: name=${result.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Auto-fetch failed for $fieldKey", e)
                            }
                        }
                        // [修复防御]: 同添加逻辑——sync 完成后若 Profile 名仍为默认值则自动填充。
                        val p = userProfileRepository.getUserProfileOnce()
                        if (p != null && (p.name.isBlank() || p.name == "用户")) {
                            val canSyncEntry = p.platforms?.entries?.firstOrNull { e ->
                                val ct = FIELD_DEF_MAP[e.key]?.contactType
                                val adp = ct?.let { PlatformAdapterRegistry.getAdapter(it) }
                                adp?.canSync == true && !e.value.displayName.isNullOrBlank()
                            }
                            val fb = p.platforms?.entries?.firstOrNull { !it.value.displayName.isNullOrBlank() }
                            val chosen = canSyncEntry ?: fb
                            if (chosen != null) {
                                userProfileRepository.saveUserProfile(p.copy(name = chosen.value.displayName!!, updateTime = System.currentTimeMillis()))
                                Log.d(TAG, "Profile name auto-filled: ${chosen.value.displayName} from ${chosen.key}")
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        profile = userProfileRepository.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
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
                    userProfileRepository.removePlatform(name)
                    withContext(Dispatchers.Main) {
                        profile = userProfileRepository.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
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
