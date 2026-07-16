package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import javax.inject.Inject

private const val TAG = "PlatformListPage"

/**
 * 「联系平台」编辑页。
 *
 * 数据来源：[UserProfileRepository.getUserProfile] 返回的 [top.mcxiafeng.badger.data.UserProfile.platforms]，
 * 该 Map 是当前用户通过扫码 / 手动添加的平台集合，由服务端持久化（详见
 * [top.mcxiafeng.badger.data.repository.UserAuthRepository]）。本页面只做两件事：
 *   1. 把当前已添加的平台以列表形式呈现；
 *   2. 提供删除入口。
 *
 * "添加平台" 跳到我的名片页（ContactDetail(-1L)），由用户在那里扫码/手动编辑后，
 * 修改经 [UserProfileRepository.updatePlatformField] 回写。
 */
@Composable
internal fun PlatformListPage(
    onBack: () -> Unit,
    onNavigateToAdd: () -> Unit = {},
    userProfileRepository: UserProfileRepository = hiltViewModel<PlatformListViewModel>().repository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    val profile by userProfileRepository.getUserProfile().collectAsState(initial = null)
    val platforms: List<Map.Entry<String, PlatformEntry>> =
        profile?.platforms.orEmpty().entries.sortedBy { it.key }

    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val dialogVisible = remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "联系平台",
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
            // ===== 操作区:跳到我的名片新增 =====
            item(key = "add_platform") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "添加平台",
                        summary = "在「我的名片」里扫码或编辑联系方式",
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        onClick = onNavigateToAdd,
                    )
                }
            }

            // ===== 已添加的平台 =====
            item(key = "list_header") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp)) {
                    Text(
                        text = "已添加 ${platforms.size} 个平台",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }

            if (platforms.isEmpty()) {
                item(key = "empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = "还未添加任何平台。点击上方「添加平台」开始建立你的联系方式，或在「我的名片」扫码导入。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            lineHeight = 1.5.em,
                        )
                    }
                }
            } else {
                item(key = "platforms_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        // [修复防御]: platforms 一律按 key 升序展示，删除/新增不会改变其它行位置，
                        // 避免"删除第二项后下面整片上移"导致用户误触到错误的删除按钮。
                        platforms.forEach { (fieldKey, entry) ->
                            val display = FIELD_DEF_MAP[fieldKey]?.displayName
                                ?: entry.displayName
                                ?: fieldKey
                            PlatformRow(
                                title = display,
                                summary = entry.value?.takeIf { it.isNotBlank() } ?: entry.jumpLink,
                                avatarUrl = entry.avatarUrl,
                                onClickDelete = {
                                    Log.d(TAG, "Request delete: fieldKey=$fieldKey")
                                    pendingDelete = fieldKey
                                    dialogVisible.value = true
                                },
                            )
                        }
                    }
                }
            }

            // ===== 说明 =====
            item(key = "help") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "平台信息保存在你的账号服务器上。换设备登录后会自动同步。「删除」只是从你的账号移除，不会取消对方账号或解除关系。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 1.5.em,
                    )
                }
            }
        }
    }

    // ===== 删除确认弹窗 =====
    val toDelete = pendingDelete
    if (toDelete != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogVisible.value = true }
        androidx.compose.runtime.LaunchedEffect(dialogVisible.value) {
            if (!dialogVisible.value) pendingDelete = null
        }
        DialogLayout(
            visible = dialogVisible,
            enableWindowDim = true,
            renderInRootScaffold = true,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    insideMargin = PaddingValues(24.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "删除平台",
                            style = MiuixTheme.textStyles.subtitle,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val name = FIELD_DEF_MAP[toDelete]?.displayName ?: toDelete
                        Text(
                            text = "确定从账号中移除「$name」？",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(
                                text = "取消",
                                onClick = { dialogVisible.value = false },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "删除",
                                onClick = {
                                    Log.d(TAG, "Delete confirmed: fieldKey=$toDelete")
                                    dialogVisible.value = false
                                    scope.launch {
                                        runCatching {
                                            userProfileRepository.removePlatform(toDelete)
                                        }.onFailure {
                                            Log.w(TAG, "removePlatform failed: ${it.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个平台行：左侧圆形头像 + 标题，右侧"删除"文字按钮。
 */
@Composable
private fun PlatformRow(
    title: String,
    summary: String?,
    avatarUrl: String?,
    onClickDelete: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary?.takeIf { it.isNotBlank() } ?: "—",
        startAction = {
            ContactAvatar(
                name = title,
                avatarUrl = avatarUrl,
                size = 36,
                transparentBackground = true,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        endActions = {
            TextButton(
                text = "删除",
                onClick = onClickDelete,
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.error,
                ),
            )
            Spacer(Modifier.size(8.dp))
        },
    )
}

/**
 * 仅用来把 [UserProfileRepository] 从 Hilt 取出来的轻量 VM。
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class PlatformListViewModel @Inject constructor(
    val repository: UserProfileRepository,
) : androidx.lifecycle.ViewModel()
