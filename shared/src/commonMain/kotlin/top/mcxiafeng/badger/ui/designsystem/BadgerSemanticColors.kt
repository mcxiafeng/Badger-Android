package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 平台品牌色映射
 *
 * key 与 [top.mcxiafeng.badger.network.PlatformAdapterRegistry] 中的平台标识对齐。
 * 未匹配的平台返回 null，由调用方 fallback 到主题色。
 */
object BadgerPlatformColors {
    private val map = mapOf(
        "wechat" to Color(0xFF07C160),
        "weixin" to Color(0xFF07C160),
        "qq" to Color(0xFF12B7F5),
        "douyin" to Color(0xFF000000),
        "tiktok" to Color(0xFF000000),
        "weibo" to Color(0xFFE6162D),
        "bilibili" to Color(0xFF00A1D6),
        "bili" to Color(0xFF00A1D6),
        "xiaohongshu" to Color(0xFFFF2442),
        "redbook" to Color(0xFFFF2442),
        "github" to Color(0xFF24292F),
        "twitter" to Color(0xFF000000),
        "x" to Color(0xFF000000),
        "telegram" to Color(0xFF26A5E4),
        "discord" to Color(0xFF5865F2),
        "linkedin" to Color(0xFF0A66C2),
        "instagram" to Color(0xFFE4405F),
        "email" to Color(0xFF4285F4),
        "phone" to Color(0xFF34C759),
        "kuaishou" to Color(0xFFFF4906),
        "zhihu" to Color(0xFF0066FF),
        "jike" to Color(0xFF0ECDB0),
        "mastodon" to Color(0xFF6364FF),
        "threads" to Color(0xFF000000),
    )

    /**
     * 根据平台标识返回品牌色，未匹配返回 null。
     */
    fun get(key: String): Color? = map[key.lowercase().trim()]
}

/**
 * 标签色数组
 *
 * 8 色循环，用于联系人标签的视觉区分。
 * 颜色在浅色/深色模式下均有足够对比度。
 */
object BadgerTagColors {
    private val colors = listOf(
        Color(0xFF4CAF50), // 绿
        Color(0xFF2196F3), // 蓝
        Color(0xFFFF9800), // 橙
        Color(0xFF9C27B0), // 紫
        Color(0xFFE91E63), // 粉
        Color(0xFF00BCD4), // 青
        Color(0xFFFF5722), // 深橙
        Color(0xFF607D8B), // 蓝灰
    )

    /**
     * 根据索引循环返回标签色。
     */
    fun get(index: Int): Color = colors[index.mod(colors.size)]

    /**
     * 返回全部标签色（供 UI 选择器使用）。
     */
    fun all(): List<Color> = colors
}

/**
 * 语义色 Token（U06）
 *
 * 提供 success / warning / danger / info 四组语义色，每组包含：
 * - **主色**：用于图标、强调文字、StatusBadge 前景
 * - **onColor**：主色上的文字
 * - **container**：淡底，用于徽章/标签背景
 * - **onContainer**：container 上的文字
 *
 * 明暗两套自动切换：读取 [MiuixTheme.colorScheme] 的 background 亮度判定，
 * 兼容 System / Light / Dark / Monet 全部 6 种模式，无需应用层传 isDark。
 *
 * **danger** 直接委托 Miuix `error`，避免两套红色并存；现有 `colorScheme.error`
 * 调用点可逐步迁移到 `BadgerSemanticColors.danger`，视觉不变。
 *
 * 使用场景：同步状态、操作历史 StatusBadge、通知分级、表单校验提示。
 */
object BadgerSemanticColors {
    // ---- Light 模式原始值（供测试/引用） ----
    internal val successLight = Color(0xFF2E7D32)
    internal val onSuccessLight = Color.White
    internal val successContainerLight = Color(0xFFC8E6C9)
    internal val onSuccessContainerLight = Color(0xFF00390E)

    internal val warningLight = Color(0xFFED6E0A)
    internal val onWarningLight = Color.White
    internal val warningContainerLight = Color(0xFFFFE0B2)
    internal val onWarningContainerLight = Color(0xFF5C3400)

    internal val infoLight = Color(0xFF1A73E8)
    internal val onInfoLight = Color.White
    internal val infoContainerLight = Color(0xFFD3E3FD)
    internal val onInfoContainerLight = Color(0xFF001A41)

    // ---- Dark 模式原始值 ----
    internal val successDark = Color(0xFF81C784)
    internal val onSuccessDark = Color(0xFF00390E)
    internal val successContainerDark = Color(0xFF1B4332)
    internal val onSuccessContainerDark = Color(0xFFC8E6C9)

    internal val warningDark = Color(0xFFFFB74D)
    internal val onWarningDark = Color(0xFF5C3400)
    internal val warningContainerDark = Color(0xFF4E3417)
    internal val onWarningContainerDark = Color(0xFFFFE0B2)

    internal val infoDark = Color(0xFF7AB8FF)
    internal val onInfoDark = Color(0xFF001A41)
    internal val infoContainerDark = Color(0xFF1A2B4A)
    internal val onInfoContainerDark = Color(0xFFD3E3FD)

    /**
     * 当前是否处于深色主题。
     *
     * 使用标准感知亮度公式（WCAG relative luminance 近似），
     * 兼容 Monet 动态色——绿色/蓝色种子不会误判为深色。
     */
    private val isDark: Boolean
        @Composable get() = MiuixTheme.colorScheme.background.run {
            0.2126f * red + 0.7152f * green + 0.0722f * blue
        } < 0.5f

    // ---- Success ----
    val success: Color @Composable get() = if (isDark) successDark else successLight
    val onSuccess: Color @Composable get() = if (isDark) onSuccessDark else onSuccessLight
    val successContainer: Color @Composable get() = if (isDark) successContainerDark else successContainerLight
    val onSuccessContainer: Color @Composable get() = if (isDark) onSuccessContainerDark else onSuccessContainerLight

    // ---- Warning ----
    val warning: Color @Composable get() = if (isDark) warningDark else warningLight
    val onWarning: Color @Composable get() = if (isDark) onWarningDark else onWarningLight
    val warningContainer: Color @Composable get() = if (isDark) warningContainerDark else warningContainerLight
    val onWarningContainer: Color @Composable get() = if (isDark) onWarningContainerDark else onWarningContainerLight

    // ---- Danger（委托 Miuix error，单一红色来源） ----
    val danger: Color @Composable get() = MiuixTheme.colorScheme.error
    val onDanger: Color @Composable get() = MiuixTheme.colorScheme.onError
    val dangerContainer: Color @Composable get() = MiuixTheme.colorScheme.errorContainer
    val onDangerContainer: Color @Composable get() = MiuixTheme.colorScheme.onErrorContainer

    // ---- Info ----
    val info: Color @Composable get() = if (isDark) infoDark else infoLight
    val onInfo: Color @Composable get() = if (isDark) onInfoDark else onInfoLight
    val infoContainer: Color @Composable get() = if (isDark) infoContainerDark else infoContainerLight
    val onInfoContainer: Color @Composable get() = if (isDark) onInfoContainerDark else onInfoContainerLight
}
