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

@Composable
internal fun SetupStepPlatforms(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()
    val isSyncing by setupGuideViewModel.isSyncing.collectAsState()
    val profile by setupGuideViewModel.profile.collectAsState()
    val platforms = remember(profile) { buildPlatformList(profile) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Pair<String, PlatformEntry>?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPlatformName by remember { mutableStateOf<String?>(null) }

    
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

    
    if (showAddDialog) AddPlatformWindowDialog(
        show = true,
        mode = AddEditMode.ADD,
        existingProfile = profile,
        onDismiss = { showAddDialog = false },
        onConfirm = { fieldKey, entry ->
            showAddDialog = false
            val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
            
            val shouldSync = fieldKey.kindCanSync &&
                (entry.displayName.isNullOrBlank() || entry.avatarUrl.isNullOrBlank())
            
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
                BadgerLog.d(PLATFORM_TAG, "Platform added: $fieldKey")
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
                val contactType = FIELD_DEF_MAP[fieldKey]?.contactType
                
                
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
                    BadgerLog.d(PLATFORM_TAG, "Platform updated: $fieldKey")
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
                    withContext(BadgerDispatchers.io) {
                        setupGuideViewModel.removePlatform(name)
                    }
                    BadgerLog.d(PLATFORM_TAG, "Platform deleted: $name")
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
