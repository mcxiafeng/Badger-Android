package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.prefs.isOnboardingCompleted
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.platform.NfcWriter
import top.mcxiafeng.badger.ui.components.BadgerFloatingBarList
import top.mcxiafeng.badger.ui.components.badgerListContentPadding
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Nfc
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.BackHandler

private const val TAG = "SocialPage"

private enum class EditTarget { NAME, VALUE }

private data class PlatformEditContext(
    val fieldKey: String,
    val entry: PlatformEntry,
    val target: EditTarget,
)

@Composable
fun SocialRoute(
    @Suppress("UNUSED_PARAMETER") navigateToContacts: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val viewModel: SocialViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SocialScreen(
        uiState = uiState,
        onSelectPlatform = viewModel::selectPlatform,
        onSetNfcSupported = viewModel::setNfcSupported,
        onShowNfcWriteDialog = viewModel::showNfcWriteDialog,
        onDismissNfcWriteDialog = viewModel::dismissNfcWriteDialog,
        onStartNfcWrite = viewModel::startNfcWrite,
        onNfcWriteSuccess = viewModel::onNfcWriteSuccess,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onUpdatePlatform = viewModel::addOrUpdatePlatform,
    )
}

@Composable
fun SocialScreen(
    uiState: SocialUiState,
    onSelectPlatform: (Int) -> Unit = {},
    onSetNfcSupported: (Boolean) -> Unit = {},
    onShowNfcWriteDialog: () -> Unit = {},
    onDismissNfcWriteDialog: (NfcActivityHandler) -> Unit = {},
    onStartNfcWrite: (NfcActivityHandler) -> Unit = {},
    onNfcWriteSuccess: (NfcActivityHandler) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onUpdatePlatform: (String, String, String?, String?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
) {
    
    
    
    
    
    val nfcWriter = remember { KoinComponentBy.get<NfcWriter>() }
    DisposableEffect(Unit) {
        onDispose {  }
    }
    val nfcHandler = remember {
        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                nfcWriter.startWriting(uri)
            }
            override fun stopWriting() {
                nfcWriter.stopWriting()
            }
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    
    val avatarPath = uiState.profile?.avatarPath

    
    val profileName = remember(uiState.profile) {
        uiState.profile?.name?.takeIf { it.isNotBlank() }
    }
    val profileBio = remember(uiState.profile) {
        uiState.profile?.bio?.ifBlank { null }
    }

    
    val platforms = uiState.platforms
    val selectedPlatform = platforms.getOrNull(uiState.selectedPlatformIndex)

    
    var showOverflowMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = showOverflowMenu) { showOverflowMenu = false }

    
    
    var editContext by remember { mutableStateOf<PlatformEditContext?>(null) }
    var editText by remember { mutableStateOf("") }

    
    LaunchedEffect(Unit) {
        onSetNfcSupported(nfcWriter.isSupported())
    }

    
    LaunchedEffect(uiState.showNfcWriteDialog) {
        if (uiState.showNfcWriteDialog) {
            onStartNfcWrite(nfcHandler)
        }
    }

    
    LaunchedEffect(uiState.nfcWriteState) {
        if (uiState.nfcWriteState == NfcWriteState.SUCCESS) {
            onNfcWriteSuccess(nfcHandler)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "我的名片",
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    
                    IconButton(
                        onClick = onShowNfcWriteDialog,
                        enabled = uiState.nfcSupported && selectedPlatform != null,
                    ) {
                        Icon(
                            imageVector = Lucide.Nfc,
                            contentDescription = "写入 NFC 标签",
                            tint = if (uiState.nfcSupported && selectedPlatform != null) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Lucide.EllipsisVertical,
                                contentDescription = "更多",
                            )
                        }
                        OverlayListPopup(
                            show = showOverflowMenu,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "编辑名片信息",
                                    optionSize = 1,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToProfile()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val contentPadding = badgerListContentPadding(
            scaffoldTop = paddingValues.calculateTopPadding(),
            scaffoldBottom = paddingValues.calculateBottomPadding(),
            topExtra = BadgerSpacing.sm,
        )

        if (platforms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                SocialProfileHeader(
                    profileName = profileName,
                    profileBio = profileBio,
                    avatarPath = avatarPath,
                    linkUpdateState = uiState.linkUpdateState,
                    onEditProfile = onNavigateToProfile,
                )
                if (isOnboardingCompleted()) {
                    FirstTimeHint(
                        text = "点击右上角「更多」可编辑名片信息",
                        hintKey = "social_empty_platforms",
                        modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.xs),
                    )
                }
                PlatformEmptyCard(onNavigateToProfile = onNavigateToProfile)
            }
        } else {
            BadgerFloatingBarList(
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                item(key = "profile_header") {
                    SocialProfileHeader(
                        profileName = profileName,
                        profileBio = profileBio,
                        avatarPath = avatarPath,
                        linkUpdateState = uiState.linkUpdateState,
                        onEditProfile = onNavigateToProfile,
                    )
                }

                item(key = "platform_chips") {
                    PlatformChipsRow(
                        platforms = platforms,
                        selectedPlatformIndex = uiState.selectedPlatformIndex,
                        onSelectPlatform = onSelectPlatform,
                    )
                }

                
                item(key = "platform_content") {
                    PlatformContent(
                        platforms = platforms,
                        selectedPlatformIndex = uiState.selectedPlatformIndex,
                        avatarPath = avatarPath,
                        userName = profileName,
                        onEditDisplayName = { fieldKey, entry ->
                            editText = entry.displayName ?: ""
                            editContext = PlatformEditContext(fieldKey, entry, EditTarget.NAME)
                        },
                        onEditValue = { fieldKey, entry ->
                            editText = entry.value ?: ""
                            editContext = PlatformEditContext(fieldKey, entry, EditTarget.VALUE)
                        },
                    )
                }
            }
        }
    }

    

    
    val ctx = editContext
    if (ctx != null) {
        val idLabel = idLabelFor(ctx.fieldKey)
        val dialogTitle = when (ctx.target) {
            EditTarget.NAME -> "编辑平台昵称"
            EditTarget.VALUE -> "编辑$idLabel"
        }
        val fieldLabel = when (ctx.target) {
            EditTarget.NAME -> "平台昵称"
            EditTarget.VALUE -> idLabel
        }
        BadgerInputDialog(
            show = true,
            title = dialogTitle,
            value = editText,
            onValueChange = { editText = it },
            label = fieldLabel,
            onConfirm = {
                val newDisplayName = if (ctx.target == EditTarget.NAME) {
                    editText.trim().ifBlank { null }
                } else ctx.entry.displayName
                val newValue = if (ctx.target == EditTarget.VALUE) {
                    editText.trim().ifBlank { null }
                } else ctx.entry.value
                onUpdatePlatform(
                    ctx.fieldKey,
                    ctx.entry.jumpLink,
                    newValue,
                    newDisplayName,
                    ctx.entry.avatarUrl,
                    ctx.entry.originalLink,
                )
                BadgerLog.d(TAG, "更新: key=${ctx.fieldKey}, target=${ctx.target}, value=$editText")
                editContext = null
            },
            onDismiss = { editContext = null },
        )
    }

    
    if (uiState.showNfcWriteDialog) {
        NfcWriteDialog(
            state = uiState.nfcWriteState,
            message = uiState.nfcWriteMessage,
            shortUrl = uiState.shortUrl,
            nfcSupported = uiState.nfcSupported,
            isShortLinkConfigured = uiState.shortLinkConfigured,
            onDismiss = { onDismissNfcWriteDialog(nfcHandler) },
            onRetry = {
                
                if (nfcWriter.isWriting) nfcHandler.stopWriting()
                onStartNfcWrite(nfcHandler)
            },
            onOpenNfcSettings = { nfcWriter.openNfcSettings() },
            onOpenShortLinkSettings = {
                onDismissNfcWriteDialog(nfcHandler)
                onNavigateToSettings()
            },
        )
    }
}
