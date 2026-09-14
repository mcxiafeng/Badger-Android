package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

object BadgerSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val lgx = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object BadgerRadius {
    
    val chip = 8.dp

    
    val inner = 12.dp

    
    val card = 16.dp

    
    val container = 20.dp

    
    val sm = chip
    val md = inner
    val lg = card
    val xl = 24.dp
}

object BadgerElevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
}

object BadgerMotion {
    
    const val DURATION_FAST = 200

    
    const val DURATION_BASE = 300

    
    const val DURATION_SCROLL_SETTLE = 300

    
    fun pushSpring(visibilityThreshold: Float = 0.01f): SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = visibilityThreshold,
    )

    

    fun pushSpringOffset(): SpringSpec<IntOffset> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
    )

    
    fun expressiveSpring(dampingRatio: Float = 0.5f, stiffness: Float = 300f): SpringSpec<Float> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

object BadgerTypeScale {
    val title1: TextStyle @Composable get() = MiuixTheme.textStyles.title1
    val title2: TextStyle @Composable get() = MiuixTheme.textStyles.title2
    val title3: TextStyle @Composable get() = MiuixTheme.textStyles.title3
    val title4: TextStyle @Composable get() = MiuixTheme.textStyles.title4
    val subtitle: TextStyle @Composable get() = MiuixTheme.textStyles.subtitle
    val body1: TextStyle @Composable get() = MiuixTheme.textStyles.body1
    val body2: TextStyle @Composable get() = MiuixTheme.textStyles.body2
    val footnote1: TextStyle @Composable get() = MiuixTheme.textStyles.footnote1
    val footnote2: TextStyle @Composable get() = MiuixTheme.textStyles.footnote2
}
