package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一间距 Token（U05）
 *
 * 基于 4dp 基准网格，覆盖 Badger 全场景间距需求。
 * 用于替代散落在各页面中的硬编码 dp 值。
 * 特别说明：lgx(20dp) 用于字母标题左侧缩进等需要 16dp 与 24dp 之间的场景。
 */
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

/**
 * 统一圆角 Token（U05 层次化语义 + 同心圆角规则）
 *
 * 同心嵌套规则（特效规格 §4）：内圆角 = 外圆角 − 层间 padding。
 * 例如 container(20dp) 卡片内嵌 card(16dp) 面板，层间留 4dp。
 *
 * 语义名是 canonical；sm/md/lg/xl 为既有调用点的旧别名，勿在新代码使用。
 */
object BadgerRadius {
    /** chip / 小标签 / 图标按钮 */
    val chip = 8.dp

    /** 内组件：卡片内的输入行、分段控件 */
    val inner = 12.dp

    /** 标准卡片 */
    val card = 16.dp

    /** 大容器：BottomSheet、对话框面板、悬浮导航栏胶囊外层 */
    val container = 20.dp

    // ---- 旧别名（兼容既有调用点，新代码用上方语义名） ----
    val sm = chip
    val md = inner
    val lg = card
    val xl = 24.dp
}

/**
 * 统一阴影层级 Token
 *
 * 对应 miuix Surface 的 elevation 语义。
 */
object BadgerElevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
}

/**
 * 动效 Token（U05 新增）
 *
 * 全部动效常量单一来源：转场（NavTransitions）、滚动收缩（LiquidGlassNavBar）、
 * 扫描线等接此处，不留散落 tween(300)/裸数字。
 *
 * spring 规格：
 * - [pushSpring]：路由转场类位移，dampingRatio 0.9（无可见振荡，快速收敛）
 * - [expressiveSpring]：NavBar 水滴等 expressive 交互，保留现有阻尼参数（特效规格 §4「现值可用」）
 */
object BadgerMotion {
    /** 快速反馈：按压、色值切换、小位移 */
    const val DURATION_FAST = 200

    /** 标准转场：页面 push/pop（NavTransitions 引用此处，单一来源） */
    const val DURATION_BASE = 300

    /** 滚动停止后恢复原形态的延迟（特效规格 §4 滚动最小化） */
    const val DURATION_SCROLL_SETTLE = 300

    /** 转场位移用低弹 spring：无振荡、300ms 级收敛（U09 接 NavTransitions 时启用） */
    fun pushSpring(visibilityThreshold: Float = 0.01f): SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = visibilityThreshold,
    )

    /** expressive 交互 spring（水滴指示器等）：参数与重做前一致 */
    fun expressiveSpring(dampingRatio: Float = 0.5f, stiffness: Float = 300f): SpringSpec<Float> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * 字阶语义别名（U05 新增）
 *
 * 指向 MiuixTheme.textStyles，页面用语义名取样式而非硬编码 fontSize+fontWeight。
 * 层级：title1(32) > title2(24) > title3(20) > title4(18) > subtitle(14 Bold)
 *      > body1(16) > body2(14) > footnote1(13) > footnote2(11)
 *
 * MiuixTheme.textStyles 只能在 @Composable 作用域读取，故暴露为 @Composable getter。
 */
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
