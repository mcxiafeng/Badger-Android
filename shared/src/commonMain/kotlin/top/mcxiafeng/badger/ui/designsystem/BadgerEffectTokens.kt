package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 特效材质 Token（K14 / 特效规格 §2）
 *
 * 家族 A = BadgerMaterials（磨砂，大面积表面用，映射 iOS system materials 五档）
 * 家族 B = BadgerGlass（液态玻璃 = 磨砂底 + 折射 + 边缘光学，浮层控件用，两档）
 *
 * 全部渲染参数（模糊半径/饱和度/色调/折射）从 token 取，shader 内不出现裸数字。
 * 明暗两套 tint 由 [BadgerMaterialSpec.tintFor] 按当前明暗取用（F4 自适应落点，
 * 明暗信号 = 主题深浅；背后内容逐像素亮度采样为后续增强，见规格 §10 风险表）。
 */
@Immutable
data class BadgerMaterialSpec(
    /** 磨砂模糊半径（dp）。渲染走 miuix-blur textureBlurEffect，上限受性能预算 §7 约束 */
    val blurRadius: Dp,
    /** 饱和度提升倍数（vibrancy，F2：不是干巴巴的 blur） */
    val saturation: Float,
    /** 浅色主题色调层（L3） */
    val tintLight: Color,
    /** 深色主题色调层（L3） */
    val tintDark: Color,
) {
    fun tintFor(isDark: Boolean): Color = if (isDark) tintDark else tintLight
}

/**
 * 家族 A — 磨砂材质五档（spec §2，映射 UIBlurEffect.Style）
 *
 * [调参 2026-09-06] 逆向校准。
 * [调参 2026-09-07-v3] 玻璃透穿问题修正：
 *   Apple iOS 26 `.regular` veil alpha = 0.718——tint 必须够实。
 *   glass 底材 regular 升到 tint 40%/50%（接近 chrome 的 48%/58%，但 blur 更低 = 不雾）。
 *   区分「玻璃感」vs「磨砂感」靠的是 blur 低（16dp vs 24dp），不是 tint 低。
 *   饱和度统一 1.8（Apple 实测值）。
 */
object BadgerMaterials {

    /** ultraThin：扫码页顶部控制条等轻量浮条 */
    val ultraThin = BadgerMaterialSpec(
        blurRadius = 8.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.20f), tintDark = Color.Black.copy(alpha = 0.28f),
    )

    /** thin：LetterTooltip、轻量浮条 */
    val thin = BadgerMaterialSpec(
        blurRadius = 12.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.28f), tintDark = Color.Black.copy(alpha = 0.38f),
    )

    /** regular：对话框面板、BottomSheet；**液态玻璃默认底材**（AndroidLiquidGlass 参考 blur 8dp + 实 tint = 玻璃感） */
    val regular = BadgerMaterialSpec(
        blurRadius = 8.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.40f), tintDark = Color.Black.copy(alpha = 0.50f),
    )

    /** thick：对话框 scrim 上的重点面板 */
    val thick = BadgerMaterialSpec(
        blurRadius = 20.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.48f), tintDark = Color.Black.copy(alpha = 0.58f),
    )

    /** chrome：底部导航栏底材（标准磨砂模式用；blur 高 = 磨砂感） */
    val chrome = BadgerMaterialSpec(
        blurRadius = 24.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.55f), tintDark = Color.Black.copy(alpha = 0.65f),
    )
}

/**
 * 家族 B — 液态玻璃规格（spec §2）：实 tint 底材 + 轻微边缘折射
 *
 * [调参 2026-09-07-v3] 透穿修正：底材 tint 升到 40%/50%，和磨砂拉开的是 blur 不是 tint。
 *   - 折射 -10dp（微弱 concave，边缘有弯曲感但不扭曲内容）
 *   - 色散 0.2（彩虹隐约可见）
 */
@Immutable
data class BadgerGlassSpec(
    /** 磨砂底材（家族 A 之一） */
    val base: BadgerMaterialSpec,
    /** 边缘折射区域宽度（L4：直边区域零位移，仅圆角弧段参与，控制 fillrate） */
    val edgeWidth: Dp,
    /** 折射位移高度（lens shader refractionHeight） */
    val refractionHeight: Dp,
    /** 折射位移强度（lens shader refractionAmount；负值 = 向内凹透镜） */
    val refractionAmount: Dp,
)

/**
 * 家族 B — 液态玻璃两档（spec §2）
 *
 * [调参 2026-09-07-v3] 透穿修正：底材统一用 regular（tint 40%/50%），不再用 thin。
 */
object BadgerGlass {

    /** 默认：NavBar（底材 regular）、FAB、FloatingToolbar */
    val glassRegular = BadgerGlassSpec(
        base = BadgerMaterials.regular,
        edgeWidth = 12.dp,
        refractionHeight = 8.dp,
        refractionAmount = (-10).dp,
    )

    /** 强调时刻：扫码取景框、放大态 QR 卡（底材 regular，折射稍强） */
    val glassClear = BadgerGlassSpec(
        base = BadgerMaterials.regular,
        edgeWidth = 16.dp,
        refractionHeight = 12.dp,
        refractionAmount = (-16).dp,
    )
}
