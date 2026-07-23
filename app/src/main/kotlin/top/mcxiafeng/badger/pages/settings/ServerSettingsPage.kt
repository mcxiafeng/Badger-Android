package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val TAG = "ServerSettings"

/**
 * 「服务器设置」一级界面。
 *
 * 内容（按需求,只有两项,服务器地址 + 修改服务器地址）：
 *   - BasicComponent:服务器地址(只读)
 *   - ArrowPreference:修改服务器地址(弹 EditServerUrlDialog)
 */
@Composable
internal fun ServerSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val homeViewModel: SettingsHomeViewModel = hiltViewModel()
    val homeState by homeViewModel.state.collectAsState()
    val accountViewModel: AccountSettingsViewModel = hiltViewModel()

    var showEditServerUrl by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "服务器设置",
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp + floatingBarBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "server_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = "服务器地址",
                        summary = homeState.serverUrl,
                    )
                    ArrowPreference(
                        title = "修改服务器地址",
                        summary = "登录与备份共用,保存后即时生效",
                        onClick = {
                            Log.d(TAG, "Open edit server url dialog")
                            showEditServerUrl = true
                        },
                    )
                }
            }
        }
    }

    if (showEditServerUrl) {
        EditServerUrlDialog(
            currentUrl = homeState.serverUrl,
            onConfirm = { newUrl ->
                Log.d(TAG, "EditServerUrlDialog confirm: $newUrl")
                accountViewModel.updateServerUrl(newUrl)
                showEditServerUrl = false
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                Log.d(TAG, "EditServerUrlDialog dismissed")
                showEditServerUrl = false
            },
        )
    }
}