package top.mcxiafeng.badger.page.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MoveLeft
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class SocialPage {

    companion object {
        @Composable
        fun PageSocial(
            onSyncClick: () -> Unit
        ) {
            var selectedIndex by remember {
                mutableStateOf(0)
            }
            val options = listOf("微信","QQ","哔哩哔哩","GitHub")
            Column {
                TopAppBar(
                    title = "主页",
                    actions = {
                        IconButton(onClick = onSyncClick) {
                            Icon(Lucide.CloudSync, contentDescription = "返回")
                        }
                    }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(
                                    color = MiuixTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MiuixTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            OverlayDropdownPreference(
                                title = "选择平台",
                                items = options,
                                selectedIndex = selectedIndex,
                                onSelectedIndexChange = {
                                    selectedIndex = it
                                }
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(
                                    color = MiuixTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                        }
                    }
                }
            }
        }
    }
}