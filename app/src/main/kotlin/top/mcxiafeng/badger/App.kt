package top.mcxiafeng.badger

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.NavigationDirection
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.ContactDetailPage
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.SettingsPage
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.pages.social.SocialRoute
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.ui.navigation.NavAnimationEasing
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.components.BlurredNavBar
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LiquidGlassNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.mcxiafeng.badger.ui.NavBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as kyantRememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme


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
    val icons = listOf(Icons.Outlined.QrCodeScanner, Icons.Outlined.Person, Icons.Outlined.CreditCard, Icons.Outlined.Settings)
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()

    val navigator = remember { AppNavigator() }
    val route by navigator.currentRoute.collectAsState()

    val repository = rememberContactRepository()
    val appContext = LocalContext.current

    // 首次启动检查
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted(appContext)) }
    if (!onboardingCompleted) {
        SetupGuideRoute(onComplete = {
            onboardingCompleted = true
            Log.d("App", "Setup guide completed, showing main app")
        })
        return
    }

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(initial = false)
    val blurAvailable by NavBarConfig.blurAvailableFlow.collectAsState(initial = NavBarConfig.isBlurSupported())
    val liquidGlassAvailable by NavBarConfig.liquidGlassAvailableFlow.collectAsState(initial = false)
    val systemBlurEnabled by NavBarConfig.systemBlurEnabledFlow.collectAsState(initial = true)
    val blurSupported = NavBarConfig.isBlurSupported()

    // Effective: 需要用户开启 + SDK 支持 + 系统允许模糊（Android 16 "减少模糊效果"、省电模式）
    val effectiveBlur = blurAvailable && blurSupported
    val effectiveLiquidGlass = liquidGlassAvailable && blurSupported

    // kyant/backdrop is used for both blur and liquid glass
    val needBackdrop = effectiveBlur || effectiveLiquidGlass
    val surfaceColor = MiuixTheme.colorScheme.surface
    val kyantBackdrop = if (needBackdrop) {
        kyantRememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else null

    val blurActive = effectiveBlur
    val liquidGlassActive = effectiveLiquidGlass
    val backdropActive = blurActive || liquidGlassActive

    // When backdrop is active, bar is transparent so content shows through
    val barColor = if (backdropActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Log.d("App", "floating=$floatingEnabled, blur=$effectiveBlur, liquidGlass=$effectiveLiquidGlass, backdropActive=$backdropActive")

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
                    // 返回：二级页面向右全屏滑出，主页从左侧滑回 1/4
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                        ),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                        ),
                        sizeTransform = SizeTransform(clip = false)
                    )
                } else if (targetState !is Route.MainTabs && initialState is Route.MainTabs) {
                    // 前进：新页面从右侧全屏滑入，主页向左滑出 1/4
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                        ),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { -it / 4 },
                            animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                        ),
                        sizeTransform = SizeTransform(clip = false)
                    )
                } else if (targetState !is Route.MainTabs && initialState !is Route.MainTabs) {
                    // 二级页面之间切换（如 CollectionDetail → ContactDetail）
                    when (navigator.navigationDirection) {
                        NavigationDirection.FORWARD -> ContentTransform(
                            targetContentEnter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                            ),
                            initialContentExit = slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                            ),
                            sizeTransform = SizeTransform(clip = false)
                        )
                        NavigationDirection.BACKWARD -> ContentTransform(
                            targetContentEnter = slideInHorizontally(
                                initialOffsetX = { -it / 4 },
                                animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                            ),
                            initialContentExit = slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = NavAnimationEasing)
                            ),
                            sizeTransform = SizeTransform(clip = false)
                        )
                        NavigationDirection.RESET -> fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    }
                } else {
                    // 同状态不触发动画
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                }
            }
        ) { currentRoute ->
            if (currentRoute is Route.MainTabs) {
                MainTabsContent(
                    pagerState = pagerState,
                    scope = scope,
                    tabs = tabs,
                    icons = icons,
                    isFloatingMode = isFloatingMode,
                    floatingEnabled = floatingEnabled,
                    blurActive = blurActive,
                    liquidGlassActive = liquidGlassActive,
                    backdropActive = backdropActive,
                    kyantBackdrop = kyantBackdrop,
                    barColor = barColor,
                    navigator = navigator,
                )
            } else {
                BackHandler(onBack = { safeNavigateBack() })
                when (currentRoute) {
                    is Route.Scanner -> {
                        ScannerPage(
                            onBack = { safeNavigateBack() },
                            targetCollectionId = if (currentRoute.mode == "collection") currentRoute.targetCollectionId else null,
                            onNavigateToAiSettings = { navigator.navigate(Route.SettingsSubPage("ai_ocr")) },
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
                                } catch (_: Exception) { null }
                                                val platformName = adapterResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                                                val platformAvatar = adapterResult?.avatarUrl?.takeIf { it.isNotBlank() }
                                                repository.updatePlatformField(displayName, jumpLink, value, platformName, platformAvatar)
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
                            onOpenScannerForImport = if (currentRoute.contactId == -1L) {{
                                navigator.navigate(Route.Scanner(mode = "importProfile"))
                            }} else null
                        )
                    }

                    is Route.SettingsSubPage -> {
                        SettingsSubPage(
                            page = currentRoute.page,
                            onBack = { safeNavigateBack() },
                            onNavigateToSubPage = { subPage -> navigator.navigate(Route.SettingsSubPage(subPage)) }
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
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    tabs: List<String>,
    icons: List<ImageVector>,
    isFloatingMode: Boolean,
    floatingEnabled: Boolean,
    blurActive: Boolean,
    liquidGlassActive: Boolean,
    backdropActive: Boolean,
    kyantBackdrop: LayerBackdrop?,
    barColor: Color,
    navigator: AppNavigator,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = if (!isFloatingMode) {
                {
                    if (liquidGlassActive && kyantBackdrop != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LiquidGlassNavBar(
                                backdrop = kyantBackdrop,
                                selectedIndex = pagerState.currentPage,
                                pageOffset = pagerState.currentPageOffsetFraction,
                                onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                                tabs = tabs,
                                icons = icons,
                                isBlurEnabled = true,
                                isFloating = false,
                                isLensSupported = NavBarConfig.isLensSupported(),
                            )
                        }
                    } else if (blurActive && kyantBackdrop != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BlurredNavBar(backdrop = kyantBackdrop, blurEnabled = true) {
                                NavigationBar(
                                    color = barColor,
                                    showDivider = false,
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
                        }
                    } else {
                        NavigationBar(
                            color = barColor,
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
                }
            } else {{}}
        ) { innerPadding ->
            // 计算底部 padding：
            // - 浮动模式：84.dp（浮动导航栏高度）
            // - 非浮动 + backdrop：使用 Scaffold 提供的 bottomPadding（因为 adjustedPadding 将 bottom 设为 0）
            // - 其他：0.dp
            val floatingBarBottomPadding = when {
                isFloatingMode -> 84.dp
                backdropActive -> innerPadding.calculateBottomPadding()
                else -> 0.dp
            }
            val adjustedPadding = if (backdropActive && !isFloatingMode) {
                PaddingValues(
                    start = innerPadding.calculateLeftPadding(LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateRightPadding(LocalLayoutDirection.current),
                    bottom = 0.dp,
                )
            } else {
                innerPadding
            }
            CompositionLocalProvider(LocalFloatingBarBottomPadding provides floatingBarBottomPadding) {
            Box(modifier = if (kyantBackdrop != null) Modifier.kyantLayerBackdrop(kyantBackdrop) else Modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(adjustedPadding)
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
                                        onNavigateToProfile = { navigator.navigate(Route.ContactDetail(contactId = -1L)) },
                                        onNavigateToSettings = { scope.launch { pagerState.animateScrollToPage(3) } }
                                    )
                                }
                                1 -> {
                                    PersonRoute(
                                        onAddContact = { navigator.navigate(Route.Scanner()) },
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
                                        onNavigateToSubPage = { page -> navigator.navigate(Route.SettingsSubPage(page)) }
                                    )
                                }
                            }
                        }
                    }
                }
            } // close kyantLayerBackdrop Box
            } // CompositionLocalProvider (LocalFloatingBarBottomPadding)
        }

        // 悬浮导航栏浮在内容上方
        AnimatedVisibility(
            visible = isFloatingMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (liquidGlassActive && kyantBackdrop != null) {
                    LiquidGlassNavBar(
                        backdrop = kyantBackdrop,
                        selectedIndex = pagerState.currentPage,
                        pageOffset = pagerState.currentPageOffsetFraction,
                        onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                        tabs = tabs,
                        icons = icons,
                        isBlurEnabled = true,
                        isLensSupported = NavBarConfig.isLensSupported(),
                    )
                } else if (blurActive && kyantBackdrop != null) {
                    FloatingNavBar(
                        selectedIndex = pagerState.currentPage,
                        pageOffset = pagerState.currentPageOffsetFraction,
                        onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                        tabs = tabs,
                        icons = icons,
                        backdrop = kyantBackdrop,
                        isBlurEnabled = true,
                    )
                } else if (floatingEnabled) {
                    FloatingNavBar(
                        selectedIndex = pagerState.currentPage,
                        pageOffset = pagerState.currentPageOffsetFraction,
                        onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                        tabs = tabs,
                        icons = icons,
                        color = barColor,
                    )
                }
            }
        }
    } // Box
}