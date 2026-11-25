package top.mcxiafeng.badger.data.todo

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import com.google.gson.annotations.SerializedName

@Immutable
data class LocalTodo(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("title") val title: String,
    @SerializedName("note") val note: String = "",
    /** ISO local date, or empty when no date was selected. */
    @SerializedName("date") val date: String = "",
    @SerializedName("place") val place: String = "",
    @SerializedName("contactIds") val contactIds: List<Long> = emptyList(),
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("associationGeneration") val associationGeneration: String = "",
    /** ISO local time, or empty for a date-only task. */
    @SerializedName("time") val time: String = "",
    @SerializedName("reminder") val reminder: TodoReminder? = null,
    @SerializedName("priority") val priority: TodoPriority = TodoPriority.LOW,
    @SerializedName("typeId") val typeId: String = "",
    @SerializedName("backgroundId") val backgroundId: String = "",
    @SerializedName("backgroundScale") val backgroundScale: TodoBackgroundScale? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
)

enum class TodoBackgroundScale(val label: String) {
    @SerializedName("FILL") FILL("填充"),
    @SerializedName("FIT") FIT("适应"),
}

@Immutable
data class TodoReminder(
    @SerializedName("daysBefore") val daysBefore: Long,
    @SerializedName("time") val time: String,
) {
    init {
        require(daysBefore in 0..Int.MAX_VALUE.toLong())
        LocalTime.parse(time)
    }
}

enum class TodoPriority(val label: String, val marker: String) {
    @SerializedName("LOW") LOW("低", ""),
    @SerializedName("MEDIUM") MEDIUM("中", "！"),
    @SerializedName("HIGH") HIGH("高", "！！"),
}

internal fun LocalTodo.reminderAt(zone: ZoneId = ZoneId.systemDefault()): Instant? =
    reminder?.takeUnless { completed || date.isBlank() }?.at(date, zone)

internal fun TodoReminder.at(date: String, zone: ZoneId = ZoneId.systemDefault()): Instant =
    LocalDate.parse(date).minusDays(daysBefore).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

internal fun todoScheduleError(date: String, time: String, reminder: TodoReminder?): String? {
    if (date.isBlank() && (time.isNotBlank() || reminder != null)) return "请先设置日期"
    if (date.isNotBlank()) LocalDate.parse(date)
    if (time.isNotBlank()) LocalTime.parse(time)
    return null
}

class TodoConflictException : IllegalStateException("待办已被修改或删除，请返回列表后重新打开")
