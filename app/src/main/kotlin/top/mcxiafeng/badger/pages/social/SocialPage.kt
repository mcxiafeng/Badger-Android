package top.mcxiafeng.badger.pages.social

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.ShortLinkPrefs
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
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

private const val TAG = "SocialPage"
private enum class EditTarget { NAME, VALUE }
private val PHONE_NUMBER_REGEX = Regex("\\d{11}")

@Composable
fun SocialRoute(
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
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val nfcHandler = remember(activity) {
        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                val act = activity ?: return
                NfcHelper.startWriting(act, uri)
            }

            override fun stopWriting() {
                activity?.let(NfcHelper::stopWriting)
            }
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val avatarPath = uiState.profile?.avatarPath
    val profileName = remember(uiState.profile) { uiState.profile?.name?.takeIf { it.isNotBlank() } }
    val profileBio = remember(uiState.profile) { uiState.profile?.bio?.ifBlank { null } }
    val platforms = uiState.platforms
    val selectedPlatform = platforms.getOrNull(uiState.selectedPlatformIndex)
    val selectedPlatformDef = selectedPlatform?.first?.let { FIELD_DEF_MAP[it] }
    val idLabel = selectedPlatformDef?.inputHint?.let { hint ->
        if (hint.contains("或")) hint.substringBefore("或").trim()
        else hint.ifBlank { selectedPlatformDef.displayName + "号" }
    } ?: (selectedPlatformDef?.displayName?.plus("号") ?: "ID")
    val qrContent = remember(selectedPlatform, selectedPlatformDef) {
        selectedPlatform?.second?.let { entry ->
            when {
                entry.jumpLink.isNotBlank() -> entry.jumpLink
                !entry.value.isNullOrBlank() -> {
                    val value = entry.value
                    if (value.matches(PHONE_NUMBER_REGEX)) {
                        "手机号：$value"
                    } else {
                        "${selectedPlatformDef?.displayName ?: "ID"}：$value"
                    }
                }
                else -> ""
            }
        } ?: ""
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = showOverflowMenu) { showOverflowMenu = false }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onSetNfcSupported(NfcHelper.isNfcSupported(context))
    }
    LaunchedEffect(uiState.showNfcWriteDialog) {
        if (uiState.showNfcWriteDialog) onStartNfcWrite(nfcHandler)
    }
    LaunchedEffect(uiState.nfcWriteState) {
        if (uiState.nfcWriteState == NfcWriteState.SUCCESS) onNfcWriteSuccess(nfcHandler)
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
                            Icons.Filled.Nfc,
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
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
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
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToProfile()
                                    },
                                )
                                DropdownImpl(
                                    text = "短链服务设置",
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        showOverflowMenu = false
                                        onNavigateToSettings()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + BadgerSpacing.sm,
                bottom = paddingValues.calculateBottomPadding() + floatingBarBottomPadding,
            ),
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
            if (platforms.isEmpty()) {
                if (isOnboardingCompleted(context)) {
                    item(key = "hint_empty_platforms") {
                        FirstTimeHint(
                            text = "点击右上角「更多」编辑名片信息",
                            hintKey = "social_empty_platforms",
                            modifier = Modifier.padding(
                                horizontal = BadgerSpacing.lg,
                                vertical = BadgerSpacing.xs,
                            ),
                        )
                    }
                }
                item(key = "platform_empty_card") {
                    PlatformEmptyCard(onNavigateToProfile = onNavigateToProfile)
                }
                return@LazyColumn
            }
            item(key = "platform_chips") {
                PlatformChipsRow(
                    platforms = platforms,
                    selectedPlatformIndex = uiState.selectedPlatformIndex,
                    onSelectPlatform = onSelectPlatform,
                )
            }
            if (selectedPlatform != null) {
                val entry = selectedPlatform.second
                item(key = "platform_info_${selectedPlatform.first}") {
                    PlatformInfoCard(
                        displayName = entry.displayName,
                        value = entry.value,
                        idLabel = idLabel,
                        onEditDisplayName = {
                            editText = entry.displayName ?: ""
                            editTarget = EditTarget.NAME
                        },
                        onEditValue = {
                            editText = entry.value ?: ""
                            editTarget = EditTarget.VALUE
                        },
                    )
                }
                if (qrContent.isNotBlank()) {
                    item(key = "qr_code") {
                        val displayValue = buildString {
                            if (!entry.displayName.isNullOrBlank() && !entry.value.isNullOrBlank()) {
                                append(entry.displayName)
                                append("（")
                                append(entry.value)
                                append("）")
                            } else if (!entry.value.isNullOrBlank()) {
                                append(entry.value)
                            }
                        }
                        Box(
                            Modifier.padding(
                                horizontal = BadgerSpacing.lg,
                                vertical = BadgerSpacing.sm,
                            ),
                        ) {
                            QrCodeCard(
                                content = qrContent,
                                userName = entry.displayName ?: selectedPlatformDef?.displayName ?: selectedPlatform.first,
                                platformName = selectedPlatformDef?.displayName ?: selectedPlatform.first,
                                platformValue = displayValue.ifBlank { null },
                                avatarPath = avatarPath,
                            )
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        BadgerInputDialog(
            show = true,
            title = if (target == EditTarget.NAME) "编辑显示名" else "编辑 ID",
            value = editText,
            onValueChange = { editText = it },
            label = if (target == EditTarget.NAME) "显示名" else idLabel,
            onConfirm = { value ->
                val entry = selectedPlatform?.second
                if (entry != null) {
                    val newDisplayName = if (target == EditTarget.NAME) value else entry.displayName
                    val newValue = if (target == EditTarget.VALUE) value else entry.value
                    onUpdatePlatform(
                        selectedPlatform.first,
                        entry.jumpLink,
                        newValue,
                        newDisplayName,
                        entry.avatarUrl,
                        entry.originalLink,
                    )
                }
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    if (uiState.showNfcWriteDialog) {
        NfcWriteDialog(
            state = uiState.nfcWriteState,
            message = uiState.nfcWriteMessage,
            shortUrl = uiState.shortUrl,
            nfcSupported = uiState.nfcSupported,
            isShortLinkConfigured = ShortLinkPrefs.getLinkId(context).isNotBlank() ||
                ShortLinkPrefs.isCustomEnabled(context) ||
                !isDeveloperMode(context),
            onDismiss = { onDismissNfcWriteDialog(nfcHandler) },
            onRetry = {
                if (NfcHelper.isWriting) nfcHandler.stopWriting()
                onStartNfcWrite(nfcHandler)
            },
            onOpenNfcSettings = { NfcHelper.openNfcSettings(context) },
            onOpenShortLinkSettings = {
                onDismissNfcWriteDialog(nfcHandler)
                onNavigateToSettings()
            },
        )
    }
}
