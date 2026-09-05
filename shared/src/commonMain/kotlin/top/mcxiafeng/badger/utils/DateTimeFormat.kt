package top.mcxiafeng.badger.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * [KMP K13c] epoch millis → `yyyy-MM-dd HH:mm`（跨端，替代 java.text.SimpleDateFormat 的
 * "yyyy-MM-dd HH:mm" 用法；时区 = 系统本地）。
 */
fun formatEpochDateTime(epoch: Long): String {
    val local = epoch.toLocalDateTime()
    return "${local.year}-${local.monthNumber.p2()}-${local.dayOfMonth.p2()} ${local.hour.p2()}:${local.minute.p2()}"
}

/** epoch millis → `yyyy-MM-dd`。 */
fun formatEpochDate(epoch: Long): String {
    val local = epoch.toLocalDateTime()
    return "${local.year}-${local.monthNumber.p2()}-${local.dayOfMonth.p2()}"
}

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

private fun Int.p2(): String = toString().padStart(2, '0')
