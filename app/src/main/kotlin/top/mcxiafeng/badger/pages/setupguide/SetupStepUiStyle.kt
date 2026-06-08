package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SetupStepUiStyle(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    var floatingEnabled by remember { mutableStateOf(NavBarConfig.isFloatingEnabled(context)) }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(TAG, "UI style step skipped")
            onSkip()
        },
        onNext = {
            Log.d(TAG, "UI style step completed")
            onNext()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "选择外观风格",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "个性化你的导航栏",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                SwitchPreference(
                    title = "悬浮导航栏",
                    summary = "胶囊式底部导航栏，进入主界面后生效",
                    checked = floatingEnabled,
                    onCheckedChange = { newValue ->
                        floatingEnabled = newValue
                        NavBarConfig.saveFloatingEnabled(context, newValue)
                        Log.d(TAG, "Floating nav bar: $newValue")
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            NavBarPreview(
                floatingEnabled = floatingEnabled
            )
        }
    }
}

@Composable
private fun NavBarPreview(
    floatingEnabled: Boolean
) {
    val tabs = listOf("社交", "名片", "扫描", "设置")
    val icons = listOf(
        Icons.Outlined.Person,
        Icons.Outlined.CreditCard,
        Icons.Outlined.QrCodeScanner,
        Icons.Outlined.Settings
    )

    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                if (floatingEnabled) {
                    FloatingNavBar(
                        selectedIndex = 0,
                        pageOffset = 0f,
                        onSelected = {},
                        tabs = tabs,
                        icons = icons,
                        color = surfaceColor
                    )
                } else {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        showDivider = false
                    ) {
                        tabs.forEachIndexed { index, label ->
                            NavBarItem(
                                title = label,
                                icon = icons[index],
                                selected = index == 0,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
