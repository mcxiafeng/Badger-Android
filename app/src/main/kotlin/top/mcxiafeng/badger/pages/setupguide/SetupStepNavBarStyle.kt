package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Check
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
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val STYLE_TAG = "SetupStepNavBarStyle"

private val PREVIEW_TABS = listOf("社交", "名片", "扫描", "设置")
private val PREVIEW_ICONS = listOf(
    Icons.Outlined.Person,
    Icons.Outlined.CreditCard,
    Icons.Outlined.QrCodeScanner,
    Icons.Outlined.Settings,
)

@Composable
internal fun SetupStepNavBarStyle(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var floatingSelected by remember { mutableStateOf(NavBarConfig.isFloatingEnabled(context)) }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(STYLE_TAG, "Nav bar style step skipped")
            onSkip()
        },
        onNext = {
            NavBarConfig.saveFloatingEnabled(context, floatingSelected)
            Log.d(STYLE_TAG, "Nav bar style step completed, floating=$floatingSelected")
            onNext()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 72.dp + floatingBarBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "选择导航栏样式",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "挑一个你喜欢的，后续随时可在设置中修改",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))

            NavBarStyleOptionCard(
                title = "经典",
                subtitle = "不悬浮的常规底栏",
                selected = !floatingSelected,
                onClick = { floatingSelected = false },
            ) {
                ClassicNavBarPreview()
            }
            Spacer(modifier = Modifier.height(12.dp))

            NavBarStyleOptionCard(
                title = "悬浮胶囊",
                subtitle = "悬浮式胶囊底栏（推荐）",
                selected = floatingSelected,
                onClick = { floatingSelected = true },
            ) {
                FloatingNavBarPreview()
            }
        }
    }
}

@Composable
private fun NavBarStyleOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
        label = "border",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "container",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.background(containerColor)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview()
        }
    }
}

@Composable
private fun ClassicNavBarPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            showDivider = false,
        ) {
            PREVIEW_TABS.forEachIndexed { index, label ->
                NavBarItem(
                    title = label,
                    icon = PREVIEW_ICONS[index],
                    selected = index == 0,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBarPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FloatingNavBar(
            selectedIndex = 0,
            pageOffset = 0f,
            onSelected = {},
            tabs = PREVIEW_TABS,
            icons = PREVIEW_ICONS,
        )
    }
}
