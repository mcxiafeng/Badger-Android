package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class PlatformEntry(
    val displayName: String? = null,
    val jumpLink: String = "",
    val originalLink: String? = null,
    val value: String? = null,
    val avatarUrl: String? = null
)
