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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.pages.person.contact.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.AddPlatformWindowDialog
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val PLATFORM_TAG = "SetupStepPlatforms"
private const val PAGE_INDEX = 3

/**
 * 引导 Step 3 — 添加社交平台。
 *
 * 设计契约：
 * - 不可跳过。至少添加 1 个平台才能下一步 —— 名片核心是分享联系方式，没平台没意义。
 * - 添加/编辑平台时同步触发 auto-fetch（昵称/头像），与已有 V2 链路一致。
 * - sync 进行中锁定"下一步"与"添加/编辑"按钮（[SetupGuideViewModel.isSyncing]），
 *   防止用户在 setupGuideViewModel.runSync 重入期间二次提交。
 */
@Composable
internal fun SetupStepPlatforms(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()
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
        Log.d(PLATFORM_TAG, "Platforms step: loaded ${platforms.size} platforms")
    }

    LaunchedEffect(platforms, isSyncing) {
        setupGuideViewModel.setPageValid(
            PAGE_INDEX,
            platforms.isNotEmpty() && !isSyncing,
        )
    }

    SetupStepScaffold(
        onBack = onBack,
        onNext = {
            if (isSyncing) {
                Log.d(PLATFORM_TAG, "next blocked: isSyncing=true")
                return@SetupStepScaffold
            }
            Log.d(PLATFORM_TAG, "next → ${platforms.size} platforms")
            onNext()
        },
        nextEnabled = platforms.isNotEmpty() && !isSyncing,
        nextText = "继续",
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "添加社交平台",
                subtitle = "至少 1 个，让别人能找到你",
                icon = Icons.Outlined.Group,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                if (platforms.isEmpty() && !isSyncing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = BadgerSpacing.xxl, horizontal = BadgerSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.height(BadgerSpacing.md))
                        Text(
                            text = "还没有添加社交平台",
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                        Text(
                            text = "点击下方「添加社交平台」开始",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                platforms.forEach { (name, entry) ->
                    ArrowPreference(
                        title = name,
                        summary = entry.value ?: entry.jumpLink,
                        onClick = {
                            if (isSyncing) return@ArrowPreference
                            editingPlatform = name to entry
                            showEditDialog = true
                            Log.d(PLATFORM_TAG, "Platform edit: $name")
                        },
                    )
                }
                if (isSyncing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                    ) {
                        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        Text(
                            text = "正在获取信息…完成后才能继续",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                ArrowPreference(
                    title = "添加社交平台",
                    summary = "添加你的社交账号",
                    onClick = {
                        if (isSyncing) return@ArrowPreference
                        showAddDialog = true
                        Log.d(PLATFORM_TAG, "Add platform dialog opened")
                    },
                )
            }
        }
    }

    if (showAddDialog) AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.ADD,
        existingProfile = profile,
        onDismiss = { showAddDialog = false },
        onConfirm = { fieldKey, entry ->
            showAddDialog = false
            val shouldSync = fieldKey.kindCanSync &&
                (entry.displayName.isNullOrBlank() || entry.avatarUrl.isNullOrBlank())
            setupGuideViewModel.runSync(reason = "add:$fieldKey") {
                withContext(Dispatchers.IO) {
                    savePlatformAndMaybeSync(
                        repo = userProfileRepository,
                        fieldKey = fieldKey,
                        jumpLink = entry.jumpLink,
                        value = entry.value,
                        displayName = entry.displayName,
                        avatarUrl = entry.avatarUrl,
                        originalLink = entry.originalLink,
                        shouldSync = shouldSync,
                    )
                }
                withContext(Dispatchers.Main) {
                    profile = userProfileRepository.getUserProfileOnce()
                    platforms = buildPlatformList(profile)
                    Log.d(PLATFORM_TAG, "Platform added: $fieldKey")
                }
            }
        },
    )

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
                val identifierChanged = newEntry.value != entry.value || newEntry.jumpLink != entry.jumpLink
                val shouldSync = fieldKey.kindCanSync && (
                    newEntry.displayName.isNullOrBlank() || newEntry.avatarUrl.isNullOrBlank() || identifierChanged
                    )
                setupGuideViewModel.runSync(reason = "edit:$fieldKey") {
                    withContext(Dispatchers.IO) {
                        savePlatformAndMaybeSync(
                            repo = userProfileRepository,
                            fieldKey = fieldKey,
                            jumpLink = newEntry.jumpLink,
                            value = newEntry.value,
                            displayName = newEntry.displayName,
                            avatarUrl = newEntry.avatarUrl,
                            originalLink = newEntry.originalLink,
                            shouldSync = shouldSync,
                        )
                    }
                    withContext(Dispatchers.Main) {
                        profile = userProfileRepository.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
                        Log.d(PLATFORM_TAG, "Platform updated: $fieldKey")
                    }
                }
            },
        )
    }

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
                val name = deletingPlatformName
                showDeleteDialog = false
                deletingPlatformName = null
                if (name == null) return@DialogButtonRow
                setupGuideViewModel.runSync(reason = "delete:$name") {
                    withContext(Dispatchers.IO) {
                        userProfileRepository.removePlatform(name)
                    }
                    withContext(Dispatchers.Main) {
                        profile = userProfileRepository.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
                        Log.d(PLATFORM_TAG, "Platform deleted: $name")
                    }
                }
            },
            isDestructive = true,
        )
    }
}

private fun buildPlatformList(profile: UserProfile?): List<Pair<String, PlatformEntry>> {
    if (profile == null) return emptyList()
    return ContactMapper.decodePlatformsMap(profile.platformsJson)
        ?.filter { it.value.jumpLink.isNotBlank() || !it.value.value.isNullOrBlank() }
        ?.map { (key, entry) ->
            val displayName = FIELD_DEF_MAP[key]?.displayName ?: entry.displayName ?: key
            displayName to entry
        }
        ?.toList() ?: emptyList()
}

/** 共用的「保存平台 + 按需 sync + 名字回填」流程。 */
private suspend fun savePlatformAndMaybeSync(
    repo: top.mcxiafeng.badger.data.repository.UserProfileRepository,
    fieldKey: String,
    jumpLink: String,
    value: String?,
    displayName: String?,
    avatarUrl: String?,
    originalLink: String?,
    shouldSync: Boolean,
) {
    val preProfile = repo.getUserProfileOnce()
    val nameWasAutoFilled = isNameAutoFilled(preProfile)

    repo.updatePlatformField(
        fieldKey, jumpLink, value, displayName, avatarUrl, originalLink,
    )

    if (shouldSync) {
        try {
            val resolveContent = jumpLink.ifBlank { value ?: "" }
            val result = ContactNetworkResolver.identify(resolveContent)
            if (result != null) {
                repo.updatePlatformField(
                    fieldKey, jumpLink, value,
                    result.name ?: displayName,
                    result.avatarUrl ?: avatarUrl,
                    originalLink,
                )
                Log.d(PLATFORM_TAG, "Auto-fetched info for $fieldKey: name=${result.name}")
            }
        } catch (e: Exception) {
            Log.e(PLATFORM_TAG, "Auto-fetch failed for $fieldKey", e)
        }
    }

    if (nameWasAutoFilled) autoFillProfileName(repo)
}

/** 名字从未手动设置 / 默认值 / 与某平台 displayName 一致即视为 auto-fill。 */
private fun isNameAutoFilled(profile: UserProfile?): Boolean {
    if (profile == null) return true
    if (profile.name.isBlank() || profile.name == "用户") return true
    return ContactMapper.decodePlatformsMap(profile.platformsJson)?.entries?.any { (_, e) ->
        !e.displayName.isNullOrBlank() && e.displayName == profile.name
    } == true
}

/** 优先取可 sync 的平台 displayName 作名字；找不到则取任意非空 displayName 的。 */
private suspend fun autoFillProfileName(
    repo: top.mcxiafeng.badger.data.repository.UserProfileRepository,
) {
    val p = repo.getUserProfileOnce() ?: return
    val platformMap = ContactMapper.decodePlatformsMap(p.platformsJson) ?: return
    val canSyncEntry = platformMap.entries.firstOrNull { e ->
        e.key.kindCanSync && !e.value.displayName.isNullOrBlank()
    }
    val fallbackEntry = platformMap.entries.firstOrNull { !it.value.displayName.isNullOrBlank() }
    val chosen = canSyncEntry ?: fallbackEntry ?: return
    val chosenName = chosen.value.displayName ?: return
    repo.saveUserProfile(
        p.copy(name = chosenName, updateTime = System.currentTimeMillis()),
    )
    Log.d(PLATFORM_TAG, "Profile name auto-filled: $chosenName from ${chosen.key}")
}
