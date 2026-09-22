package top.mcxiafeng.badger.page

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Wallet
import top.mcxiafeng.badger.page.collection.CollectionPage
import top.mcxiafeng.badger.page.person.PersonPage
import top.mcxiafeng.badger.page.settings.SettingPage
import top.mcxiafeng.badger.page.social.SocialPage
import top.mcxiafeng.badger.page.sync.SyncPage
import top.mcxiafeng.badger.ui.MainPagerState
import top.mcxiafeng.badger.ui.Navigator
import top.mcxiafeng.badger.ui.Route
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

class HomePage {

    companion object{
        @Composable
        fun Home(
            navigator: Navigator
        ){
            val pages = listOf("主页", "联系人", "名片夹", "设置")
            val icons = listOf(Lucide.Bot, Lucide.PawPrint, Lucide.Wallet, Lucide.Settings)
            val pagerState = rememberPagerState(pageCount = {4})
            val mainPagerState = MainPagerState.rememberMainPagerState(pagerState)
            LaunchedEffect(pagerState.currentPage) {
                mainPagerState.syncPage()
            }
            Scaffold(
                bottomBar = {
                    NavigationBar{
                        pages.forEachIndexed { index, string ->
                            NavigationBarItem(
                                selected = mainPagerState.selectedPage == index,
                                onClick = {
                                    mainPagerState.animateToPage(index)
                                },
                                icon = icons[index],
                                label = string
                            )
                        }
                    }
                }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                ) { page ->
                    when (page) {
//                        0 -> SocialPage.PageSocial { navigator.push(Route.Sync) }
                        0 -> SyncPage.PageSync{}
                        1 -> PersonPage.PagePerson()
                        2 -> CollectionPage.PageCollection()
                        3 -> SettingPage.PageSetting()
                    }
                }
            }
        }
    }


}