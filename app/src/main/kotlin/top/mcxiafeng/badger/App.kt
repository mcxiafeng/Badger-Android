package top.mcxiafeng.badger

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.prefs.isDeveloperMode
import top.mcxiafeng.badger.data.prefs.isOnboardingCompleted
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.NavTransitions
import top.mcxiafeng.badger.ui.navigation.NavigationDirection
import top.mcxiafeng.badger.ui.navigation.Route
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop


/**
 * 应用主界面
 *
 * 使用 HorizontalPager + NavigationBar 实现 4 个 Tab 页的切换：
 * - Tab 0: 我的名片页 [SocialPage] - 展示个人社交二维码和联系方式
 * - Tab 1: 联系人页 [PersonPage] - 联系人列表管理
 * - Tab 2: 名片夹页 [CardPage] - 名片夹分组管理
 * - Tab 3: 更多/设置页 [SettingsPage] - 应用设置
 *
 * 二级页面（扫描页、联系人详情页）完全覆盖一级界面，互不干扰。
 * 导航状态由 [AppNavigator] 管理，基于 [Route] sealed class 实现类型安全路由。
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
@Preview
fun App() {

    val tabs = listOf("我的名片","联系人","名片夹","设置")
    val icons = listOf(Icons.Filled.QrCodeScanner, Icons.Filled.Person, Icons.Filled.Folder, Icons.Filled.Settings)
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()

    val navigator = remember { AppNavigator() }
    val route by navigator.currentRoute.collectAsState()

    // 关键：SaveableStateHolder 必须在 AnimatedContent 之上创建，让子页面在 AnimatedContent
    // 切换（push/pop 详情页）时仍能保存 rememberSaveable 状态（如 LazyListState）。
    // 否则 push 到 ContactDetailPage 时 PersonRoute 整个被卸载，rememberSaveable 找不到
    // 父 SavedStateRegistry，scrollToItem 恢复失败，回到顶部。
    val saveableStateHolder = rememberSaveableStateHolder()

    val appViewModel: AppViewModel = koinViewModel()
    val userProfileRepository = appViewModel.userProfileRepository
    val userAuthRepository = appViewModel.userAuthRepository
    val unreadNotificationCount by appViewModel.unreadNotificationCount.collectAsState()
    val appContext = LocalContext.current

    var devMode by remember { mutableStateOf(isDeveloperMode(appContext)) }

    // [C3] Deep Link 处理
    val contactRepository = appViewModel.contactRepository

    /** [C3] 解析 serverId → 导航到联系人详情。 */
    suspend fun resolveDeepLink(serverId: String) {
        Log.d("App", "Processing deep link for serverId: $serverId")
        val contact = withContext(Dispatchers.IO) {
            contactRepository.getContactByServerId(serverId)
        }
        if (contact != null) {
            Log.d("App", "Deep link resolved to contactId: ${contact.id}")
            navigator.navigate(Route.ContactDetail(contact.id))
        } else {
            Log.w("App", "Deep link: contact not found for serverId: $serverId")
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "未找到该联系人", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 冷启动：消费 MainActivity.onCreate 时设置的 pendingDeepLinkServerId
    LaunchedEffect(Unit) {
        val activity = appContext as? MainActivity ?: return@LaunchedEffect
        val serverId = activity.consumeDeepLink() ?: return@LaunchedEffect
        resolveDeepLink(serverId)
    }

    // 热启动：消费 onNewIntent → MainActivity SharedFlow
    LaunchedEffect(Unit) {
        val activity = appContext as? MainActivity ?: return@LaunchedEffect
        activity.deepLinkEvents.collect { serverId ->
            resolveDeepLink(serverId)
        }
    }

    // 首次启动检查
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted(appContext)) }
    if (!onboardingCompleted) {
        SetupGuideRoute(onComplete = {
            onboardingCompleted = true
            Log.d("App", "Setup guide completed, showing main app")
        })
        return
    }

    // [修复防御]: 启动期仅做"等待 bootstrap"的 splash，不做 auth gate。
    // 老逻辑:监听 authState,SignedOut 时强制跳 Route.Login —— 与项目"本地优先"理念冲突,
    // 用户首次启动只想用本地功能也会被强行弹登录。新逻辑:onboarding 完成即放行,
    // 未登录用户也能用全部本地功能;登录入口只在设置页顶部"未登录"卡片提供。
    val authState by userAuthRepository.state.collectAsState()
    if (authState is AuthState.Unknown) {
        // Splash-equivalent: render nothing while we hit /refresh.
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(initial = true)
    val liquidGlassEnabled by NavBarConfig.liquidGlassFlow.collectAsState(initial = true)
    val blurIntensity by NavBarConfig.blurIntensityFlow.collectAsState(initial = BlurIntensity.THICK)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.BG_BLUR)

    // GPU 兼容性检测
    val gpuAdvancedSupported = remember { GpuCompat.isAdvancedBlurSupported(appContext) }
    val effectiveAdvancedBlur = advancedBlurEnabled && gpuAdvancedSupported

    // HazeState：所有路径共用（Haze 作为 GPU 不兼容时的 fallback）
    val hazeState = rememberHazeState()

    // LayerBackdrop：仅 GPU 兼容时有效
    val backdrop: LayerBackdrop? = if (effectiveAdvancedBlur) rememberLayerBackdrop() else null

    // 后台/前台生命周期管理：ON_STOP 时禁用模糊节省资源
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    hazeState.blurEnabled = false
                }
                Lifecycle.Event.ON_START -> {
                    hazeState.blurEnabled = liquidGlassEnabled
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // isScrolling 状态
    var isScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            isScrolling = scrolling
        }
    }

    // 安全返回：路由栈空时回退到主页
    fun safeNavigateBack() {
        if (!navigator.navigateBack()) {
            navigator.resetToMain()
        }
    }

    // MainTabs 始终在 composition 中 — 通过 AnimatedContent 统一管理所有页面
    val isFloatingMode = floatingEnabled

    Box(modifier = Modifier.fillMaxSize()) {
        // 全部页面过渡动画 — AnimatedContent 支持动画中断时从当前视觉状态平滑衔接
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState is Route.MainTabs && initialState !is Route.MainTabs) {
                    NavTransitions.subToMain()
                } else if (targetState !is Route.MainTabs && initialState is Route.MainTabs) {
                    NavTransitions.mainToSub()
                } else if (targetState !is Route.MainTabs && initialState !is Route.MainTabs) {
                    when (navigator.navigationDirection) {
                        NavigationDirection.FORWARD -> NavTransitions.push()
                        NavigationDirection.BACKWARD -> NavTransitions.pop()
                        NavigationDirection.RESET -> NavTransitions.reset()
                    }
                } else {
                    NavTransitions.none()
                }
            }
        ) { currentRoute ->
            if (currentRoute is Route.MainTabs) {
                // 用 SaveableStateProvider 把 MainTabs 子树固定到 key="MainTabs"，
                // 让内部 PersonRoute 的 rememberSaveable(LazyListState) 能跨详情页 push/pop 保留。
                saveableStateHolder.SaveableStateProvider(key = "MainTabs") {
                    MainTabsContent(
                        pagerState = pagerState,
                        scope = scope,
                        tabs = tabs,
                        icons = icons,
                        isFloatingMode = isFloatingMode,
                        floatingEnabled = floatingEnabled,
                        liquidGlassEnabled = liquidGlassEnabled,
                        hazeState = hazeState,
                        backdrop = backdrop,
                        blurIntensity = blurIntensity,
                        effectMode = effectMode,
                        isScrolling = isScrolling,
                        route = route,
                        navigator = navigator,
                        devMode = devMode,
                        onDevModeChange = { devMode = it },
                        unreadNotificationCount = unreadNotificationCount,
                    )
                }
            } else {
                AppSubRouteContent(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    onNavigateBack = { safeNavigateBack() },
                    scope = scope,
                    appContext = appContext,
                    userProfileRepository = userProfileRepository,
                    pagerState = pagerState,
                    onRefreshUserProfile = { appViewModel.refreshUserProfile() },
                    devMode = devMode,
                    onDevModeChange = { devMode = it },
                )
            }
        }
    } // Box
}

