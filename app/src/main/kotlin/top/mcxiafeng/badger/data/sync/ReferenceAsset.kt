package top.mcxiafeng.badger.data.sync

import android.util.Log
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.utils.atomicWrite
import java.io.File

/** Each business reference owns a separate file, so removing one avatar cannot delete another background. */
internal fun referenceAsset(ledger: SyncLedger, assetId: String?, reference: String): String? {
    if (assetId == null) return null
    SyncJson.id(assetId)
    val root = File(requireNotNull(ledger.get("space/filesRoot"))).canonicalFile
    val source = File(requireNotNull(ledger.get("assetPath/$assetId")) { "远端图片尚未完成校验" }).canonicalFile
    require(source.isFile && source.toPath().startsWith(root.toPath())) { "图片不属于此用户空间" }
    val directory = File(root, "sync-references").apply { check(isDirectory || mkdirs()) }.canonicalFile
    require(directory.toPath().startsWith(root.toPath()))
    val name = SyncJson.hash("$reference/$assetId".toByteArray(Charsets.UTF_8)) + ".image"
    val file = File(directory, name)
    val bytes = source.readBytes()
    if (!file.isFile || file.length() != bytes.size.toLong() || SyncJson.hash(file.readBytes()) != SyncJson.hash(bytes)) {
        atomicWrite(file) { it.write(bytes) }
        Log.d("ReferenceAssetTester", "已创建业务图片的独立文件引用")
    }
    return file.canonicalPath
}
