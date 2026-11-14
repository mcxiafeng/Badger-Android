package top.mcxiafeng.badger

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.ContactDetailPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.SettingsPage
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.pages.auth.AuthPage
import top.mcxiafeng.badger.pages.auth.ForgotPasswordPage
import top.mcxiafeng.badger.pages.server.ChoiceServerPage
import top.mcxiafeng.badger.pages.server.ServerAddressesPage
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.pages.social.SocialRoute
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.theme.MiuixTheme


/**
 * 应用主界面
 *
 * 使用 Miuix Nav（[NavDisplay] + [NavController]）管理页面导航：
 * - 栈底 [Route.MainTabs]：HorizontalPager 4 个 Tab 页（我的名片 / 联系人 / 名片夹 / 设置）
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

    val tabs = listOf("我的名片","联系人","名片夹","设置")
    val icons = listOf(Icons.Outlined.QrCodeScanner, Icons.Outlined.Person, Icons.Outlined.CreditCard, Icons.Outlined.Settings)
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()

    // Miuix Nav：路由栈由 NavBackStack 管理；根路由 MainTabs 永不出栈
    //（pop() 保证至少保留一个栈底，onBack 也不会在栈底触发，系统返回此时交给系统退出应用）
    val navController = rememberNavController<Route>(Route.MainTabs)

    val appViewModel: AppViewModel = hiltViewModel()
    val userProfileRepository = appViewModel.userProfileRepository
    val appContext = LocalContext.current

    var devMode by remember { mutableStateOf(isDeveloperMode(appContext)) }

    // 首次启动检查
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted(appContext)) }
    if (!onboardingCompleted) {
        SetupGuideRoute(onComplete = {
            onboardingCompleted = true
            Log.d("App", "Setup guide completed, showing main app")
        })
        return
    }

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(initial = true)

    // 所有页面统一由 Miuix NavDisplay 渲染：
    // - 系统返回走 androidx.navigationevent 预测性返回（页面内部 BackHandler/对话框仍优先消费）；
    //   栈底时 onBack 不会触发，系统返回交给系统默认行为（退出应用）
    // - 页面内部无需再挂 BackHandler 拦截返回，pop 即返回上一页
    NavDisplay(
        navController = navController,
        onBack = {
            Log.d("Tester", "App: onBack pop, stackSize=${navController.backStack.size}")
            navController.pop()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        entry<Route.MainTabs> {
            MainTabsContent(
                pagerState = pagerState,
                scope = scope,
                tabs = tabs,
                icons = icons,
                floatingEnabled = floatingEnabled,
                navController = navController,
                devMode = devMode,
                onDevModeChange = { devMode = it },
                onNavigateToAuth = {
                    // 未配置服务器时先走服务器页（成功后由其跳转登录页）
                    if (appViewModel.hasActiveServer.value) {
                        navController.push(Route.Auth)
                    } else {
                        navController.push(Route.ChoiceServer)
                    }
                },
            )
        }

        entry<Route.Scanner> { currentRoute ->
            ScannerPage(
                onBack = { navController.pop() },
                targetCollectionId = if (currentRoute.mode == "collection") currentRoute.targetCollectionId else null,
                onNavigateToAiSettings = { navController.push(Route.SettingsSubPage(SettingsPage.AiOcr)) },
                onNavigateToCreateContact = {
                    navController.push(Route.CreateContact(targetCollectionId = currentRoute.targetCollectionId))
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

        entry<Route.ContactDetail> { currentRoute ->
            ContactDetailPage(
                contactId = currentRoute.contactId,
                onBack = { navController.pop() },
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
                    navController.push(Route.Scanner(mode = "importProfile"))
                }} else null
            )
        }

        entry<Route.SettingsSubPage> { currentRoute ->
            SettingsSubPage(
                page = currentRoute.page,
                onBack = { navController.pop() },
                onNavigateToSubPage = { subPage -> navController.push(Route.SettingsSubPage(subPage)) },
                devMode = devMode,
                onDevModeChange = { devMode = it },
            )
        }

        entry<Route.CollectionDetail> { currentRoute ->
            CollectionDetailPage(
                collectionId = currentRoute.collectionId,
                onBack = { navController.pop() },
                onNavigateToScanner = { cid ->
                    navController.push(Route.Scanner(mode = "collection", targetCollectionId = cid))
                },
                onNavigateToContactDetail = { cid ->
                    navController.push(Route.ContactDetail(cid))
                },
                onNavigateToCreateContact = { cid ->
                    navController.push(Route.CreateContact(targetCollectionId = cid))
                }
            )
        }

        entry<Route.CreateContact> { currentRoute ->
            CreateContactPage(
                targetCollectionId = currentRoute.targetCollectionId,
                onBack = { navController.pop() },
                onNavigateToContactDetail = { contactId ->
                    navController.push(Route.ContactDetail(contactId))
                }
            )
        }

        entry<Route.Auth> {
            AuthPage(
                onBack = { navController.pop() },
                onNavigateToForgotPassword = { navController.push(Route.ForgotPassword) },
                onNavigateToServerAddresses = { navController.push(Route.ServerAddresses) },
            )
        }

        entry<Route.ForgotPassword> {
            ForgotPasswordPage(
                onBack = { navController.pop() },
            )
        }

        entry<Route.ChoiceServer> {
            ChoiceServerPage(
                onBack = { navController.pop() },
                onProceedToAuth = { navController.push(Route.Auth) },
            )
        }

        entry<Route.ServerAddresses> {
            ServerAddressesPage(
                onBack = { navController.pop() },
            )
        }
    }
}

@SuppressLint("FrequentlyChangingValue")
@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun MainTabsContent(
    pagerState: PagerState,
    scope: CoroutineScope,
    tabs: List<String>,
    icons: List<ImageVector>,
    floatingEnabled: Boolean,
    navController: NavController,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
    onNavigateToAuth: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = if (!floatingEnabled) {
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
                            )
                        }
                    }
                }
            } else {{}}
        ) { innerPadding ->
            val floatingBarBottomPadding = if (floatingEnabled) 84.dp else 0.dp
            CompositionLocalProvider(LocalFloatingBarBottomPadding provides floatingBarBottomPadding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
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
                                    onNavigateToProfile = { navController.push(Route.ContactDetail(contactId = -1L)) },
                                    onNavigateToSettings = { scope.launch { pagerState.animateScrollToPage(3) } }
                                )
                            }
                            1 -> {
                                PersonRoute(
                                    onAddContact = { navController.push(Route.Scanner()) },
                                    onContactClick = { contactId -> navController.push(Route.ContactDetail(contactId)) }
                                )
                            }
                            2 -> {
                                CardRoute(
                                    onScanToCollection = { collectionId -> navController.push(Route.Scanner(mode = "collection", targetCollectionId = collectionId)) },
                                    onContactClick = { contactId -> navController.push(Route.ContactDetail(contactId)) },
                                    onNavigateToCollectionDetail = { collectionId -> navController.push(Route.CollectionDetail(collectionId)) }
                                )
                            }
                            3 -> {
                                SettingsPage(
                                    onNavigateToSubPage = { page -> navController.push(Route.SettingsSubPage(page)) },
                                    onNavigateToAuth = onNavigateToAuth,
                                    onNavigateToServerAddresses = { navController.push(Route.ServerAddresses) },
                                    devMode = devMode,
                                    onDevModeChange = onDevModeChange,
                                )
                            }
                        }
                    }
                }
                    // 悬浮导航栏在 Scaffold 内部，弹窗遮罩可正常覆盖
                    AnimatedVisibility(
                        visible = floatingEnabled,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        FloatingNavBar(
                            selectedIndex = pagerState.currentPage,
                            pageOffset = pagerState.currentPageOffsetFraction,
                            onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                            tabs = tabs,
                            icons = icons,
                            color = MiuixTheme.colorScheme.surface,
                        )
                    }
                }
            }
        }
    } // Box
}
