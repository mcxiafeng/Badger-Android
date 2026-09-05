package top.mcxiafeng.badger.utils

import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.platform.PlatformClipboard
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 通用工具（[KMP K13c] common 化后的保留面）。
 *
 * 原 Bitmap 落盘/相册/QR 半边已由 `platform/` 边界接管（ImageFiles/ImageCodec/
 * GallerySaver/QrCodeGenerator），本对象只剩跨端安全的纯工具。
 */
object Methods {

    fun copyToClipboard(label: String, text: String) {
        // [KMP K12] 复制走平台边界（label 保留参数形状，兼容既有调用方）
        PlatformClipboard.copy(text)
    }

    fun copyToClipboard(text: String, snackbarHostState: SnackbarHostState) {
        PlatformClipboard.copy(text)
        // [兼容] 原 Context 重载的形状收敛：仅复制 + 原样保留 snackbar 消费点
    }

    /** QR 卡内置色板（QrCodeCard 消费）。 */
    val qrColors = listOf(
        Color(0xFF000000),
        Color(0xFF3482FF),
        Color(0xFFE91E63),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFF9C27B0)
    )

    /**
     * ISO 字符串或 epoch millis → `yyyy-MM-dd HH:mm` 格式化（kotlinx-datetime 跨端实现，
     * 语义对齐原 java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", 默认 Locale)）。
     *
     * @param raw 原始时间字符串（ISO 格式或 epoch millis）
     * @param fallbackOnFailure 解析失败时的回退值（默认返回 null）
     * @return 格式化后的时间字符串，解析失败返回 [fallbackOnFailure]
     */
    fun formatDateTime(raw: String?, fallbackOnFailure: String? = null): String? {
        if (raw.isNullOrBlank()) return fallbackOnFailure
        raw.toLongOrNull()?.let { epoch ->
            return runCatching {
                formatLocal(Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault()))
            }.getOrNull() ?: fallbackOnFailure
        }
        return runCatching {
            // ISO 去小数/时区尾巴，对齐原 SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss") 解析段
            val trimmed = raw.substringBefore('.').substringBefore('+').substringBefore('Z')
            val local = LocalDateTime.parse(trimmed)
            formatLocal(local)
        }.getOrNull() ?: fallbackOnFailure
    }

    private fun formatLocal(local: LocalDateTime): String {
        val month = local.monthNumber.toString().padStart(2, '0')
        val day = local.dayOfMonth.toString().padStart(2, '0')
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        return "${local.year}-$month-$day $hour:$minute"
    }
}
