package top.mcxiafeng.badger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.settings.SettingsPage
import top.mcxiafeng.badger.pages.social.SocialRoute
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.blur.badgerBackdropSource
import top.mcxiafeng.badger.ui.formatUnreadBadge
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainTabsContent(
    pagerState: PagerState,
    scope: CoroutineScope,
    tabs: List<String>,
    icons: List<ImageVector>,
    isFloatingMode: Boolean,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    advancedRefraction: Boolean,
    effectMode: EffectMode,
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
                    // [K14] 采样源仅包裹 HorizontalPager，导航栏在源外部避免自采样
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isFloatingMode && effectMode != EffectMode.NONE && blurActive) {
                                    Modifier.badgerBackdropSource(backdrop)
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
                            FloatingNavBar(
                                selectedIndex = pagerState.currentPage,
                                onSelected = { index -> scope.launch { if (pagerState.currentPage != index) pagerState.animateScrollToPage(index) } },
                                tabs = tabs,
                                icons = icons,
                                containerColor = MiuixTheme.colorScheme.surface,
                                effectMode = effectMode,
                                backdrop = backdrop,
                                blurActive = blurActive,
                                advancedRefraction = advancedRefraction,
                                badges = tabBadges,
                            )
                    }
                }
            }
        }
    } // Box
}
