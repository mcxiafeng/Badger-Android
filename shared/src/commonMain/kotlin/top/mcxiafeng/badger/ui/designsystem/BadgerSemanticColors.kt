package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.ui.graphics.Color

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
        "douyin" to Color(0xFF000000),
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
