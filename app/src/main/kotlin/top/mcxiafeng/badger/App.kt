package top.mcxiafeng.badger

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.data.isDeveloperMode
import top.mcxiafeng.badger.data.isOnboardingCompleted
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.NavigationDirection
import top.mcxiafeng.badger.ui.navigation.NavTransitions
import top.mcxiafeng.badger.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text

/**
 * Application composition root.
 *
 * Bootstrap, route dispatch, main tabs, and visual-effect lifecycle are delegated to focused
 * composables so this function only coordinates application-level state.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun App() {
    val context = LocalContext.current
    val navigator = remember { AppNavigator() }
    val route by navigator.currentRoute.collectAsState()
    val saveableStateHolder = rememberSaveableStateHolder()
    val appViewModel: AppViewModel = koinViewModel()
    val authState by appViewModel.authState.collectAsState()

    var devMode by remember { mutableStateOf(isDeveloperMode(context)) }
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted(context)) }

    AppDeepLinkEffect(navigator = navigator, appViewModel = appViewModel)

    if (!onboardingCompleted) {
        SetupGuideRoute(onComplete = { onboardingCompleted = true })
        return
    }

    if (authState is top.mcxiafeng.badger.data.repository.AuthState.Unknown) {
        AppLoadingContent()
        return
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val visuals = rememberAppVisualEffects(context = context, pagerState = pagerState)
    val tabs = remember { listOf("我的名片", "联系人", "名片夹", "设置") }
    val icons = remember {
        listOf(
            top.yukonga.miuix.kmp.icon.MiuixIcons.Scan,
            top.yukonga.miuix.kmp.icon.MiuixIcons.Contacts,
            top.yukonga.miuix.kmp.icon.MiuixIcons.Folder,
            top.yukonga.miuix.kmp.icon.MiuixIcons.Settings,
        )
    }
    val unreadCount by appViewModel.unreadNotificationCount.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                when {
                    targetState is Route.MainTabs && initialState !is Route.MainTabs ->
                        NavTransitions.subToMain()
                    targetState !is Route.MainTabs && initialState is Route.MainTabs ->
                        NavTransitions.mainToSub()
                    targetState !is Route.MainTabs && initialState !is Route.MainTabs ->
                        when (navigator.navigationDirection) {
                            NavigationDirection.FORWARD -> NavTransitions.push()
                            NavigationDirection.BACKWARD -> NavTransitions.pop()
                            NavigationDirection.RESET -> NavTransitions.reset()
                        }
                    else -> NavTransitions.none()
                }
            },
        ) { currentRoute ->
            if (currentRoute is Route.MainTabs) {
                saveableStateHolder.SaveableStateProvider(key = "MainTabs") {
                    AppMainTabs(
                        pagerState = pagerState,
                        scope = scope,
                        tabs = tabs,
                        icons = icons,
                        floatingEnabled = visuals.floatingEnabled,
                        liquidGlassEnabled = visuals.liquidGlassEnabled,
                        hazeState = visuals.hazeState,
                        backdrop = visuals.backdrop,
                        blurIntensity = visuals.blurIntensity,
                        effectMode = visuals.effectMode,
                        isScrolling = visuals.isScrolling,
                        navigator = navigator,
                        devMode = devMode,
                        onDevModeChange = { devMode = it },
                        unreadNotificationCount = unreadCount,
                    )
                }
            } else {
                AppRouteHost(
                    route = currentRoute,
                    navigator = navigator,
                    appViewModel = appViewModel,
                    devMode = devMode,
                    onDevModeChange = { devMode = it },
                )
            }
        }
    }
}

@Composable
private fun AppLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = "正在准备应用…")
        }
    }
}
