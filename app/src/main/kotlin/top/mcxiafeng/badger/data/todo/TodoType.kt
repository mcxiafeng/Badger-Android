package top.mcxiafeng.badger.data.todo

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import java.util.UUID

@Immutable
internal data class TodoType(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: Long,
) {
    val isPreset: Boolean get() = presets.any { it.id == id }

    companion object {
        val presets = listOf(
            TodoType("red", "红色", 0xFFE55353),
            TodoType("orange", "橙色", 0xFFF08932),
            TodoType("yellow", "黄色", 0xFFE0AF17),
            TodoType("green", "绿色", 0xFF43A76B),
            TodoType("cyan", "青色", 0xFF2AA9A9),
            TodoType("blue", "蓝色", 0xFF477EDF),
            TodoType("violet", "紫色", 0xFF9362C8),
        )

        fun custom(name: String, color: Long) = TodoType(UUID.randomUUID().toString(), name, color)
    }
}
