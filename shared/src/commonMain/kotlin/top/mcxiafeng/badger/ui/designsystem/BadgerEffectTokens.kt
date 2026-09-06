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
 * [调参 2026-09-06] 初版参数（blur 20–60 / veil 8–24%）对照逆向实测校准下调/上调：
 * - Apple 控制中心材质逆向（CAFilter：saturate→blur→brightness）= blur 30 / saturate 1.8 / tint 分层
 * - iOS 26 `.regular` 原生玻璃逐像素实测 = veil alpha 0.718、blur 仅 ~5.4pt（veil 实、模糊轻）
 * - Web 生产配方共识：blur 12–20 + saturate 160–180% + fill 10–55%，**blur >20–30 呈雾感/隐私屏**
 * 结论：磨砂的「实」来自低半径 + 够实的 veil，不是大半径薄纱。饱和度统一 1.8（Apple 实测值）。
 */
object BadgerMaterials {

    /** ultraThin：扫码页顶部控制条等轻量浮条 */
    val ultraThin = BadgerMaterialSpec(
        blurRadius = 12.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.20f), tintDark = Color.Black.copy(alpha = 0.30f),
    )

    /** thin：LetterTooltip、轻量浮条 */
    val thin = BadgerMaterialSpec(
        blurRadius = 16.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.28f), tintDark = Color.Black.copy(alpha = 0.40f),
    )

    /** regular：对话框面板、BottomSheet；glassRegular 底材 */
    val regular = BadgerMaterialSpec(
        blurRadius = 20.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.38f), tintDark = Color.Black.copy(alpha = 0.50f),
    )

    /** thick：对话框 scrim 上的重点面板 */
    val thick = BadgerMaterialSpec(
        blurRadius = 24.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.48f), tintDark = Color.Black.copy(alpha = 0.60f),
    )

    /** chrome：底部导航栏底材（NavBar 玻璃档底材；= Apple 控制中心逆向的 blur 30 档） */
    val chrome = BadgerMaterialSpec(
        blurRadius = 30.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.58f), tintDark = Color.Black.copy(alpha = 0.70f),
    )
}

/**
 * 家族 B — 液态玻璃规格（spec §2）：磨砂底 + 边缘折射
 *
 * 折射强度换算：规格「位移上限 ≈ 40%/60% 宽度」按 64dp 高的标准导航栏折算为
 * refractionAmount 24dp / 38dp；取景框等大面容器用 glassClear。
 */
@Immutable
data class BadgerGlassSpec(
    /** 磨砂底材（家族 A 之一） */
    val base: BadgerMaterialSpec,
    /** 边缘折射区域宽度（L4：直边区域零位移，仅圆角弧段参与，控制 fillrate） */
    val edgeWidth: Dp,
    /** 折射位移高度（lens shader refractionHeight） */
    val refractionHeight: Dp,
    /** 折射位移强度（lens shader refractionAmount） */
    val refractionAmount: Dp,
)

/**
 * 家族 B — 液态玻璃两档（spec §2）
 */
object BadgerGlass {

    /** 默认：NavBar（底材 chrome）、FAB、FloatingToolbar */
    val glassRegular = BadgerGlassSpec(
        base = BadgerMaterials.chrome,
        edgeWidth = 12.dp,
        refractionHeight = 24.dp,
        refractionAmount = 24.dp,
    )

    /** 强调时刻：扫码取景框、放大态 QR 卡（底材 ultraThin，折射更强） */
    val glassClear = BadgerGlassSpec(
        base = BadgerMaterials.ultraThin,
        edgeWidth = 16.dp,
        refractionHeight = 32.dp,
        refractionAmount = 38.dp,
    )
}
