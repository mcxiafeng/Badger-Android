package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.data.AppearanceAccent
import top.mcxiafeng.badger.data.AppearanceMode
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.PageActionBar
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "MyPageTester"
private const val AVATAR_BACKGROUND_ALPHA = 0.12f

@Composable
fun MyRoute(
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onManageCollections: () -> Unit,
    onManageTags: () -> Unit,
    onAbout: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val appearanceState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    MyPage(
        accountState, onSettings, onAccount, onManageCollections, onManageTags, onAbout,
        appearanceState, appearanceViewModel::setMode, appearanceViewModel::setAccent,
    )
}

@Composable
internal fun MyPage(
    accountState: SettingsAccountState,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onManageCollections: () -> Unit,
    onManageTags: () -> Unit,
    onAbout: () -> Unit,
    appearanceState: AppearanceUiState = AppearanceUiState(),
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    onAppearanceAccentChange: (AppearanceAccent) -> Unit = {},
) {
    Scaffold(progressiveTopBlur = true, topBar = { MyTopBar(onSettings) }) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = BadgerSize.contentMaxWidth).fillMaxSize().scrollTopFade().springOverscroll(),
                contentPadding = PaddingValues(
                    start = BadgerSpacing.md, end = BadgerSpacing.md,
                    top = innerPadding.calculateTopPadding() + BadgerSpacing.sm + BadgerSpacing.scrollContentTopGap,
                    bottom = BadgerSpacing.sm + innerPadding.calculateBottomPadding() + LocalFloatingBarBottomPadding.current,
                ),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
            ) {
                item(key = "account_header") { MyAccountHeader(accountState, onAccount) }
                item(key = "organize") {
                    Card(insideMargin = PaddingValues()) {
                        MyEntry("管理名片夹", Icons.Outlined.CreditCard, onManageCollections)
                        MyEntry("标签管理", Icons.AutoMirrored.Outlined.Label, onManageTags)
                    }
                }
                item(key = "appearance") {
                    AppearanceSettings(appearanceState, onAppearanceModeChange, onAppearanceAccentChange)
                }
                item(key = "about_card") {
                    Card(insideMargin = PaddingValues()) {
                        MyEntry("关于 Badger", Icons.Outlined.Info, onAbout)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTopBar(onSettings: () -> Unit) {
    PageActionBar(title = "我的", actions = {
        IconButton(onClick = {
            Log.d(TAG, "从我的页面打开设置")
            onSettings()
        }, modifier = Modifier.size(BadgerSize.minimumTouchTarget)) {
            Icon(Icons.Outlined.Settings, contentDescription = "设置")
        }
    })
}

@Composable
private fun MyEntry(title: String, icon: ImageVector, onClick: () -> Unit) {
    ArrowPreference(
        title = title,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = BadgerSpacing.md),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = {
            Log.d(TAG, "从我的页面打开$title")
            onClick()
        },
    )
}

@Composable
private fun MyAccountHeader(account: SettingsAccountState, onAccount: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "账号与同步") {
                Log.d(TAG, "从我的页面打开账号与同步")
                onAccount()
            }
            .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.displayName, style = MiuixTheme.textStyles.title3)
            Text(
                text = account.subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun AccountAvatar(account: SettingsAccountState) {
    Box(
        modifier = Modifier.size(BadgerSize.coverIconSize)
            .clip(BadgerShapes.Surface)
            .background(
                if (account.loggedIn && account.avatarInitial != null)
                    MiuixTheme.colorScheme.primary.copy(alpha = AVATAR_BACKGROUND_ALPHA)
                else MiuixTheme.colorScheme.surfaceContainer
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (account.loggedIn && account.avatarInitial != null) {
            Text(
                text = account.avatarInitial,
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(BadgerSize.coverIconSize / 2),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}
