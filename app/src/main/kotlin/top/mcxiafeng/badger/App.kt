package top.mcxiafeng.badger

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.util.fastRoundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.navigation.MainTab
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.pushUnique
import top.mcxiafeng.badger.ui.navigation.replaceUnique
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.ContactAvatarPage
import top.mcxiafeng.badger.pages.person.contact.ContactDetailPage
import top.mcxiafeng.badger.pages.person.contact.AddPlatformPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.person.ContactSearchRoute
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.todo.TodoEditorPage
import top.mcxiafeng.badger.pages.todo.TodoRoute
import top.mcxiafeng.badger.pages.todo.TodoTypeManagerPage
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.MyRoute
import top.mcxiafeng.badger.pages.settings.SettingsPage as SettingsScreen
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.pages.account.AccountPage
import top.mcxiafeng.badger.pages.account.AccountSwitchPage
import top.mcxiafeng.badger.pages.account.AccountUiHost
import top.mcxiafeng.badger.pages.account.AccountViewModel
import top.mcxiafeng.badger.pages.auth.AuthPage
import top.mcxiafeng.badger.pages.auth.ForgotPasswordPage
import top.mcxiafeng.badger.pages.server.ChoiceServerPage
import top.mcxiafeng.badger.pages.server.ServerAddressesPage
import top.mcxiafeng.badger.data.MY_CONTACT_ID
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.pages.social.SocialRoute
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.FloatingNavBarMinBottomPadding
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.glass.GlassBackdropContent
import top.mcxiafeng.badger.ui.glass.GlassPage
import top.mcxiafeng.badger.ui.glass.GlassStyle
import top.mcxiafeng.badger.ui.glass.LocalAppDarkTheme
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.mcxiafeng.badger.ui.glass.LocalGlassBackdrop
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.glassEntry
import top.mcxiafeng.badger.ui.glass.logGlassStyle
import top.mcxiafeng.badger.ui.glass.rememberGlassBackdrop
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition


/**
 * 全局导航转场：视觉与 [top.yukonga.miuix.kmp.nav.transition.NavTransitions.MiuixDefault]
 * 一致（进入页整幅滑入 + 被覆盖层 1/4 视差淡出），但把 opaqueDepth 从 1f 提到 2f。
 *
 * 默认 opaqueDepth=1f 会在推入转场收尾时把被覆盖层整棵移出组合树；返回时该层
 * （MainTabs 的三个 Tab：头像重载、名片背景重新解码、列表重订阅）在返回转场中途
 * 全量重建，实测 Choreographer 跳 32 帧，画面冻结在转场中段（半旧半新）。
 * opaqueDepth=2f 让被覆盖层保持组合（生命周期降级为 STARTED），返回零重建。
 */
private val KeepCoveredTransition: NavTransition = navGraphicsTransition(opaqueDepth = 2f) { scope ->
    val width = scope.layoutSize.width.toFloat()
    val d = scope.relativeDepth
    val rtl = scope.layoutDirection == LayoutDirection.Rtl
    if (d <= 0f) {
        // 进入/离开顶层：整幅滑入（与 MiuixDefault 相同，含整像素取整防边缘闪烁）
        translationX = ((if (rtl) -1f else 1f) * (-d).coerceIn(0f, 1f) * width).fastRoundToInt().toFloat()
    } else {
        // 被覆盖层：1/4 宽度视差 + 10% 淡出（d 超过 1 后保持完全覆盖态，不参与动画）
        val covered = d.coerceIn(0f, 1f)
        translationX = (if (rtl) 1f else -1f) * covered * width * 0.25f
        alpha = 1f - 0.1f * covered
    }
}


/**
 * 应用主界面
 *
 * 使用 Miuix Nav（[NavDisplay] + [NavController]）管理页面导航：
 * - 栈底 [Route.MainTabs]：HorizontalPager 4 个 Tab 页（首页 / 名片夹 / 待办 / 我的）
 * - 二级页面（扫描页、联系人详情页等）通过 push 进入栈顶，pop 返回；
 *   推入/弹出由 NavDisplay 的连续深度动画驱动（进入页滑入 + 被覆盖页视差），
 *   系统返回接入预测性返回手势，边缘滑动可直接交互式退出页面。
 * - 栈底 MainTabs 被覆盖时保持在 composition 中（生命周期被 NavDisplay 降级），
 *   Pager 滚动状态天然保留，返回时自动恢复。
 * - 路由栈为 rememberSaveable 持久化的 [Route]（@Serializable），旋转/进程重建后自动恢复。
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
@Preview
fun App() {

    val pagerState = rememberPagerState { MainTab.entries.size }
    val scope = rememberCoroutineScope()

    // Miuix Nav：路由栈由 NavBackStack 管理；根路由 MainTabs 永不出栈
    //（pop() 保证至少保留一个栈底，onBack 也不会在栈底触发，系统返回此时交给系统退出应用）
    val navController = rememberNavController<Route>(Route.MainTabs)

    val appViewModel: AppViewModel = hiltViewModel()
    val accountSync: AccountViewModel = hiltViewModel()
    val appContext = LocalContext.current
    var devMode by remember { mutableStateOf(isDeveloperMode(appContext)) }

    // 首次启动检查
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted(appContext)) }

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState()
    val materialEnabled by NavBarConfig.glassMaterialFlow.collectAsState()
    val glassStyle = GlassStyle(enabled = true, dark = LocalAppDarkTheme.current,
        materialEnabled = materialEnabled, floatingNavigation = floatingEnabled)
    LaunchedEffect(glassStyle) { logGlassStyle(glassStyle) }
    CompositionLocalProvider(LocalGlassStyle provides glassStyle) {
        RetainedPopupHost {
            AppRoutes(navController, pagerState, scope, appViewModel, accountSync, onboardingCompleted, {
                onboardingCompleted = true
                Log.d("AppTester", "引导完成，显示主页面")
            }, devMode, { devMode = it })
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun AppRoutes(
    navController: NavController,
    pagerState: PagerState,
    scope: CoroutineScope,
    appViewModel: AppViewModel,
    accountSync: AccountViewModel,
    onboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
) {
    val appContext = LocalContext.current
    val floatingEnabled = LocalGlassStyle.current.floatingNavigation
    val navigateToAuth = {
        if (appViewModel.hasActiveServer.value) navController.pushUnique(Route.Auth)
        else navController.pushUnique(Route.ChoiceServer)
    }
    AccountUiHost(onImport = { navController.pushUnique(Route.Accounts("import")) }, viewModel = accountSync)

    // 所有页面统一由 Miuix NavDisplay 渲染：
    // - 系统返回走 androidx.navigationevent 预测性返回（页面内部 BackHandler/对话框仍优先消费）；
    //   栈底时 onBack 不会触发，系统返回交给系统默认行为（退出应用）
    // - 页面内部无需再挂 BackHandler 拦截返回，pop 即返回上一页
    // - transition 用 KeepCoveredTransition（MiuixDefault 同视觉 + opaqueDepth=2）：
    //   默认 opaqueDepth=1 会在转场收尾把被覆盖层整个移出组合，返回时三 Tab 全量重建
    //   （头像重载 + 名片背景重解码 + reload），重组恰好落在返回转场中途导致掉帧卡屏
    NavDisplay(
        navController = navController,
        transition = KeepCoveredTransition,
        onBack = {
            Log.d("Tester", "App: onBack pop, stackSize=${navController.backStack.size}")
            navController.pop()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        glassEntry<Route.MainTabs> {
            if (!onboardingCompleted) {
                SetupGuideRoute(
                    onComplete = onOnboardingComplete,
                    onNavigateToAddPlatform = { navController.pushUnique(Route.AddPlatform(MY_CONTACT_ID)) }
                )
            } else {
                MainTabsContent(
                    pagerState = pagerState,
                    scope = scope,
                    floatingEnabled = floatingEnabled,
                    navController = navController,
                    accountSync = accountSync,
                )
            }
        }

        glassEntry<Route.Scanner> { currentRoute ->
            ScannerPage(
                onBack = { navController.pop() },
                targetCollectionId = if (currentRoute.mode == "collection") currentRoute.targetCollectionId else null,
                onNavigateToAiSettings = { navController.pushUnique(Route.SettingsSubPage(SettingsPage.AiOcr)) },
                onNavigateToCreateContact = {
                    navController.pushUnique(Route.CreateContact(targetCollectionId = currentRoute.targetCollectionId))
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
                                    // "我的名片"按真实联系人存储（contacts 表 id=-1），平台写入 contact_platforms
                                    appViewModel.contactRepository.updateContactPlatform(
                                        MY_CONTACT_ID,
                                        key,
                                        PlatformEntry(
                                            jumpLink = jumpLink,
                                            value = value,
                                            displayName = platformName,
                                            avatarUrl = platformAvatar
                                        )
                                    )
                                    importedCount++
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            navController.pop()
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

        glassEntry<Route.AddPlatform> { currentRoute ->
            AddPlatformPage(contactId = currentRoute.contactId,
                onBack = { navController.pop() },
                onSaved = {
                    appViewModel.refreshUserProfile()
                    navController.pop()
                })
        }

        glassEntry<Route.ContactSearch> {
            ContactSearchRoute(
                onBack = { if (navController.backStack.lastOrNull() == Route.ContactSearch) navController.pop() },
                onContactClick = { contactId ->
                    if (navController.backStack.lastOrNull() == Route.ContactSearch) {
                        navController.pushUnique(Route.ContactDetail(contactId))
                    }
                },
            )
        }

        glassEntry<Route.ContactDetail>(glassButtons = false) { currentRoute ->
            ContactDetailPage(
                contactId = currentRoute.contactId,
                onBack = { navController.pop() },
                onNavigateToAddPlatform = { navController.pushUnique(Route.AddPlatform(currentRoute.contactId)) },
                onNavigateToEdit = { contactId ->
                    navController.pushUnique(Route.CreateContact(contactId = contactId))
                },
                onNavigateToAvatar = { contactId ->
                    navController.pushUnique(Route.ContactAvatar(contactId))
                },
                onRefreshData = {
                    // 从名片夹编辑联系人后，保留「我的」作为返回目的地。
                    val openedFromCollections = Route.Collections in navController.backStack
                    scope.launch {
                        if (!openedFromCollections) pagerState.animateScrollToPage(MainTab.CONTACTS.ordinal)
                        appViewModel.refreshUserProfile()
                    }
                }
            )
        }

        glassEntry<Route.ContactAvatar>(glassButtons = false) { currentRoute ->
            ContactAvatarPage(
                contactId = currentRoute.contactId,
                onBack = { navController.pop() },
                onRefreshData = {
                    // 头像变更后刷新"我的名片"（详情页返回时会自行重读联系人）
                    scope.launch { appViewModel.refreshUserProfile() }
                }
            )
        }

        glassEntry<Route.Settings> {
            val configuredServerUrl by appViewModel.configuredServerUrl.collectAsState(initial = null)
            SettingsScreen(
                onBack = { navController.pop() },
                onNavigateToSubPage = { page -> navController.pushUnique(Route.SettingsSubPage(page)) },
                onNavigateToServerAddresses = { navController.pushUnique(Route.ServerAddresses) },
                configuredServerUrl = configuredServerUrl,
            )
        }

        glassEntry<Route.SettingsSubPage> { currentRoute ->
            SettingsSubPage(
                page = currentRoute.page,
                onBack = { navController.pop() },
                onNavigateToSubPage = { subPage -> navController.pushUnique(Route.SettingsSubPage(subPage)) },
                devMode = devMode,
                onDevModeChange = onDevModeChange,
            )
        }

        glassEntry<Route.Collections> {
            CardRoute(
                onBack = {
                    Log.d("AppTester", "从名片夹返回我的")
                    navController.pop()
                },
                onNavigateToCollectionDetail = { collectionId ->
                    navController.pushUnique(Route.CollectionDetail(collectionId))
                },
            )
        }

        glassEntry<Route.CollectionDetail> { currentRoute ->
            CollectionDetailPage(
                collectionId = currentRoute.collectionId,
                onBack = { navController.pop() },
                onNavigateToScanner = { cid ->
                    navController.pushUnique(Route.Scanner(mode = "collection", targetCollectionId = cid))
                },
                onNavigateToContactDetail = { cid ->
                    navController.pushUnique(Route.ContactDetail(cid))
                },
                onNavigateToCreateContact = { cid ->
                    navController.pushUnique(Route.CreateContact(targetCollectionId = cid))
                }
            )
        }

        glassEntry<Route.CreateContact> { currentRoute ->
            CreateContactPage(
                contactId = currentRoute.contactId,
                targetCollectionId = currentRoute.targetCollectionId,
                onBack = { navController.pop() },
                onNavigateToContactDetail = { contactId ->
                    if (currentRoute.contactId != null) {
                        // 编辑模式：详情页已在栈中，保存后直接返回它（onResume 会重新加载）。
                        Log.d("AppTester", "编辑名片已保存，返回详情页")
                        navController.pop()
                    } else {
                        Log.d("AppTester", "草稿已保存，进入名片详情")
                        navController.replaceUnique(Route.ContactDetail(contactId))
                    }
                }
            )
        }

        glassEntry<Route.TodoEditor>(glassButtons = false) { currentRoute ->
            TodoEditorPage(id = currentRoute.id, onBack = { navController.pop() }, onManageTypes = {
                navController.pushUnique(Route.TodoTypes)
            }, onContactClick = { contactId ->
                Log.d("AppTester", "从待办详情打开联系人详情")
                navController.pushUnique(Route.ContactDetail(contactId))
            })
        }

        glassEntry<Route.TodoTypes>(glassButtons = false) {
            TodoTypeManagerPage(onBack = { navController.pop() })
        }

        glassEntry<Route.Accounts> { currentRoute ->
            AccountPage(section = currentRoute.section, onBack = { navController.pop() }, onLogin = {
                Log.d("AccountNavigationTester", "打开专用账号切换页")
                navController.pushUnique(Route.AccountSwitch)
            }, onManageTags = { navController.pushUnique(Route.SettingsSubPage(SettingsPage.TagManager)) },
                onReauthenticate = navigateToAuth)
        }

        glassEntry<Route.AccountSwitch> {
            AccountSwitchPage(onBack = { navController.pop() }, onLoginNewAccount = {
                Log.d("AccountNavigationTester", "从账号切换页开始新认证任务")
                navController.replaceUnique(if (appViewModel.hasActiveServer.value) Route.Auth else Route.ChoiceServer)
            })
        }

        glassEntry<Route.Auth> {
            AuthPage(
                onBack = { navController.pop() },
                onNavigateToForgotPassword = { navController.pushUnique(Route.ForgotPassword) },
                onNavigateToServerAddresses = { navController.pushUnique(Route.ServerAddresses) },
            )
        }

        glassEntry<Route.ForgotPassword> {
            ForgotPasswordPage(
                onBack = { navController.pop() },
            )
        }

        glassEntry<Route.ChoiceServer> {
            ChoiceServerPage(
                onBack = { navController.pop() },
                onProceedToAuth = {
                    Log.d("AccountNavigationTester", "服务器选择完成，进入认证任务")
                    navController.replaceUnique(Route.Auth)
                },
            )
        }

        glassEntry<Route.ServerAddresses> {
            ServerAddressesPage(
                onBack = { navController.pop() },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainTabsContent(
    pagerState: PagerState,
    scope: CoroutineScope,
    floatingEnabled: Boolean,
    navController: NavController,
    accountSync: AccountViewModel,
) {
    Scaffold(
        progressiveTopBlur = false, // Each tab owns one fade/blur region; the Pager host adds none.
        bottomBar = { if (!floatingEnabled) ClassicMainTabBar(pagerState, scope) },
        contentWindowInsets = if (floatingEnabled) WindowInsets(0) else WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        val density = LocalDensity.current
        val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
        var floatingBarHeightPx by remember { mutableIntStateOf(0) }
        val floatingBarBottomPadding = if (floatingEnabled) {
            with(density) { (floatingBarHeightPx - navigationBarBottomPx).coerceAtLeast(0).toDp()
                .coerceAtLeast(FloatingNavBarMinBottomPadding) }
        } else 0.dp
        CompositionLocalProvider(LocalFloatingBarBottomPadding provides floatingBarBottomPadding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                val backdrop = rememberGlassBackdrop(MiuixTheme.colorScheme.surface)
                GlassBackdropContent(backdrop) {
                    MainTabsPager(pagerState, scope, navController, accountSync)
                }
                // 保持在 Scaffold 的内容层，让弹窗遮罩覆盖底栏。
                if (floatingEnabled) {
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        FloatingNavBar(
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .onSizeChanged { floatingBarHeightPx = it.height }
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                            pagerState = pagerState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicMainTabBar(pagerState: PagerState, scope: CoroutineScope) {
    NavigationBar(modifier = Modifier.padding(horizontal = BadgerSpacing.xl), showDivider = true) {
        MainTab.entries.forEach { tab ->
            NavBarItem(
                title = tab.label,
                icon = tab.icon,
                selected = pagerState.currentPage == tab.ordinal,
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage != tab.ordinal) pagerState.animateScrollToPage(tab.ordinal)
                    }
                },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainContactsTab(navController: NavController, accountSync: AccountViewModel) {
    PersonRoute(
        accountSync = accountSync,
        onScan = { navController.pushUnique(Route.Scanner()) },
        onCreateContact = {
            if (navController.backStack.lastOrNull() == Route.MainTabs) {
                Log.d("AppTester", "从名片夹打开完整名片草稿")
                navController.pushUnique(Route.CreateContact())
            }
        },
        onContactClick = { navController.pushUnique(Route.ContactDetail(it)) },
        onSearch = {
            if (navController.backStack.lastOrNull() == Route.MainTabs) {
                navController.pushUnique(Route.ContactSearch)
            }
        },
    )
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainTabsPager(
    pagerState: PagerState, scope: CoroutineScope, navController: NavController, accountSync: AccountViewModel,
) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 四个 Tab 常驻组合：默认 1 只保留相邻页，「我的」(页3) 与「首页」(页0) 相隔 3 页，
            // 互切会整棵重建两边的页（头像重载 + 名片背景重解码 + 列表重订阅），顿挫感明显
            beyondViewportPageCount = 3,
        ) { page ->
            GlassPage(glassButtons = page != MainTab.TODOS.ordinal) {
                MainTabContent(page, pagerState, scope, navController, accountSync)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainTabContent(
    page: Int, pagerState: PagerState, scope: CoroutineScope, navController: NavController, accountSync: AccountViewModel,
) {
    when (MainTab.entries[page]) {
        MainTab.MY_CARD -> SocialRoute(
            navigateToContacts = { scope.launch { pagerState.animateScrollToPage(MainTab.CONTACTS.ordinal) } },
            onNavigateToProfile = { navController.pushUnique(Route.ContactDetail(contactId = -1L)) },
            onNavigateToSettings = {
                Log.d("AppTester", "从名片写入提示打开 NFC 设置")
                navController.pushUnique(Route.SettingsSubPage(SettingsPage.NfcSettings))
            },
            onNavigateToAddPlatform = { navController.pushUnique(Route.AddPlatform(MY_CONTACT_ID)) },
        )
        MainTab.CONTACTS -> MainContactsTab(navController, accountSync)
        MainTab.TODOS -> TodoRoute(onEdit = {
            Log.d("AppTester", "打开待办编辑页；new=${it.isEmpty()}")
            navController.pushUnique(Route.TodoEditor(it))
        }, onManageTypes = { navController.pushUnique(Route.TodoTypes) }, isActive = pagerState.currentPage == page)
        MainTab.MY_PAGE -> MyRoute(
            onSettings = { navController.pushUnique(Route.Settings) },
            onAccount = { navController.pushUnique(Route.Accounts()) },
            onManageCollections = { navController.pushUnique(Route.Collections) },
            onManageTags = { navController.pushUnique(Route.SettingsSubPage(SettingsPage.TagManager)) },
            onAbout = { navController.pushUnique(Route.SettingsSubPage(SettingsPage.About)) },
        )
    }
}
