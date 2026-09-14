package top.mcxiafeng.badger.utils

import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.platform.PlatformClipboard
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object Methods {

    fun copyToClipboard(label: String, text: String) {
        
        PlatformClipboard.copy(text)
    }

    fun copyToClipboard(text: String, snackbarHostState: SnackbarHostState) {
        PlatformClipboard.copy(text)
        
    }

    
    val qrColors = listOf(
        Color(0xFF000000),
        Color(0xFF3482FF),
        Color(0xFFE91E63),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFF9C27B0)
    )

    

    fun formatDateTime(raw: String?, fallbackOnFailure: String? = null): String? {
        if (raw.isNullOrBlank()) return fallbackOnFailure
        raw.toLongOrNull()?.let { epoch ->
            return runCatching {
                formatLocal(Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault()))
            }.getOrNull() ?: fallbackOnFailure
        }
        return runCatching {
            
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
