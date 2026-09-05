package top.mcxiafeng.badger.pages.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

private const val TAG = "AccountProfilePage"

/**
 * 「个人信息」二级页。
 *
 * 入口：设置主页顶部大卡片（已登录时点击）。
 *
 * 内容：
 *   - 信息区：用户名 / 角色 / 服务器地址 + 修改服务器地址
 *   - 操作区：修改昵称 / 修改简介 / 已登录设备 / 退出登录
 *
 * 修改昵称 + 修改简介：分别弹 dialog，存到 UserProfile 表。
 * 修改服务器地址：复用 [EditServerUrlDialog] + [AccountSettingsViewModel.updateServerUrl]。
 * 退出登录：复用 [LogoutConfirmDialog] + [AccountSettingsViewModel.logout]。
 */
@Composable
internal fun AccountProfilePage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPageRoute) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val accountViewModel: AccountSettingsViewModel = koinViewModel()
    val accountState by accountViewModel.state.collectAsState()

    val userProfileViewModel: UserProfileDetailViewModel = koinViewModel()
    val userProfileRepository = userProfileViewModel.userProfileRepository

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "AccountProfilePage loaded")
        profile = userProfileRepository.getUserProfileOnce()
    }

    var showEditName by remember { mutableStateOf(false) }
    var showEditBio by remember { mutableStateOf(false) }
    var showEditServerUrl by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // [修复防御]: 提取公共的 profile 字段保存逻辑，消除昵称/简介保存的重复代码
    val saveProfileField: (UserProfile.() -> UserProfile, () -> Unit) -> Unit = { transform, onDone ->
        scope.launch(BadgerDispatchers.io) {
            val current = userProfileRepository.getUserProfileOnce()
                ?: UserProfile(name = "用户", updateTime = nowMs())
            val updated = current.transform().copy(updateTime = nowMs())
            userProfileRepository.saveUserProfile(updated)
            withContext(Dispatchers.Main) {
                profile = userProfileRepository.getUserProfileOnce() ?: updated
                onDone()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "个人信息",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = BadgerSpacing.md,
                end = BadgerSpacing.md,
                top = BadgerSpacing.sm,
                bottom = BadgerSpacing.sm + floatingBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        ) {
            // ===== 信息卡 =====
            item(key = "info_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = "用户名",
                        summary = accountState.username ?: "—",
                    )
                    BasicComponent(
                        title = "角色",
                        summary = accountState.role ?: "普通用户",
                    )
                    BasicComponent(
                        title = "服务器地址",
                        summary = accountState.serverUrl,
                    )
                    ArrowPreference(
                        title = "修改服务器地址",
                        summary = "保存后即时生效",
                        onClick = {
                            BadgerLog.d(TAG, "Open edit server url dialog")
                            showEditServerUrl = true
                        },
                    )
                }
            }

            // ===== 操作卡 =====
            item(key = "actions_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "修改昵称",
                        summary = profile?.name?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = {
                            BadgerLog.d(TAG, "Open edit name dialog")
                            showEditName = true
                        },
                    )
                    ArrowPreference(
                        title = "修改简介",
                        summary = profile?.bio?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = {
                            BadgerLog.d(TAG, "Open edit bio dialog")
                            showEditBio = true
                        },
                    )
                    // [B4] 已登录设备入口
                    ArrowPreference(
                        title = "已登录设备",
                        summary = "管理已登录设备 / 注销其它设备",
                        onClick = {
                            BadgerLog.d(TAG, "Navigate to Devices")
                            onNavigateToSubPage(SettingsPageRoute.Devices)
                        },
                    )
                    // 修改密码入口
                    ArrowPreference(
                        title = "修改密码",
                        summary = "修改当前账号密码",
                        onClick = {
                            BadgerLog.d(TAG, "Navigate to ChangePassword")
                            onNavigateToSubPage(SettingsPageRoute.ChangePassword)
                        },
                    )
                    ArrowPreference(
                        title = if (accountState.isLoggingOut) "正在退出..." else "退出登录",
                        summary = "清除本地凭证",
                        enabled = !accountState.isLoggingOut,
                        onClick = {
                            BadgerLog.d(TAG, "Open logout confirm")
                            showLogoutConfirm = true
                        },
                    )
                }
            }
        }
    }

    // ===== 修改昵称 Dialog =====
    if (showEditName) {
        WindowDialog(
            show = true,
            title = "修改昵称",
            summary = "",
            onDismissRequest = { showEditName = false },
        ) {
            var editName by remember(profile) { mutableStateOf(profile?.name ?: "") }
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "昵称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { showEditName = false },
                    onPositive = {
                        BadgerLog.d(TAG, "Save new name: $editName")
                        val newName = editName.ifBlank { "用户" }
                        saveProfileField({ copy(name = newName) }, { showEditName = false })
                    },
                )
            }
        }
    }

    // ===== 修改简介 Dialog =====
    if (showEditBio) {
        WindowDialog(
            show = true,
            title = "修改简介",
            summary = "",
            onDismissRequest = { showEditBio = false },
        ) {
            var editBio by remember(profile) { mutableStateOf(profile?.bio ?: "") }
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = editBio,
                    onValueChange = { editBio = it },
                    label = "简介",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    positiveText = "保存",
                    onNegative = { showEditBio = false },
                    onPositive = {
                        BadgerLog.d(TAG, "Save new bio")
                        val newBio = editBio.ifBlank { null }
                        saveProfileField({ copy(bio = newBio) }, { showEditBio = false })
                    },
                )
            }
        }
    }

    // ===== 退出登录确认 Dialog =====
    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            isLoggingOut = accountState.isLoggingOut,
            onConfirm = {
                BadgerLog.d(TAG, "Logout confirmed")
                showLogoutConfirm = false
                accountViewModel.logout()
                // App 守卫会自动跳 Route.Login
            },
            onDismiss = {
                BadgerLog.d(TAG, "Logout cancelled")
                showLogoutConfirm = false
            },
        )
    }

    // ===== 修改服务器地址 Dialog =====
    if (showEditServerUrl) {
        EditServerUrlDialog(
            currentUrl = accountState.serverUrl,
            onConfirm = { newUrl ->
                BadgerLog.d(TAG, "EditServerUrlDialog confirm: $newUrl")
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