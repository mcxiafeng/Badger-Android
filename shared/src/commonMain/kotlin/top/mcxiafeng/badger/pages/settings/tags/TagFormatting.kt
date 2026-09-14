package top.mcxiafeng.badger.pages.settings.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val Tag.colorCompose: Color
    @Composable
    get() {
        val cs = MiuixTheme.colorScheme
        val c = Color(color)
        return if (c.alpha == 0f) cs.primary else c
    }

internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
