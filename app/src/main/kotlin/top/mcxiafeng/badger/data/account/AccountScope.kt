package top.mcxiafeng.badger.data.account

import androidx.compose.runtime.Immutable
import java.security.MessageDigest

/** A fixed business-data space. Never resolve the active account again during an operation. */
@Immutable
data class AccountScope(
    val key: String,
    val serverId: String?,
    val userId: String?,
    val dataEpoch: String?,
    val username: String,
    val baseUrl: String,
    val databaseName: String,
) {
    val isLocal: Boolean get() = serverId == null

    companion object {
        const val LOCAL_DATABASE_NAME = "badger_database"

        internal fun local(spaceId: String) = AccountScope(
            key = "local_$spaceId", serverId = null, userId = null, dataEpoch = null,
            username = "本地用户", baseUrl = "", databaseName = LOCAL_DATABASE_NAME,
        )

        internal fun remote(
            serverId: String, userId: String, dataEpoch: String, username: String, baseUrl: String,
        ): AccountScope {
            require(serverId.isNotBlank() && userId.isNotBlank() && dataEpoch.isNotBlank()) {
                "Remote account identity must not be blank"
            }
            // Length prefixes avoid delimiter collisions; epochs must never reuse old sync state.
            val identity = listOf(serverId, userId, dataEpoch).joinToString("") { "${it.length}:$it" }
            val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return AccountScope("remote_$hash", serverId, userId, dataEpoch, username, baseUrl, "badger_$hash")
        }
    }
}
