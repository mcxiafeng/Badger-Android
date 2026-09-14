package top.mcxiafeng.badger

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.platform.AppLinkHandler
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.prefs.isDeveloperMode
import top.mcxiafeng.badger.data.prefs.isOnboardingCompleted
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.pages.setupguide.SetupGuideRoute
import top.mcxiafeng.badger.ui.blur.GpuCompat
import top.mcxiafeng.badger.ui.blur.rememberBadgerBackdrop
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.EffectMode
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.NavTransitions
import top.mcxiafeng.badger.ui.navigation.NavigationDirection
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.windowsize.LocalBadgerWindowSizeClass
import top.mcxiafeng.badger.ui.windowsize.rememberBadgerWindowSizeClass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Folder
import top.yukonga.miuix.kmp.blur.LayerBackdrop

private const val TAG_APP = "App"

@Composable
fun App() {

    val tabs = listOf("我的名片","联系人","名片夹","设置")
    val icons = listOf(Lucide.ScanLine, Lucide.User, Lucide.Folder, Lucide.Settings)
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()

    val navigator = remember { AppNavigator() }
    val route by navigator.currentRoute.collectAsState()

    
    
    
    
    val saveableStateHolder = rememberSaveableStateHolder()

    val appViewModel: AppViewModel = koinViewModel()
    val userProfileRepository = appViewModel.userProfileRepository
    val userAuthRepository = appViewModel.userAuthRepository
    val unreadNotificationCount by appViewModel.unreadNotificationCount.collectAsState()

    var devMode by remember { mutableStateOf(isDeveloperMode()) }

    
    val contactRepository = appViewModel.contactRepository

    
    suspend fun resolveDeepLink(serverId: String) {
        BadgerLog.d(TAG_APP, "Processing deep link for serverId: $serverId")
        val contact = withContext(BadgerDispatchers.io) {
            contactRepository.getContactByServerId(serverId)
        }
        if (contact != null) {
            BadgerLog.d(TAG_APP, "Deep link resolved to contactId: ${contact.id}")
            navigator.navigate(Route.ContactDetail(contact.id))
        } else {
            BadgerLog.w(TAG_APP, "Deep link: contact not found for serverId: $serverId")
            showToast("未找到该联系人")
        }
    }

    
    val linkHandler = remember { KoinComponentBy.get<AppLinkHandler>() }
    LaunchedEffect(linkHandler) {
        linkHandler.consumePendingDeepLink()?.let { resolveDeepLink(it) }
    }
    LaunchedEffect(linkHandler) {
        linkHandler.deepLinkEvents.collect { serverId ->
            resolveDeepLink(serverId)
        }
    }

    
    var onboardingCompleted by remember { mutableStateOf(isOnboardingCompleted()) }
    if (!onboardingCompleted) {
        SetupGuideRoute(onComplete = {
            onboardingCompleted = true
            BadgerLog.d(TAG_APP, "Setup guide completed, showing main app")
        })
        return
    }

    
    
    
    
    val authState by userAuthRepository.state.collectAsState()
    if (authState is AuthState.Unknown) {
        
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val floatingEnabled by NavBarConfig.floatingFlow.collectAsState(initial = true)
    val advancedBlurEnabled by NavBarConfig.advancedBlurFlow.collectAsState(initial = false)
    val effectMode by NavBarConfig.effectModeFlow.collectAsState(initial = EffectMode.BG_BLUR)

    
    val gpuAdvancedSupported = remember { GpuCompat.isAdvancedBlurSupported() }
    val advancedRefraction = advancedBlurEnabled && gpuAdvancedSupported

    
    val effectsActive = floatingEnabled && effectMode != EffectMode.NONE
    val backdrop: LayerBackdrop? = if (effectsActive) rememberBadgerBackdrop() else null

    
    var blurActive by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    blurActive = false
                    BadgerLog.d(TAG_APP, "app backgrounded, blur paused")
                }
                Lifecycle.Event.ON_START -> {
                    blurActive = true
                    BadgerLog.d(TAG_APP, "app foregrounded, blur resumed")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    
    fun safeNavigateBack() {
        if (!navigator.navigateBack()) {
            navigator.resetToMain()
        }
    }

    
    
    val windowSizeClass = rememberBadgerWindowSizeClass()

    
    val isFloatingMode = floatingEnabled

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBadgerWindowSizeClass provides windowSizeClass) {
        
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState is Route.MainTabs && initialState !is Route.MainTabs) {
                    NavTransitions.subToMain()
                } else if (targetState is Route.Scanner && initialState is Route.MainTabs) {
                    
                    NavTransitions.modal()
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
                
                
                saveableStateHolder.SaveableStateProvider(key = "MainTabs") {
                    MainTabsContent(
                        pagerState = pagerState,
                        scope = scope,
                        tabs = tabs,
                        icons = icons,
                        isFloatingMode = isFloatingMode,
                        backdrop = backdrop,
                        blurActive = blurActive,
                        advancedRefraction = advancedRefraction,
                        effectMode = effectMode,
                        route = route,
                        navigator = navigator,
                        unreadNotificationCount = unreadNotificationCount,
                        windowSizeClass = windowSizeClass,
                    )
                }
            } else {
                AppSubRouteContent(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    onNavigateBack = { safeNavigateBack() },
                    scope = scope,
                    userProfileRepository = userProfileRepository,
                    pagerState = pagerState,
                    onRefreshUserProfile = { appViewModel.refreshUserProfile() },
                    devMode = devMode,
                    onDevModeChange = { devMode = it },
                )
            }
        }
        } 
    } 
}

