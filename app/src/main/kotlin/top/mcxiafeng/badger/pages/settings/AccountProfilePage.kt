package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.DialogButtonRow
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

private const val TAG = "AccountProfilePage"

/**
 * 「个人信息」二级页。
 *
 * 入口：设置主页顶部大卡片（已登录时点击）。
 *
 * 内容：
 *   - 信息区：用户名 / 角色 / 服务器地址（来自 AuthPrefs + JWT role）
 *   - 操作区：修改昵称 / 修改简介 / 修改密码（开发中） / 退出登录
 *
 * 修改昵称 + 修改简介：分别弹 dialog，存到 UserProfile 表。
 * 修改密码：服务器侧暂无 API，弹 toast 占位。
 * 退出登录：复用 [LogoutConfirmDialog] + [AccountSettingsViewModel.logout]。
 */
@Composable
internal fun AccountProfilePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val accountViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountState by accountViewModel.state.collectAsState()

    val userProfileViewModel: UserProfileDetailViewModel = hiltViewModel()
    val userProfileRepository = userProfileViewModel.userProfileRepository

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    LaunchedEffect(Unit) {
        Log.d(TAG, "AccountProfilePage loaded")
        profile = userProfileRepository.getUserProfileOnce()
    }

    var showEditName by remember { mutableStateOf(false) }
    var showEditBio by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "个人信息",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp + floatingBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            Log.d(TAG, "Open edit name dialog")
                            showEditName = true
                        },
                    )
                    ArrowPreference(
                        title = "修改简介",
                        summary = profile?.bio?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = {
                            Log.d(TAG, "Open edit bio dialog")
                            showEditBio = true
                        },
                    )
                    ArrowPreference(
                        title = "修改密码",
                        summary = "暂未实现",
                        onClick = {
                            Log.d(TAG, "Change password clicked: not implemented")
                            Toast.makeText(context, "修改密码功能开发中", Toast.LENGTH_SHORT).show()
                        },
                    )
                    ArrowPreference(
                        title = if (accountState.isLoggingOut) "正在退出..." else "退出登录",
                        summary = "清除本地凭证",
                        enabled = !accountState.isLoggingOut,
                        onClick = {
                            Log.d(TAG, "Open logout confirm")
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
                        Log.d(TAG, "Save new name: $editName")
                        val newName = editName.ifBlank { "用户" }
                        scope.launch(Dispatchers.IO) {
                            // [修复防御]: 从 DB 重新读取最新 profile,避免用过时的 UI 快照覆盖并发修改
                            val current = userProfileRepository.getUserProfileOnce()
                                ?: UserProfile(
                                    name = "用户",
                                    updateTime = System.currentTimeMillis(),
                                )
                            val updated = current.copy(
                                name = newName,
                                updateTime = System.currentTimeMillis(),
                            )
                            userProfileRepository.saveUserProfile(updated)
                            withContext(Dispatchers.Main) {
                                profile = userProfileRepository.getUserProfileOnce() ?: updated
                            }
                        }
                        showEditName = false
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
                        Log.d(TAG, "Save new bio")
                        val newBio = editBio.ifBlank { null }
                        scope.launch(Dispatchers.IO) {
                            val current = userProfileRepository.getUserProfileOnce()
                                ?: UserProfile(
                                    name = "用户",
                                    updateTime = System.currentTimeMillis(),
                                )
                            val updated = current.copy(
                                bio = newBio,
                                updateTime = System.currentTimeMillis(),
                            )
                            userProfileRepository.saveUserProfile(updated)
                            withContext(Dispatchers.Main) {
                                profile = userProfileRepository.getUserProfileOnce() ?: updated
                            }
                        }
                        showEditBio = false
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
                Log.d(TAG, "Logout confirmed")
                showLogoutConfirm = false
                accountViewModel.logout()
                // App 守卫会自动跳 Route.Login
            },
            onDismiss = {
                Log.d(TAG, "Logout cancelled")
                showLogoutConfirm = false
            },
        )
    }
}