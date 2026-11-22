package top.mcxiafeng.badger.data.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import top.mcxiafeng.badger.network.sync.FullSyncContract
import top.mcxiafeng.badger.network.sync.SyncJson
import top.mcxiafeng.badger.network.sync.SyncRoot
import java.io.IOException
import java.util.UUID

/** HTTP-boundary fixture: stateful CAS and receipts, with controlled response loss/corruption. */
internal class SyncServerFixture(private val server: String, private val user: String, private val epoch: String) : Interceptor {
    private val roots = linkedMapOf<String, SyncRoot>()
    private val receipts = mutableMapOf<String, Pair<JsonObject, JsonObject>>()
    private val assets = mutableMapOf<String, ByteArray>()
    private var revision = 1L
    private var frame: Pair<JsonObject, List<String>>? = null
    var loseNextPersonReply = false
    var loseNextAssetReply = false
    val assetAttempts = mutableListOf<String>()
    var tamperPage = false
    var redirectMutation = false
    var referenceOnNextFieldDelete = false
    var requestCount = 0
    var assetDownloads = 0
    val personAttempts = mutableListOf<String>()
    val hosts = mutableSetOf<String>()

    init {
        val self = SyncRoot("selfCard", UUID.randomUUID().toString(), "1", value = SyncJson.obj("profile" to
            SyncJson.obj("circleName" to null, "accounts" to emptyList<Any>(), "interestTags" to emptyList<Any>(), "avatarAssetId" to null)))
        roots[self.key] = self
    }

    fun people(): List<SyncRoot> = roots.values.filter { it.kind == "person" && !it.deleted }
    fun addPeople(count: Int) {
        val start = people().size
        repeat(count) { index ->
            val name = "SyncCapacity${start + index}"
            val root = SyncRoot("person", UUID.randomUUID().toString(), "1", value = SyncJson.obj(
                "alias" to name, "notes" to "合成容量备注", "source" to null,
                "profile" to SyncJson.obj("circleName" to name, "accounts" to emptyList<Any>(),
                    "interestTags" to emptyList<Any>(), "avatarAssetId" to null),
                "clientData" to SyncJson.obj("bio" to null, "avatarUrl" to null, "createTime" to 1,
                    "updateTime" to 1, "fields" to emptyList<Any>(), "platformDetails" to emptyList<Any>())))
            roots[root.key] = root
        }
        revision += count
    }

    fun tags(): List<SyncRoot> = roots.values.filter { it.kind == "tag" && !it.deleted }
    fun addTag(name: String): String {
        val root = SyncRoot("tag", UUID.randomUUID().toString(), "1", value = SyncJson.obj("name" to name,
            "memberIds" to emptyList<Any>(), "clientData" to SyncJson.obj("color" to 0xFF1976D2L,
                "source" to "manual", "showDot" to true, "createTime" to 1, "members" to emptyList<Any>())))
        roots[root.key] = root
        revision++
        return root.id
    }
    fun disablePhone() {
        val field = roots.values.single { it.kind == "fieldDefinition" && it.value?.str("fieldKey") == "phone" }
        roots[field.key] = field.copy(version = (field.version.toLong() + 1).toString(),
            value = field.value?.deepCopy()?.apply { addProperty("isEnabled", false) })
        revision++
    }
    fun enrichPerson() {
        val person = people().single()
        roots[person.key] = person.copy(version = (person.version.toLong() + 1).toString(), value = person.value?.deepCopy()?.apply {
            getAsJsonObject("profile").addProperty("circleName", "公开名")
            getAsJsonObject("profile").add("interestTags", SyncJson.element(listOf("主题A")))
            add("source", SyncJson.obj("kind" to "manual", "capturedAt" to "2026-01-01T00:00:00.000Z", "text" to "联系来源"))
        })
        revision++
    }

    fun changePersonNote(note: String) {
        val person = people().single()
        revision++
        roots[person.key] = person.copy(version = (person.version.toLong() + 1).toString(),
            value = person.value?.deepCopy()?.apply { addProperty("notes", note) })
    }

    @Synchronized override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requestCount++
        hosts.add(request.url.host)
        require(request.header("X-Badger-Sync-Schema") == "2")
        require(request.header("Cookie") == null)
        require(request.header("Authorization") == "Bearer ${"A".repeat(43)}")
        return when {
            request.url.encodedPath.endsWith("/capabilities") -> response(request, SyncJson.obj(
                "minSchemaVersion" to 1, "maxSchemaVersion" to 2, "requiredSchemaVersion" to 2))
            request.url.encodedPath.endsWith("/assets") -> uploadAsset(request)
            request.url.encodedPath.contains("/assets/") -> {
                assetDownloads++
                val bytes = requireNotNull(assets[request.url.encodedPath.substringAfterLast('/')])
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                    .body(bytes.toResponseBody("image/png".toMediaType())).build()
            }
            request.url.encodedPath.endsWith("/mutations") -> mutation(request)
            request.url.encodedPath.endsWith("/snapshots") -> snapshot(request)
            request.url.encodedPath.contains("/snapshots/") -> {
                val pageIndex = requireNotNull(request.url.queryParameter("pageToken")?.removePrefix("page_")?.toIntOrNull()) - 1
                val page = requireNotNull(frame).second[pageIndex]
                val text = if (tamperPage) page.replace("恶意", "篡改") else page
                if (tamperPage) require(text != page)
                response(request, text)
            }
            else -> error("Unexpected sync endpoint")
        }
    }

    fun referenceCustomFields() {
        val definitions = roots.values.filter { it.kind == "fieldDefinition" && it.value?.flag("isSystem") == false }.map { it.id }
        require(definitions.size == 2)
        addRemoteReference(definitions)
    }

    private fun addRemoteReference(definitionIds: List<String>) {
        val person = SyncRoot("person", UUID.randomUUID().toString(), "1", value = SyncJson.obj(
            "alias" to "云端新增联系人", "notes" to "云端备注", "source" to null,
            "profile" to SyncJson.obj("circleName" to "云端新增联系人", "accounts" to emptyList<Any>(),
                "interestTags" to emptyList<Any>(), "avatarAssetId" to null),
            "clientData" to SyncJson.obj("bio" to null, "avatarUrl" to null, "createTime" to 1, "updateTime" to 1,
                "fields" to definitionIds.map { definitionId -> SyncJson.obj("id" to UUID.randomUUID().toString(), "definitionId" to definitionId,
                    "value" to "新增引用", "createTime" to 1, "updateTime" to 1) }, "platformDetails" to emptyList<Any>())))
        roots[person.key] = person
        revision++
    }

    private fun uploadAsset(request: Request): Response {
        val body = request.body as MultipartBody
        val metadataPart = body.parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"metadata\"") == true }
        val filePart = body.parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"file\"") == true }
        val metadataBuffer = Buffer().also { metadataPart.body.writeTo(it) }
        val metadata = SyncJson.parse(metadataBuffer.readUtf8())
        val bytes = Buffer().also { filePart.body.writeTo(it) }.readByteArray()
        val id = requireNotNull(metadata.str("assetId"))
        val mutation = requireNotNull(metadata.str("mutationId"))
        assetAttempts.add(mutation)
        require(metadata.str("dataEpoch") == epoch && metadata.str("sha256") == SyncJson.hash(bytes))
        require(metadata.num("size") == bytes.size.toLong())
        receipts[mutation]?.let { (previous, ack) -> require(previous == metadata); return response(request, ack) }
        assets[id] = bytes
        roots["asset/$id"] = SyncRoot("asset", id, "1", value = SyncJson.obj("canonicalSha256" to SyncJson.hash(bytes),
            "canonicalSize" to bytes.size, "canonicalMime" to "image/png"))
        revision++
        val ack = SyncJson.obj("mutationId" to mutation, "dataEpoch" to epoch, "revision" to revision.toString(),
            "changes" to listOf(SyncJson.obj("kind" to "asset", "id" to id, "version" to "1", "deleted" to false)),
            "assets" to listOf(SyncJson.obj("assetId" to id, "canonicalSha256" to SyncJson.hash(bytes),
                "canonicalSize" to bytes.size, "canonicalMime" to "image/png")))
        receipts[mutation] = metadata to ack
        if (loseNextAssetReply) {
            loseNextAssetReply = false
            throw IOException("Controlled lost image receipt after commit")
        }
        return response(request, ack)
    }

    private fun mutation(request: Request): Response {
        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        val input = SyncJson.parse(buffer.readUtf8())
        val mutationId = requireNotNull(input.str("mutationId"))
        val kind = requireNotNull(input.str("kind"))
        val id = requireNotNull(input.str("id"))
        if (kind == "person") personAttempts.add(mutationId)
        if (redirectMutation) return response(request, "{}", 302).newBuilder().header("Location", "https://wrong.example.test/receive").build()
        receipts[mutationId]?.let { (original, ack) ->
            require(original == input) { "Client reused an immutable intent with a different body" }
            return response(request, ack)
        }
        if (kind == "fieldDefinition" && input.str("action") == "delete") {
            if (referenceOnNextFieldDelete) { referenceOnNextFieldDelete = false; addRemoteReference(listOf(id)) }
            val inUse = people().any { person -> person.value?.details()?.items("fields")?.any { it.str("definitionId") == id } == true }
            if (inUse) return response(request, SyncJson.obj("error" to SyncJson.obj("code" to "VALIDATION_FAILED",
                "message" to "字段不合法", "requestId" to UUID.randomUUID().toString(),
                "fieldErrors" to listOf(SyncJson.obj("path" to "id", "code" to "REFERENCE_IN_USE", "message" to "字段被使用")),
                "details" to SyncJson.obj())), 422)
        }
        val key = "$kind/$id"
        val previous = roots[key]
        if (input.str("expectedVersion") != (previous?.version ?: "0")) return response(request, SyncJson.obj("error" to
            SyncJson.obj("code" to "VERSION_CONFLICT", "message" to "版本冲突", "requestId" to UUID.randomUUID().toString(),
                "fieldErrors" to emptyList<Any>(), "details" to SyncJson.obj("mutationId" to mutationId, "observedRevision" to revision.toString(), "targets" to emptyList<Any>()))), 409)
        val deleted = input.str("action") == "delete"
        val next = SyncRoot(kind, id, ((previous?.version?.toLong() ?: 0) + 1).toString(), deleted,
            input["value"].takeUnless { it.isJsonNull }?.asJsonObject)
        FullSyncContract.validate(next)
        revision++
        roots[key] = next
        val ack = SyncJson.obj("mutationId" to mutationId, "dataEpoch" to epoch, "revision" to revision.toString(),
            "changes" to listOf(SyncJson.obj("kind" to kind, "id" to id, "version" to next.version, "deleted" to deleted)))
        receipts[mutationId] = input.deepCopy() to ack.deepCopy()
        if (kind == "person" && loseNextPersonReply) {
            loseNextPersonReply = false
            throw IOException("Controlled lost response after commit")
        }
        return response(request, ack)
    }

    private fun snapshot(request: Request): Response {
        val chunks = roots.values.sortedBy { it.key }.chunked(SNAPSHOT_PAGE_ROWS).map { chunk ->
            JsonArray().apply {
                for (root in chunk) add(root.json().apply {
                    add("profileId", SyncJson.element(if (root.kind in setOf("person", "selfCard") && !root.deleted) root.id else null))
                })
            }
        }
        val encoded = chunks.map { SyncJson.canonical(it).toByteArray(Charsets.UTF_8) }
        val id = UUID.randomUUID().toString()
        val descriptors = chunks.mapIndexed { index, rows -> SyncJson.obj("pageNumber" to index,
            "rowCount" to rows.size(), "byteCount" to encoded[index].size, "sha256" to SyncJson.hash(encoded[index])) }
        val manifest = SyncJson.obj("serverId" to server, "userId" to user, "dataEpoch" to epoch, "snapshotId" to id,
            "revision" to revision.toString(), "schemaVersion" to 2, "totalRows" to roots.size,
            "totalBytes" to encoded.sumOf { it.size }, "pageCount" to chunks.size,
            "expiresAt" to "2099-01-01T00:00:00.000Z", "pages" to descriptors)
        val metadata = manifest.deepCopy().apply {
            addProperty("status", "snapshot"); addProperty("digestAlgorithm", "badger-jcs-sha256-1")
            addProperty("digest", SyncJson.hash(SyncJson.canonical(manifest).toByteArray(Charsets.UTF_8)))
            addProperty("firstPageToken", "page_1")
        }
        val pages = chunks.mapIndexed { index, rows -> SyncJson.canonical(SyncJson.obj(
            "serverId" to server, "userId" to user, "dataEpoch" to epoch, "snapshotId" to id,
            "revision" to revision.toString(), "pageNumber" to index, "rowCount" to rows.size(),
            "byteCount" to encoded[index].size, "sha256" to SyncJson.hash(encoded[index]), "rows" to rows,
            "nextPageToken" to if (index + 1 < chunks.size) "page_${index + 2}" else null)) }
        frame = metadata to pages
        return response(request, metadata)
    }

    private companion object { const val SNAPSHOT_PAGE_ROWS = 1_000 }

    private fun response(request: Request, body: JsonObject, status: Int = 200): Response = response(request, SyncJson.encode(body), status)
    private fun response(request: Request, body: String, status: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("test")
        .body(body.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
}
