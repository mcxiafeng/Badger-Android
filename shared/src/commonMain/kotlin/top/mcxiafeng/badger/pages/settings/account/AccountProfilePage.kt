package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.ui.components.BadgerInputDialog
import top.mcxiafeng.badger.ui.components.EditServerUrlDialog
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val TAG = "AccountProfilePage"

@Composable
internal fun AccountProfilePage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPage) -> Unit = {},
) {
    val accountViewModel: AccountSettingsViewModel = koinViewModel()
    val accountState by accountViewModel.state.collectAsState()
    val profile by accountViewModel.profile.collectAsState()

    var showEditName by remember { mutableStateOf(false) }
    var showEditBio by remember { mutableStateOf(false) }
    var showEditServerUrl by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var isRefreshingAccount by remember { mutableStateOf(false) }
    var refreshResultMsg by remember { mutableStateOf("重新拉取昵称/简介/国家/地区等资料") }
    val pageScope = rememberCoroutineScope()

    SettingsListScaffold(
        title = SettingsPageRoute.AccountProfile.title,
        onBack = onBack,
    ) {
        
        item(key = "info_card") {
            SettingsGroupCard(
                rows = listOf(
                    {
                        BasicComponent(
                            title = "用户名",
                            summary = accountState.username ?: "—",
                        )
                    },
                    {
                        BasicComponent(
                            title = "角色",
                            summary = accountState.role ?: "普通用户",
                        )
                    },
                    {
                        BasicComponent(
                            title = "服务器地址",
                            summary = accountState.serverUrl,
                        )
                    },
                    {
                        ArrowPreference(
                            title = "修改服务器地址",
                            summary = "保存后即时生效",
                            onClick = {
                                BadgerLog.d(TAG, "Open edit server url dialog")
                                showEditServerUrl = true
                            },
                        )
                    },
                ),
            )
        }

        
        item(key = "actions_card") {
            SettingsGroupCard(
                rows = listOf(
                    {
                        ArrowPreference(
                            title = "修改昵称",
                            summary = profile?.name?.takeIf { it.isNotBlank() } ?: "未设置",
                            onClick = {
                                BadgerLog.d(TAG, "Open edit name dialog")
                                showEditName = true
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "修改简介",
                            summary = profile?.bio?.takeIf { it.isNotBlank() } ?: "未设置",
                            onClick = {
                                BadgerLog.d(TAG, "Open edit bio dialog")
                                showEditBio = true
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "已登录设备",
                            summary = "管理已登录设备 / 注销其它设备",
                            onClick = {
                                BadgerLog.d(TAG, "Navigate to Devices")
                                onNavigateToSubPage(SettingsPageRoute.Devices)
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "修改密码",
                            summary = "修改当前账号密码",
                            onClick = {
                                BadgerLog.d(TAG, "Navigate to ChangePassword")
                                onNavigateToSubPage(SettingsPageRoute.ChangePassword)
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = if (isRefreshingAccount) "正在刷新..." else "刷新账号信息",
                            summary = "重新拉取昵称/简介/国家/地区等资料",
                            enabled = !isRefreshingAccount,
                            onClick = {
                                BadgerLog.d(TAG, "Refresh account info")
                                isRefreshingAccount = true
                                pageScope.launch {
                                    val ok = accountViewModel.refreshAccountInfo()
                                    showToast(if (ok) "账号信息已刷新" else "刷新失败，请检查网络")
                                    isRefreshingAccount = false
                                }
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = if (accountState.isLoggingOut) "正在退出..." else "退出登录",
                            summary = "清除本地凭证",
                            enabled = !accountState.isLoggingOut,
                            onClick = {
                                BadgerLog.d(TAG, "Open logout confirm")
                                showLogoutConfirm = true
                            },
                        )
                    },
                ),
            )
        }
    }

    
    if (showEditName) {
        var editName by remember(profile) { mutableStateOf(profile?.name ?: "") }
        BadgerInputDialog(
            show = true,
            title = "修改昵称",
            value = editName,
            onValueChange = { editName = it },
            label = "昵称",
            confirmText = "保存",
            onConfirm = {
                accountViewModel.updateName(it)
                showEditName = false
            },
            onDismiss = { showEditName = false },
        )
    }

    
    if (showEditBio) {
        var editBio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
        BadgerInputDialog(
            show = true,
            title = "修改简介",
            value = editBio,
            onValueChange = { editBio = it },
            label = "简介",
            confirmText = "保存",
            onConfirm = {
                accountViewModel.updateBio(it)
                showEditBio = false
            },
            onDismiss = { showEditBio = false },
        )
    }

    
    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            isLoggingOut = accountState.isLoggingOut,
            onConfirm = {
                BadgerLog.d(TAG, "Logout confirmed")
                showLogoutConfirm = false
                accountViewModel.logout()
            },
            onDismiss = {
                BadgerLog.d(TAG, "Logout cancelled")
                showLogoutConfirm = false
            },
        )
    }

    
    if (showEditServerUrl) {
        EditServerUrlDialog(
            currentUrl = accountState.serverUrl,
            onConfirm = { newUrl ->
                BadgerLog.d(TAG, "EditServerUrlDialog confirm")
                accountViewModel.updateServerUrl(newUrl)
                showEditServerUrl = false
                showToast("保存成功")
            },
            onDismiss = {
                BadgerLog.d(TAG, "EditServerUrlDialog dismissed")
                showEditServerUrl = false
            },
        )
    }
}
