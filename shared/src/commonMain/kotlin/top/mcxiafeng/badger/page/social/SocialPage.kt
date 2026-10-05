package top.mcxiafeng.badger.page.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.data.user.entity.Platform
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.page.social.dialogs.QrCodeCard
import top.mcxiafeng.badger.ui.components.StatusPanel
import top.mcxiafeng.badger.platform.QrCodeGenerator
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class SocialPage {

    companion object {

        @Composable
        fun PageSocial(
            vm: SocialViewModel = viewModel { SocialViewModel() },
            onSyncClick: () -> Unit,
        ) {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val platformsState by vm.platformsState.collectAsStateWithLifecycle()
            val selectedPlatformIndex by vm.selectedPlatformIndex.collectAsStateWithLifecycle()
            when (val current = state) {
                SocialUiState.Loading -> StatusPanel.onLoading()
                is SocialUiState.Error -> StatusPanel.onError(current.message) { vm.refresh() }
                is SocialUiState.Success -> SocialSuccessPane(
                    state = current,
                    platformList = platformsState,
                    selectedPlatformIndex = selectedPlatformIndex,
                    onSelectPlatform = { vm.selectPlatform(it) },
                    onSyncClick = onSyncClick,
                )
            }
        }

        @Composable
        private fun SocialSuccessPane(
            state: SocialUiState.Success,
            platformList: List<Platform>,
            selectedPlatformIndex: Int,
            onSelectPlatform: (Int) -> Unit,
            onSyncClick: () -> Unit,
        ) {
            val user = state.user
            val profile = state.profile
            val platforms = profile.contact

            val selectedIndex = if (platforms?.isEmpty() == true) {
                -1
            } else {
                selectedPlatformIndex.coerceIn(0, platforms?.lastIndex)
            }
            val selectedPlatform = platforms?.getOrNull(selectedIndex)
            val qrContent = selectedPlatform?.sourceUrl
            var showQrDialog by remember { mutableStateOf(false) }
            val snackbarHostState = remember { SnackbarHostState() }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "主页",
                        actions = {
                            IconButton(onClick = onSyncClick) {
                                Icon(Lucide.CloudSync, contentDescription = "同步")
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .padding(innerPadding)
                        .padding(horizontal = BadgerSpacing.cardPadding),
                    contentPadding = PaddingValues(vertical = BadgerSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
                ) {
                    item { SocialCoverCard(user, profile) }
                    if (!platforms.isNullOrEmpty()) {
                        item {
                            PlatformPickerCard(platforms, selectedIndex, onSelectPlatform, platformList)
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text("没有找到可用平台")
                            }
                        }
                    }
                    item {
                        SocialQrCodeCard(
                            content = "$qrContent",
                            onClick = { showQrDialog = true }
                        )
                    }
                }
                val targetPlatform = platformList.firstOrNull { value ->
                    value.name == selectedPlatform?.platformName
                }
                val platformName = targetPlatform?.displayName ?: selectedPlatform?.platformName
                QrCodeCard(
                    userName = user.displayName,
                    platformName = platformName,
                    platformValue = selectedPlatform?.sourceUrl,
                    avatarPath = user.avatar,
                    externalShowDialog = showQrDialog,
                    onDialogDismiss = { showQrDialog = false }
                )
            }
        }

        @Composable
        private fun SocialCoverCard(user: User, profile: Profile) {
            val cs = MiuixTheme.colorScheme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BadgerSize.coverHeight)
                    .clip(miuixShape(BadgerRadius.card))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                cs.primary,
                                cs.primary.copy(alpha = BadgerAlpha.COVER_GRADIENT_END_ALPHA),
                            ),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(BadgerSpacing.cardPadding),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContactAvatar(
                            name = user.displayName,
                            avatarPath = user.avatar,
                            avatarUrl = user.avatar,
                            size = BadgerSize.coverAvatarSize.value.toInt(),
                        )
                        Column {
                            Text(
                                text = user.displayName,
                                style = MiuixTheme.textStyles.title4,
                                color = cs.onPrimary,
                            )
                            Text(
                                text = "@${user.name}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = cs.onPrimary.copy(alpha = BadgerAlpha.AVATAR_TEXT_ALPHA),
                            )
                        }
                    }
                    profile.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MiuixTheme.textStyles.body2,
                            color = cs.onPrimary.copy(alpha = BadgerAlpha.AVATAR_TEXT_ALPHA),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = BadgerSpacing.md),
                        )
                    }
                }
            }
        }

        @Composable
        private fun PlatformPickerCard(
            platforms: List<Contact>,
            selectedIndex: Int,
            onSelectPlatform: (Int) -> Unit,
            platformList: List<Platform>?
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "名片平台",
                    entry = DropdownEntry(
                        items = platforms.mapIndexed { index, entry ->
                            BadgerLog.i("测试",entry.toString())
                            val targetPlatform = platformList?.firstOrNull { value ->
                                value.name == entry.platformName
                            }
                            val platformName = targetPlatform?.displayName ?: entry.platformName
                            DropdownItem(
                                text = platformName,
                                selected = index == selectedIndex,
                                onClick = { onSelectPlatform(index) },
                            )
                        },
                    ),
                )
            }
        }

        @Composable
        private fun SocialQrCodeCard(
            content: String,
            onClick: () -> Unit,
        ) {
            val primaryColor = MiuixTheme.colorScheme.primary
            val surfaceContainerColor = MiuixTheme.colorScheme.surfaceContainer

            val qrBytes = remember(content, primaryColor) {
                QrCodeGenerator.generateBytes(
                    content = content,
                    sizePx = 512,
                    foregroundColor = primaryColor.toArgb(),
                    backgroundColor = 0x00000000,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        color = surfaceContainerColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (qrBytes != null) {
                    AsyncImage(
                        model = qrBytes,
                        contentDescription = "二维码",
                        modifier = Modifier.size(200.dp),
                    )
                } else {
                    Text(
                        text = "暂无二维码数据",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
