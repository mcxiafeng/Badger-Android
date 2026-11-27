package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.FloatingNavBar
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.NavBarItem
import top.mcxiafeng.badger.ui.navigation.MainTab
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val STYLE_TAG = "SetupStepNavBarStyle"

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
                text = "稍后可在「UI 设置」中修改",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))

            NavBarStyleOptionCard(
                title = "经典底栏",
                selected = !floatingSelected,
                onClick = { floatingSelected = false },
            ) {
                ClassicNavBarPreview()
            }
            Spacer(modifier = Modifier.height(12.dp))

            NavBarStyleOptionCard(
                title = "悬浮胶囊",
                selected = floatingSelected,
                onClick = { floatingSelected = true },
            ) {
                FloatingNavBar()
            }
        }
    }
}

@Composable
private fun NavBarStyleOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "container",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.background(containerColor)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(BadgerMotion.DURATION_FAST, easing = FastOutSlowInEasing)),
                ) {
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
            .clip(BadgerShapes.Surface)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            showDivider = false,
        ) {
            MainTab.entries.forEach { tab ->
                NavBarItem(
                    title = tab.label,
                    icon = tab.icon,
                    selected = tab == MainTab.MY_CARD,
                    onClick = {},
                )
            }
        }
    }
}
