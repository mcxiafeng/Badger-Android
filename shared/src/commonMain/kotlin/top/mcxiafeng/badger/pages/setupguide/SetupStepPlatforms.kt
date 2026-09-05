package top.mcxiafeng.badger.pages.setupguide

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddEditMode
import top.mcxiafeng.badger.pages.person.contact.dialogs.AddPlatformWindowDialog
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

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
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()
    val isSyncing by setupGuideViewModel.isSyncing.collectAsState()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var platforms by remember { mutableStateOf<List<Pair<String, PlatformEntry>>>(emptyList()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPlatformName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = setupGuideViewModel.getUserProfileOnce()
        profile = p
        platforms = buildPlatformList(p)
        BadgerLog.d(PLATFORM_TAG, "Platforms step: loaded ${platforms.size} platforms")
    }

    // [修复防御]: 上报当前页可推进性 — 至少 1 个平台 + 不在 sync。
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
                BadgerLog.d(PLATFORM_TAG, "next blocked: isSyncing=true")
                return@SetupStepScaffold
            }
            BadgerLog.d(PLATFORM_TAG, "next → ${platforms.size} platforms")
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
                icon = Lucide.Users,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                if (platforms.isEmpty() && !isSyncing) {
                    // [修复防御]: 空态视觉引导 —— 加 icon + 行动召唤,
                    // 比「一行小字」更直观告诉用户下一步做什么。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = BadgerSpacing.xxl, horizontal = BadgerSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Lucide.Users,
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
                            // [修复防御]: sync 中禁止打开编辑对话框，避免用户再次提交导致 runSync 重入。
                            if (isSyncing) return@ArrowPreference
                            editingPlatform = name to entry
                            showEditDialog = true
                            BadgerLog.d(PLATFORM_TAG, "Platform edit: $name")
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
                        BadgerLog.d(PLATFORM_TAG, "Add platform dialog opened")
                    },
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
            // sync 判定基于 platformKey 字符串(`SYNCABLE_KINDS`),与服务端 `/v1/resolver/<kind>/...` 端点对齐。
            val shouldSync = fieldKey.kindCanSync &&
                (entry.displayName.isNullOrBlank() || entry.avatarUrl.isNullOrBlank())
            // [修复防御]: 用 ViewModel.runSync 统一管理同步状态，使"下一步"按钮与翻页手势都能感知到锁。
            setupGuideViewModel.runSync(reason = "add:$fieldKey") {
                withContext(BadgerDispatchers.io) {
                    setupGuideViewModel.savePlatformAndMaybeSync(
                        fieldKey = fieldKey,
                        jumpLink = entry.jumpLink,
                        value = entry.value,
                        displayName = entry.displayName,
                        avatarUrl = entry.avatarUrl,
                        originalLink = entry.originalLink,
                        shouldSync = shouldSync,
                        contactType = contactType,
                    )
                }
                withContext(Dispatchers.Main) {
                    profile = setupGuideViewModel.getUserProfileOnce()
                    platforms = buildPlatformList(profile)
                    BadgerLog.d(PLATFORM_TAG, "Platform added: $fieldKey")
                }
            }
        },
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
                // [修复防御]: 编辑时若标识符（value/jumpLink）变化，即使 displayName/avatarUrl 已有值也必须重 sync，
                // 因为改 QQ 号可能指向不同账户，旧 name/avatar 不再有效。
                val identifierChanged = newEntry.value != entry.value || newEntry.jumpLink != entry.jumpLink
                val shouldSync = fieldKey.kindCanSync && (
                    newEntry.displayName.isNullOrBlank() || newEntry.avatarUrl.isNullOrBlank() || identifierChanged
                    )
                setupGuideViewModel.runSync(reason = "edit:$fieldKey") {
                    withContext(BadgerDispatchers.io) {
                        setupGuideViewModel.savePlatformAndMaybeSync(
                            fieldKey = fieldKey,
                            jumpLink = newEntry.jumpLink,
                            value = newEntry.value,
                            displayName = newEntry.displayName,
                            avatarUrl = newEntry.avatarUrl,
                            originalLink = newEntry.originalLink,
                            shouldSync = shouldSync,
                            contactType = contactType,
                        )
                    }
                    withContext(Dispatchers.Main) {
                        profile = setupGuideViewModel.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
                        BadgerLog.d(PLATFORM_TAG, "Platform updated: $fieldKey")
                    }
                }
            },
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
                val name = deletingPlatformName
                showDeleteDialog = false
                deletingPlatformName = null
                if (name == null) return@DialogButtonRow
                // [修复防御 #B2 DELETE race]: DELETE 必须走 runSync,与 ADD/EDIT 一致地翻 isSyncing 闸,
                // 否则删除唯一平台期间用户可点「继续」推到 page 4,留下 platforms=empty 的脏状态。
                setupGuideViewModel.runSync(reason = "delete:$name") {
                    withContext(BadgerDispatchers.io) {
                        setupGuideViewModel.removePlatform(name)
                    }
                    withContext(Dispatchers.Main) {
                        profile = setupGuideViewModel.getUserProfileOnce()
                        platforms = buildPlatformList(profile)
                        BadgerLog.d(PLATFORM_TAG, "Platform deleted: $name")
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
            val name: String = FIELD_DEF_MAP[key]?.displayName ?: entry.displayName ?: key
            name to entry
        }
        ?.toList() ?: emptyList()
}
