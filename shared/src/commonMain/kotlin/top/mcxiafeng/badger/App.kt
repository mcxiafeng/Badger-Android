package top.mcxiafeng.badger

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MoveLeft
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Wallet
import top.mcxiafeng.badger.page.HomePage
import top.mcxiafeng.badger.page.social.SocialPage
import top.mcxiafeng.badger.page.sync.SyncPage
import top.mcxiafeng.badger.ui.MainPagerState
import top.mcxiafeng.badger.ui.Navigator
import top.mcxiafeng.badger.ui.Route
import top.mcxiafeng.badger.ui.designsystem.ProvideBadgerDesignColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App() {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember { Navigator(backStack) }
    MiuixTheme(controller = controller) {
        ProvideBadgerDesignColors(controller = controller) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                NavDisplay(
                    backStack = backStack,
                    onBack = { navigator.pop() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    effects = NavDisplayEffects(
                        enableCornerClip = true,
                        cornerClipMode = NavCornerClipMode.All,
                        cornerClipRadius = rememberNavSystemCornerRadius()
                    )
                ) {
                    entry<Route.Main> {
                        HomePage.Home(navigator)
                    }
                    entry<Route.Sync> {
                        SyncPage.PageSync(onBackClick = {
                            backStack.removeAt(backStack.lastIndex)
                        })
                    }
                }
            }
        }
    }
}
