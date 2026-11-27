package top.mcxiafeng.badger.pages.settings


import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip as avatarClip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.BuildConfig
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.data.setDeveloperMode
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.mcxiafeng.badger.ui.components.mergePagePadding
import top.mcxiafeng.badger.ui.navigation.SettingsPage as SettingsPageRoute
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.mcxiafeng.badger.ui.components.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "AboutPageTester"
private const val GITHUB_AVATAR_EDGE_PX = 128
private const val DEVELOPER_TAP_TARGET = 7
private const val DEVELOPER_TAP_PROGRESS_START = 4
private const val DEVELOPER_TAP_TIMEOUT_MS = 2_000L
private const val PROJECT_URL = "https://github.com/mcxiafeng/Badger-Android"
private const val DEVELOPER_URL = "https://github.com/mcxiafeng"
private const val CONTRIBUTOR_URL = "https://github.com/YuLan888"
private const val UI_DESIGN_CONTRIBUTOR_URL = "https://github.com/MarsDemon0520"

@Composable
internal fun AboutPage(
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPageRoute) -> Unit,
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(Unit) {
        Log.d(TAG, "AboutPage loaded, version=${BuildConfig.VERSION_NAME}")
    }
    Scaffold(topBar = {
        SmallTopAppBar(title = "关于 Badger", scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                Log.d(TAG, "返回关于页来源")
                onBack()
            }, modifier = Modifier.size(BadgerSize.minimumTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        })
    }) { innerPadding ->
        AboutContent(
            devMode = devMode,
            onDevModeChange = onDevModeChange,
            onNavigate = onNavigateToSubPage,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            chromePadding = innerPadding,
        )
    }
}

@Composable
private fun AboutContent(
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
    onNavigate: (SettingsPageRoute) -> Unit,
    modifier: Modifier = Modifier,
    chromePadding: PaddingValues = PaddingValues(),
) {
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current
    LazyColumn(
        modifier = modifier,
        contentPadding = mergePagePadding(chromePadding, PaddingValues(
            start = BadgerSpacing.md,
            end = BadgerSpacing.md,
            top = BadgerSpacing.sm,
            bottom = BadgerSpacing.xl + floatingBarBottomPadding,
        )),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xl),
    ) {
        item(key = "about_brand") { AboutBrand() }
        item(key = "about_version") {
            AboutSection("版本与构建") {
                AboutVersionRow(devMode, onDevModeChange)
                BasicComponent(title = "构建日期", summary = BuildConfig.BUILD_DATE)
            }
        }
        if (devMode) {
            item(key = "about_developer") {
                AboutDeveloperOptions(onDevModeChange, onNavigate)
            }
        }
        item(key = "about_contributors") { AboutContributors() }
        item(key = "about_project") { AboutProjectLinks(onNavigate) }
    }
}

@Composable
private fun AboutBrand() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = BadgerSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.badger_logo),
            contentDescription = "Badger 标志",
            modifier = Modifier.size(BadgerSize.coverIconSize).clip(BadgerShapes.Surface),
        )
        Text("Badger", style = MiuixTheme.textStyles.title2)
        Text(
            "电子名片夹",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            modifier = Modifier.padding(horizontal = BadgerSpacing.lg).semantics { heading() },
        )
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(), content = content)
    }
}

@Composable
private fun AboutVersionRow(devMode: Boolean, onDevModeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(DEVELOPER_TAP_TIMEOUT_MS)
            tapCount = 0
            Log.d(TAG, "开发者模式点击超时，计数已重置")
        }
    }
    AboutVersionNumber(devMode, tapCount) {
        if (devMode) {
            Log.d(TAG, "版本号被点击：开发者模式已打开")
            Toast.makeText(context, "开发者模式已打开", Toast.LENGTH_SHORT).show()
        } else {
            val now = SystemClock.elapsedRealtime()
            tapCount = if (now - lastTapTime > DEVELOPER_TAP_TIMEOUT_MS) 1 else tapCount + 1
            lastTapTime = now
            Log.d(TAG, "开发者模式点击 $tapCount/$DEVELOPER_TAP_TARGET")
            if (tapCount >= DEVELOPER_TAP_TARGET) {
                tapCount = 0
                changeAboutDeveloperMode(context, true, onDevModeChange)
                Toast.makeText(context, "开发者模式已打开", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun AboutVersionNumber(devMode: Boolean, tapCount: Int, onClick: () -> Unit) {
    val showProgress = tapCount >= DEVELOPER_TAP_PROGRESS_START
    BasicComponent(
        title = "版本号",
        summary = when {
            devMode -> "开发者模式已打开"
            showProgress -> "已点击 $tapCount/$DEVELOPER_TAP_TARGET"
            else -> BuildConfig.VERSION_NAME
        },
        summaryColor = if (devMode || showProgress) {
            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary)
        } else {
            BasicComponentDefaults.summaryColor()
        },
        onClick = onClick,
    )
}

@Composable
private fun AboutDeveloperOptions(
    onDevModeChange: (Boolean) -> Unit,
    onNavigate: (SettingsPageRoute) -> Unit,
) {
    val context = LocalContext.current
    AboutSection("开发者选项") {
        SwitchPreference(
            title = "开发者模式",
            summary = "关闭后隐藏开发者专属功能",
            checked = true,
            onCheckedChange = { changeAboutDeveloperMode(context, it, onDevModeChange) },
        )
        AboutRouteEntry("软件日志", "查看应用日志", SettingsPageRoute.AppLog, onNavigate)
    }
}

private fun changeAboutDeveloperMode(context: Context, enabled: Boolean, onChange: (Boolean) -> Unit) {
    setDeveloperMode(context, enabled)
    Log.d(TAG, "开发者模式开关 -> $enabled")
    onChange(enabled)
}

@Composable
private fun AboutContributors() {
    AboutSection("贡献者") {
        AboutContributorEntry("夏枫大笨喵w", "mcxiafeng", DEVELOPER_URL)
        AboutContributorEntry("懒猫的盒子", "YuLan888", CONTRIBUTOR_URL)
        AboutContributorEntry("MarsDemon", "MarsDemon0520", UI_DESIGN_CONTRIBUTOR_URL, R.drawable.contributor_mars_avatar)
    }
}

@Composable
private fun AboutContributorEntry(title: String, handle: String, url: String, localAvatar: Int? = null) {
    val context = LocalContext.current
    ArrowPreference(title = title, summary = "GitHub · $handle", onClick = { openAboutLink(context, url) },
        startAction = { ContributorAvatar(title, url, localAvatar) })
}

@Composable
private fun ContributorAvatar(title: String, url: String, localAvatar: Int?) {
    val modifier = Modifier.padding(end = BadgerSpacing.md).size(BadgerSize.coverSmallAvatarSize).avatarClip(CircleShape)
    val description = "$title 的 GitHub 头像"
    SideEffect { Log.d(TAG, "Contributor avatar; bundled=${localAvatar != null}; circular=true") }
    if (localAvatar != null) {
        Image(painterResource(localAvatar), description, modifier, contentScale = ContentScale.Crop)
    } else {
        val fallback = painterResource(R.drawable.ic_github)
        AsyncImage(model = "$url.png?size=$GITHUB_AVATAR_EDGE_PX", contentDescription = description, modifier = modifier,
            contentScale = ContentScale.Crop, placeholder = fallback, error = fallback)
    }
}

@Composable
private fun AboutProjectLinks(onNavigate: (SettingsPageRoute) -> Unit) {
    AboutSection("项目与支持") {
        AboutExternalLinkEntry("本项目仓库", "GitHub · Badger-Android", PROJECT_URL)
        AboutRouteEntry("开源许可", "查看使用的开源库", SettingsPageRoute.OpenSourceLicense, onNavigate)
        AboutRouteEntry("联系我们", "QQ 群 / Telegram / Matrix", SettingsPageRoute.ContactUs, onNavigate)
    }
}

@Composable
private fun AboutExternalLinkEntry(title: String, summary: String, url: String) {
    val context = LocalContext.current
    ArrowPreference(title = title, summary = summary, onClick = { openAboutLink(context, url) })
}

@Composable
private fun AboutRouteEntry(
    title: String,
    summary: String,
    route: SettingsPageRoute,
    onNavigate: (SettingsPageRoute) -> Unit,
) {
    ArrowPreference(title = title, summary = summary, onClick = {
        Log.d(TAG, "打开关于页入口：$route")
        onNavigate(route)
    })
}

private fun openAboutLink(context: Context, url: String) {
    Log.d(TAG, "打开关于页外部链接：$url")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (error: Exception) {
        Log.e(TAG, "打开关于页外部链接失败：$url", error)
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
