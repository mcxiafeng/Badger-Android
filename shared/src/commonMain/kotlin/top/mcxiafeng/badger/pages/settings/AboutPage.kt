package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.platform.UrlOpener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.platform.AppIcon
import top.mcxiafeng.badger.platform.AppInfo
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.data.prefs.setDeveloperMode
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.nowMs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AboutPage"

private const val DEV_MODE_TAP_THRESHOLD = 7

private const val DEV_TAP_RESET_MS = 2_000L

private const val AUTHOR_GITHUB_URL = "https://github.com/mcxiafeng"
private const val AUTHOR_AVATAR_URL = "https://avatars.githubusercontent.com/u/50166277?v=4"
private const val TESTER_GITHUB_URL = "https://github.com/YuLan888"
private const val TESTER_AVATAR_URL = "https://avatars.githubusercontent.com/u/287770688?v=4"
private const val PROJECT_REPO_URL = "https://github.com/mcxiafeng/Badger-Android"

@Composable
internal fun AboutPage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPageRoute) -> Unit,
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    val appInfo = remember { KoinComponentBy.get<AppInfo>() }

    
    var devTapCount by remember { mutableIntStateOf(0) }
    var lastDevTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(devTapCount) {
        if (devTapCount == 0) return@LaunchedEffect
        snapshotFlow { devTapCount }.collect { count ->
            if (count > 0) {
                delay(DEV_TAP_RESET_MS)
                devTapCount = 0
            }
        }
    }

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "AboutPage loaded, version=${appInfo.versionName}")
    }

    SettingsListScaffold(title = SettingsPage.About.title, onBack = onBack) {
        
        item(key = "about_header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(modifier = Modifier.size(48.dp).clip(CircleShape))
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                Text("Badger", style = MiuixTheme.textStyles.subtitle)
                Text(
                    text = "v${appInfo.versionName}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }

        
        item(key = "contributors") {
            SettingsGroupCard(
                rows = listOf(
                    {
                        ArrowPreference(
                            title = "夏枫大笨喵w",
                            summary = "项目作者",
                            startAction = {
                                ContactAvatar(
                                    avatarUrl = AUTHOR_AVATAR_URL,
                                    size = 36,
                                    transparentBackground = true,
                                    modifier = Modifier.padding(end = BadgerSpacing.md),
                                )
                            },
                            onClick = { UrlOpener.openUrl(AUTHOR_GITHUB_URL) },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "懒猫的盒子",
                            summary = "有机会帮你测试一下",
                            startAction = {
                                ContactAvatar(
                                    avatarUrl = TESTER_AVATAR_URL,
                                    size = 36,
                                    transparentBackground = true,
                                    modifier = Modifier.padding(end = BadgerSpacing.md),
                                )
                            },
                            onClick = { UrlOpener.openUrl(TESTER_GITHUB_URL) },
                        )
                    },
                ),
            )
        }

        
        item(key = "app_info") {
            val rows = buildList<@Composable () -> Unit> {
                add {
                    BasicComponent(
                        title = "版本号",
                        summary = when {
                            devMode -> "开发者模式已打开"
                            devTapCount >= DEV_MODE_TAP_THRESHOLD - 3 -> "已点击 $devTapCount/$DEV_MODE_TAP_THRESHOLD"
                            else -> appInfo.versionName
                        },
                        summaryColor = if (devMode || devTapCount >= DEV_MODE_TAP_THRESHOLD - 3) {
                            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary)
                        } else {
                            BasicComponentDefaults.summaryColor()
                        },
                        onClick = {
                            BadgerLog.d(TAG, "版本号被点击")
                            if (devMode) {
                                showToast("开发者模式已打开")
                                return@BasicComponent
                            }
                            val now = nowMs()
                            if (now - lastDevTapTime > DEV_TAP_RESET_MS) {
                                devTapCount = 0
                            }
                            lastDevTapTime = now
                            devTapCount++
                            BadgerLog.d(TAG, "开发者模式点击: $devTapCount/$DEV_MODE_TAP_THRESHOLD")
                            if (devTapCount >= DEV_MODE_TAP_THRESHOLD) {
                                devTapCount = 0
                                setDeveloperMode(true)
                                onDevModeChange(true)
                                BadgerLog.d(TAG, "开发者模式已开启")
                                showToast("开发者模式已开启")
                            }
                        },
                    )
                }
                add {
                    BasicComponent(
                        title = "构建日期",
                        summary = appInfo.buildDate,
                    )
                }
                add {
                    BasicComponent(
                        title = "构建版本号",
                        summary = "${appInfo.versionCode}",
                    )
                }
                add {
                    
                    BasicComponent(
                        title = "数据库版本",
                        summary = AppDatabase.DB_VERSION.toString(),
                    )
                }
                if (devMode) {
                    add {
                        SwitchPreference(
                            title = "开发者模式",
                            summary = "关闭后隐藏开发者专属功能",
                            checked = true,
                            onCheckedChange = { newValue ->
                                setDeveloperMode(newValue)
                                onDevModeChange(newValue)
                                BadgerLog.d(TAG, "开发者模式开关: -> $newValue")
                            },
                        )
                    }
                    add {
                        ArrowPreference(
                            title = "软件日志",
                            summary = "查看应用日志",
                            onClick = { onNavigateToSubPage(SettingsPageRoute.AppLog) },
                        )
                    }
                }
            }
            SettingsGroupCard(rows = rows)
        }

        
        item(key = "links") {
            SettingsGroupCard(
                rows = listOf(
                    {
                        ArrowPreference(
                            title = "本项目仓库",
                            summary = "点击打开 GitHub 仓库",
                            onClick = {
                                BadgerLog.d(TAG, "Open Project URL: $PROJECT_REPO_URL")
                                if (!UrlOpener.openUrl(PROJECT_REPO_URL)) {
                                    BadgerLog.w(TAG, "Open Project URL Failed")
                                }
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "开源许可",
                            summary = "查看使用的开源库",
                            onClick = { onNavigateToSubPage(SettingsPageRoute.OpenSourceLicense) },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "联系我们",
                            summary = "QQ 群 / Telegram / Matrix",
                            onClick = {
                                BadgerLog.d(TAG, "Navigate to ContactUs")
                                onNavigateToSubPage(SettingsPageRoute.ContactUs)
                            },
                        )
                    },
                ),
            )
        }
    }
}
