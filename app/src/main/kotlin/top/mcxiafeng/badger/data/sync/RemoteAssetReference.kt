package top.mcxiafeng.badger.data.sync

import android.util.Log
import com.google.gson.JsonObject
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.utils.atomicWrite
import java.io.File

/** Durable metadata is not an image cache entry. Evicting image bytes never clears this reference. */
data class RemoteAssetReference(val id: String, val sha256: String, val size: Long) {
    fun json(): JsonObject = SyncJson.obj("assetId" to id, "canonicalSha256" to sha256,
        "canonicalSize" to size, "canonicalMime" to "image/png")

    fun write(file: File): File {
        val encoded = SyncJson.encode(json())
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        if (!file.isFile || file.length() != bytes.size.toLong() || file.readText(Charsets.UTF_8) != encoded) {
            atomicWrite(file) { it.write(bytes) }
            Log.d(TAG, "已持久化图片引用，图片内容按显示需要读取")
        }
        return file
    }

    companion object {
        const val SUFFIX = ".asset.json"
        const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
        private const val MAX_REFERENCE_BYTES = 4096L
        private const val TAG = "RemoteAssetReferenceTester"
        private val HASH = Regex("[0-9a-f]{64}")

        fun descriptor(id: String, value: JsonObject): RemoteAssetReference {
            SyncJson.id(id)
            val hash = requireNotNull(SyncJson.text(value, "canonicalSha256"))
            val size = SyncJson.long(value, "canonicalSize")
            require(HASH.matches(hash) && size in 1..MAX_IMAGE_BYTES.toLong() &&
                SyncJson.text(value, "canonicalMime") == "image/png") { "远端图片描述无效，原资料已保留" }
            return RemoteAssetReference(id, hash, size)
        }

        fun read(file: File): RemoteAssetReference {
            require(file.name.endsWith(SUFFIX) && file.isFile && file.length() in 1..MAX_REFERENCE_BYTES)
            val value = SyncJson.parse(file.readText(Charsets.UTF_8))
            return descriptor(requireNotNull(SyncJson.text(value, "assetId")), value)
        }
    }
}
