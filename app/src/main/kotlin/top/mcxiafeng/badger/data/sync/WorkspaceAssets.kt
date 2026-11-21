package top.mcxiafeng.badger.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.auth.AuthSession
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import top.mcxiafeng.badger.network.sync.SyncTransport
import top.mcxiafeng.badger.utils.atomicWrite
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Immutable upload files and verified private downloads, confined to one captured account directory. */
internal class WorkspaceAssets(private val context: Context) {
    private val directory = File(context.filesDir, "sync-assets").apply { check(mkdirs() || isDirectory) }

    suspend fun reference(path: String?, ledger: SyncLedger): String? {
        if (path.isNullOrBlank()) return null
        val source = File(path).canonicalFile
        require(source.toPath().startsWith(context.filesDir.canonicalFile.toPath())) { "图片不属于当前用户空间" }
        require(source.isFile && source.length() in 1..MAX_DOWNLOAD_BYTES.toLong()) { "本机图片不可读取，资料已保留" }
        val bytes = source.readBytes()
        val hash = SyncJson.hash(bytes)
        ledger.get("imageHash/$hash")?.let { return it }
        val id = UUID.randomUUID().toString()
        val staged = File(directory, "$id.source")
        atomic(staged, bytes)
        ledger.put("imageHash/$hash", id)
        ledger.put("upload/$id", SyncJson.encode(SyncJson.obj("id" to id,
            "mutationId" to UUID.randomUUID().toString(), "sourceHash" to hash)))
        Log.d(TAG, "已固定待上传图片副本")
        return id
    }

    suspend fun upload(db: AppDatabase, transport: SyncTransport, session: AuthSession) {
        val pending = db.withTransaction { SyncLedger.load(db).entries("upload/") }
        for ((key, encoded) in pending) {
            val record = SyncJson.parse(encoded)
            val id = SyncJson.id(requireNotNull(SyncJson.text(record, "id")))
            val png = preparePng(id, requireNotNull(SyncJson.text(record, "sourceHash")))
            val bytes = png.readBytes()
            val metadata = SyncJson.obj("mutationId" to SyncJson.text(record, "mutationId"),
                "dataEpoch" to session.dataEpoch, "assetId" to id, "sha256" to SyncJson.hash(bytes),
                "size" to bytes.size, "mime" to "image/png")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, SyncJson.encode(metadata).toRequestBody(JSON_TYPE))
                .addFormDataPart("file", "$id.png", png.asRequestBody(PNG_TYPE)).build()
            val ack = SyncJson.parse(String(transport.bytes(session, "/api/v1/assets", body), Charsets.UTF_8))
            require(SyncJson.text(ack, "mutationId") == SyncJson.text(metadata, "mutationId") &&
                SyncJson.text(ack, "dataEpoch") == session.dataEpoch)
            val descriptor = SyncJson.array(ack, "assets").single().asJsonObject
            require(SyncJson.text(descriptor, "assetId") == id)
            db.withTransaction {
                val ledger = SyncLedger.load(db)
                ledger.put("asset/$id", SyncJson.encode(descriptor))
                ledger.highWater(requireNotNull(SyncJson.text(ack, "revision")))
                ledger.remove(key)
            }
            Log.d(TAG, "私有图片上传已确认")
        }
    }

    private fun preparePng(id: String, sourceHash: String): File {
        val png = File(directory, "$id.upload.png")
        if (png.exists()) {
            require(png.length() in 1..MAX_INGRESS_BYTES.toLong())
            return png
        }
        val source = File(directory, "$id.source")
        require(source.isFile && SyncJson.hash(source.readBytes()) == sourceHash) { "待上传图片副本校验失败" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, options)
        require(options.outWidth in 1..MAX_DIMENSION && options.outHeight in 1..MAX_DIMENSION &&
            options.outWidth.toLong() * options.outHeight <= MAX_PIXELS) { "图片超过云端尺寸限制" }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(source.path)) { "图片解码失败" }
        val temporary = File(directory, "$id.encoding")
        try {
            FileOutputStream(temporary).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
            require(temporary.length() in 1..MAX_INGRESS_BYTES.toLong()) { "图片超过云端上传限制" }
            atomic(png, temporary.readBytes())
        } finally {
            bitmap.recycle()
            if (temporary.exists() && !temporary.delete()) Log.w(TAG, "临时图片文件未能清理")
        }
        return png
    }

    suspend fun download(roots: List<SyncRoot>, transport: SyncTransport, session: AuthSession): Map<String, File> {
        val result = linkedMapOf<String, File>()
        for (root in roots.filter { it.kind == "asset" && !it.deleted }) {
            val descriptor = requireNotNull(root.value)
            val sha = requireNotNull(SyncJson.text(descriptor, "canonicalSha256"))
            require(HASH.matches(sha) && SyncJson.text(descriptor, "canonicalMime") == "image/png")
            val size = SyncJson.long(descriptor, "canonicalSize")
            require(size in 1..MAX_DOWNLOAD_BYTES.toLong())
            val file = File(directory, "${SyncJson.id(root.id)}.png")
            val valid = file.isFile && file.length() == size && SyncJson.hash(file.readBytes()) == sha
            if (!valid) {
                val bytes = transport.bytes(session, "/api/v1/assets/${root.id}", maxBytes = MAX_DOWNLOAD_BYTES)
                require(bytes.size.toLong() == size && SyncJson.hash(bytes) == sha) { "云端图片校验失败" }
                atomic(file, bytes)
                Log.d(TAG, "私有图片下载校验完成")
            }
            result[root.id] = file
        }
        return result
    }

    suspend fun remember(files: Map<String, File>, ledger: SyncLedger) {
        val root = context.filesDir.canonicalPath
        if (ledger.get("space/filesRoot") != root) ledger.put("space/filesRoot", root)
        for ((id, file) in files) {
            ledger.put("imageHash/${SyncJson.hash(file.readBytes())}", id)
            ledger.put("assetPath/$id", file.canonicalPath)
        }
    }

    private fun atomic(file: File, bytes: ByteArray) = atomicWrite(file) { it.write(bytes) }

    companion object {
        private const val TAG = "WorkspaceAssetsTester"
        private const val MAX_INGRESS_BYTES = 5 * 1024 * 1024
        private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
        private const val MAX_DIMENSION = 8192
        private const val MAX_PIXELS = 20_000_000L
        private const val PNG_QUALITY = 100
        private val HASH = Regex("[0-9a-f]{64}")
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PNG_TYPE = "image/png".toMediaType()
    }
}
