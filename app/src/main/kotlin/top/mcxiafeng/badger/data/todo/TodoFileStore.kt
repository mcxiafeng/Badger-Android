package top.mcxiafeng.badger.data.todo

import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Legacy array format is retained; malformed records never get replaced with an empty list. */
internal class TodoFileStore(directory: File) {
    private val file = AtomicFile(File(directory, FILE_NAME))
    private val generationFile = AtomicFile(File(directory, GENERATION_FILE))
    private val gson = Gson()
    val key: String = file.baseFile.canonicalPath

    fun generation(): String = if (exists(generationFile)) {
        generationFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } else ""

    fun read(generation: String): List<LocalTodo> {
        if (!exists(file)) return emptyList()
        val root = file.openRead().bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        require(root.isJsonArray) { "待办文件内容无效" }
        val records = root.asJsonArray.map { element ->
            require(element.isJsonObject) { "待办数据不完整" }
            parse(element.asJsonObject, generation)
        }
        require(records.map { it.id }.distinct().size == records.size) { "待办编号重复" }
        Log.d(TAG, "Read local tasks; count=${records.size}")
        return records
    }

    private fun parse(value: JsonObject, generation: String): LocalTodo {
        val id = value.string("id")
        val title = value.string("title")
        val date = value.string("date")
        require(id.isNotBlank() && title.isNotBlank()) { "待办数据不完整" }
        if (date.isNotBlank()) LocalDate.parse(date)
        val ids = value.get("contactIds")?.takeUnless { it.isJsonNull }?.asJsonArray
            ?.map { it.asString.toLong() }?.filter { it > 0 }?.distinct().orEmpty()
        return LocalTodo(
            id = id, title = title, note = value.string("note"), date = date,
            place = value.string("place"),
            contactIds = if (value.string("associationGeneration") == generation) ids else emptyList(),
            completed = value.get("completed")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
            createdAt = value.get("createdAt")?.takeUnless { it.isJsonNull }?.asLong ?: 0L,
            associationGeneration = generation,
        )
    }

    fun write(items: List<LocalTodo>) {
        val bytes = gson.toJson(items).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            Log.e(TAG, "Atomic task write failed", error)
            throw error
        }
        // AtomicFile can log a failed rename without throwing; do not publish uncommitted state.
        if (!file.openRead().use { it.readBytes() }.contentEquals(bytes)) {
            throw IOException("待办文件未能完整保存")
        }
        Log.d(TAG, "Committed local tasks; count=${items.size}")
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)?.takeUnless { it.isJsonNull } ?: return ""
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "待办字段格式无效" }
        return value.asString
    }

    private fun exists(target: AtomicFile): Boolean =
        target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()

    companion object {
        private const val TAG = "TodoFileStoreTester"
        const val FILE_NAME = "badger_local_todos.json"
        const val GENERATION_FILE = "badger_local_todo_contact_generation"
    }
}
