package top.mcxiafeng.badger

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.NavigationDirection
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.ContactDetailPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.auth.LoginScreen
import top.mcxiafeng.badger.pages.auth.RegisterScreen
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.SettingsPage
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.pages.social.SocialRoute
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.ui.navigation.NavAnimationEasing
import top.mcxiafeng.badger.ui.navigation.NavTransitions
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.formatUnreadBadge
import top.mcxiafeng.badger.ui.blur.BlurIntensity
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.blur.applyBlurSource
import top.mcxiafeng.badger.ui.blur.applyLayerBackdrop
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState


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
    val icons = listOf(MiuixIcons.Scan, MiuixIcons.Contacts, MiuixIcons.Folder, MiuixIcons.Settings)
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
        val contact = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            contactRepository.getContactByServerId(serverId)
        }
        if (contact != null) {
            Log.d("App", "Deep link resolved to contactId: ${contact.id}")
            navigator.navigate(Route.ContactDetail(contact.id))
        } else {
            Log.w("App", "Deep link: contact not found for serverId: $serverId")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, "未找到该联系人", android.widget.Toast.LENGTH_SHORT).show()
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
    if (authState is top.mcxiafeng.badger.data.repository.AuthState.Unknown) {
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
                BackHandler(onBack = { safeNavigateBack() })
                when (currentRoute) {
                    is Route.Login -> {
                        LoginScreen(
                            onAuthed = {
                                navigator.resetToMain()
                            },
                            onNavigateToRegister = { navigator.navigate(Route.Register) },
                            onBack = { safeNavigateBack() },
                            // [V2-E2E #1]: 启动期 server URL 未配置时,推 ServerSettingsPage 引导。
                            onNavigateToServerSettings = {
                                navigator.navigate(Route.SettingsSubPage(SettingsPage.ServerSettings))
                            },
                        )
                    }
                    is Route.Register -> {
                        RegisterScreen(
                            onAuthed = {
                                navigator.resetToMain()
                            },
                            onNavigateToLogin = { navigator.navigate(Route.Login) },
                            onBack = { safeNavigateBack() },
                        )
                    }
                    is Route.Scanner -> {
                        ScannerPage(
                            onBack = { safeNavigateBack() },
                            targetCollectionId = if (currentRoute.mode == "collection") currentRoute.targetCollectionId else null,
                            onNavigateToAiSettings = { navigator.navigate(Route.SettingsSubPage(SettingsPage.NfcSettings)) },
                            onNavigateToCreateContact = {
                                navigator.navigate(Route.CreateContact(targetCollectionId = currentRoute.targetCollectionId))
                            },
                            onImportToProfile = if (currentRoute.mode == "importProfile") { { items ->
                                scope.launch(Dispatchers.IO) {
                                    var importedCount = 0
                                    for ((rawContent, info) in items) {
                                        info.toFieldValues().forEach { (key, value) ->
                                            if (value.isNotBlank() && key != "phone" && key != "email") {
                                                val displayName = FIELD_DEF_MAP[key]?.displayName ?: key
                                                val jumpLink = buildPlatformLink(key, value)
                                                val adapterResult = try {
                                                    ContactNetworkResolver.getResultInfo(jumpLink, mutableMapOf())
                                                } catch (e: Exception) {
                                                    Log.w("App", "导入时平台信息解析失败", e)
                                                    null
                                                }
                                                val platformName = adapterResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                                                val platformAvatar = adapterResult?.avatarUrl?.takeIf { it.isNotBlank() }
                                                userProfileRepository.updatePlatformField(displayName, jumpLink, value, platformName, platformAvatar)
                                                importedCount++
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        safeNavigateBack()
                                        if (importedCount > 0) {
                                            Toast.makeText(appContext, "已导入 $importedCount 个平台", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(appContext, "未识别到可导入的平台", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } } else null
                        )
                    }

                    is Route.ContactDetail -> {
                        ContactDetailPage(
                            contactId = currentRoute.contactId,
                            onBack = { safeNavigateBack() },
                            onRefreshData = {
                                // [修复防御]: 详情页发生数据变更（同步信息/编辑头像/编辑联系人等），
                                // 切到 PersonRoute 那一页（PagerState 仍在 composition 中），
                                // 触发 PersonViewModel.refreshUserProfile() 拉一次最新 UserProfile。
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                    appViewModel.refreshUserProfile()
                                }
                            },
                            onOpenScannerForImport = if (currentRoute.contactId == -1L) {{
                                navigator.navigate(Route.Scanner(mode = "importProfile"))
                            }} else null
                        )
                    }

                    is Route.SettingsSubPage -> {
                        SettingsSubPage(
                            page = currentRoute.page,
                            onBack = { safeNavigateBack() },
                            onNavigateToSubPage = { subPage -> navigator.navigate(Route.SettingsSubPage(subPage)) },
                            onNavigateToLogin = { navigator.navigate(Route.Login) },
                            onNavigateToMyProfile = { navigator.navigate(Route.ContactDetail(-1L)) },
                            onNavigateToContact = { contactId -> navigator.navigate(Route.ContactDetail(contactId)) },
                            devMode = devMode,
                            onDevModeChange = { devMode = it },
                        )
                    }

                    is Route.CollectionDetail -> {
                        CollectionDetailPage(
                            collectionId = currentRoute.collectionId,
                            onBack = { safeNavigateBack() },
                            onNavigateToScanner = { cid ->
                                navigator.navigate(Route.Scanner(mode = "collection", targetCollectionId = cid))
                            },
                            onNavigateToContactDetail = { cid ->
                                navigator.navigate(Route.ContactDetail(cid))
                            },
                            onNavigateToCreateContact = { cid ->
                                navigator.navigate(Route.CreateContact(targetCollectionId = cid))
                            }
                        )
                    }

                    is Route.CreateContact -> {
                        CreateContactPage(
                            targetCollectionId = currentRoute.targetCollectionId,
                            onBack = { safeNavigateBack() },
                            onNavigateToContactDetail = { contactId ->
                                navigator.navigate(Route.ContactDetail(contactId))
                            }
                        )
                    }
                }
            }
        }
    } // Box
}

@SuppressLint("FrequentlyChangingValue")
@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainTabsContent(
    pagerState: PagerState,
    scope: CoroutineScope,
    tabs: List<String>,
    icons: List<ImageVector>,
    isFloatingMode: Boolean,
    floatingEnabled: Boolean,
    liquidGlassEnabled: Boolean,
    hazeState: HazeState,
    backdrop: LayerBackdrop?,
    blurIntensity: BlurIntensity,
    effectMode: EffectMode,
    isScrolling: Boolean,
    route: Route,
    navigator: AppNavigator,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
    unreadNotificationCount: Int,
) {
    val settingsBadge = formatUnreadBadge(unreadNotificationCount)
    val tabBadges = listOf(null, null, null, settingsBadge)
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = if (!isFloatingMode) {
                {
                    NavigationBar(
                        showDivider = true,
                    ) {
                        tabs.forEachIndexed { index, label ->
                            NavBarItem(
                                title = label,
                                icon = icons[index],
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                                badge = tabBadges.getOrNull(index),
                            )
                        }
                    }
                }
            } else {{}}
        ) { innerPadding ->
            val floatingBarBottomPadding = if (isFloatingMode) 84.dp else 0.dp
            CompositionLocalProvider(LocalFloatingBarBottomPadding provides floatingBarBottomPadding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    // hazeSource 仅包裹 HorizontalPager，导航栏在源外部避免自采样
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isFloatingMode && liquidGlassEnabled && effectMode != EffectMode.NONE) {
                                    Modifier
                                        .applyBlurSource(hazeState)
                                        .applyLayerBackdrop(backdrop)
                                } else Modifier
                            )
                    ) {
                        CompositionLocalProvider(LocalOverscrollFactory provides null) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { page ->
                                when(page){
                                0 -> {
                                    SocialRoute(
                                        navigateToContacts = { scope.launch { pagerState.animateScrollToPage(1) } },
                                        onNavigateToProfile = { navigator.navigate(Route.ContactDetail(contactId = -1L)) },
                                        onNavigateToSettings = { scope.launch { pagerState.animateScrollToPage(3) } }
                                    )
                                }
                                1 -> {
                                    PersonRoute(
                                        onScanContact = { navigator.navigate(Route.Scanner()) },
                                        onCreateContact = { navigator.navigate(Route.CreateContact()) },
                                        onContactClick = { contactId -> navigator.navigate(Route.ContactDetail(contactId)) }
                                    )
                                }
                                2 -> {
                                    CardRoute(
                                        onScanToCollection = { collectionId -> navigator.navigate(Route.Scanner(mode = "collection", targetCollectionId = collectionId)) },
                                        onContactClick = { contactId -> navigator.navigate(Route.ContactDetail(contactId)) },
                                        onNavigateToCollectionDetail = { collectionId -> navigator.navigate(Route.CollectionDetail(collectionId)) }
                                    )
                                }
                                3 -> {
                                    SettingsPage(
                                        onNavigateToSubPage = { page -> navigator.navigate(Route.SettingsSubPage(page)) },
                                        onNavigateToLogin = { navigator.navigate(Route.Login) },
                                        onNavigateToMyProfile = { navigator.navigate(Route.ContactDetail(-1L)) },
                                        devMode = devMode,
                                        onDevModeChange = onDevModeChange,
                                    )
                                }
                            }
                        }
                    }
                }
                    // 悬浮导航栏在 Scaffold 内部，弹窗遮罩可正常覆盖
                    AnimatedVisibility(
                        visible = isFloatingMode,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        if (floatingEnabled) {
                            FloatingNavBar(
                                selectedIndex = pagerState.currentPage,
                                pageOffset = pagerState.currentPageOffsetFraction,
                                onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                                tabs = tabs,
                                icons = icons,
                                color = MiuixTheme.colorScheme.surface,
                                liquidGlassEnabled = liquidGlassEnabled,
                                hazeState = hazeState,
                                backdrop = backdrop,
                                blurIntensity = blurIntensity,
                                effectMode = effectMode,
                                isScrolling = isScrolling,
                                badges = tabBadges,
                            )
                        }
                    }
                }
            }
        }
    } // Box
}