package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * UI 层 Tag 格式化工具。
 *
 * - [Tag.colorCompose] 把 `Long ARGB` 安全转成 Compose [Color]，
 *   并对 `Long = 0`（透明黑）做 fallback，避免 `Color.Transparent` 陷阱
 *   （参见 feedback_miuix_rules.md 的 Color 陷阱条目）。
 */
internal val Tag.colorCompose: Color
    @Composable
    get() {
        val cs = MiuixTheme.colorScheme
        val c = Color(color)
        return if (c.alpha == 0f) cs.primary else c
    }

/**
 * 把 Compose [Color] 安全转回 ARGB Long。
 *
 * 取代旧实现 `Color.value shr 32`（在 Compose 1.6+ 上 ULong 处理脆弱且易溢出）。
 * 走 [Color.toArgb] 拿 32-bit ARGB，再 `toLong() and 0xFFFFFFFFL` 保证 Room 字段无损。
 */
internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
