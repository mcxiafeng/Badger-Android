package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [Phase 1] 传输层路径迁移（v1 前缀 → api 前缀）回归锚点。
 *
 * 覆盖 QA 检查指出的缺口：机械路径替换只有 `/api/resolver` 一条被
 * [ContactNetworkResolverTest] 锚定，其余 API 类的路径改动均无直接测试。
 * 本测试逐一断言每个 API 方法发出的 HTTP path —— 当后续 Phase 3/4 精修为
 * 真实域路径（如 /api/contacts → /api/user/persons）时，这里会失败提醒更新断言。
 *
 * 复用 [LocalHttpServer] 进程内服务端基建，走真实 OkHttp 栈。
 */
class ApiPathMigrationTest {

    private lateinit var server: LocalHttpServer
    private lateinit var core: ApiCore

    @Before
    fun setUp() {
        server = LocalHttpServer().also { it.start() }
        core = ApiCore(server.baseUrl, OkHttpClient(), { null })
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun assertPath(expect: String) {
        assertThat(server.requestCount.get()).isEqualTo(1)
        assertThat(server.lastPath.get()).isEqualTo(expect)
    }

    // ============ ContactApi（Phase 3 将精修为 /api/user/persons） ============

    @Test
    fun contactApi_createContact_path() {
        server.enqueue(200, """{"id":"c1","server_id":"s1","version":1,"contact":{}}""")
        ContactApi(core).createContact(com.google.gson.JsonObject(), null)
        assertPath("/api/contacts")
    }

    @Test
    fun contactApi_getContact_path() {
        server.enqueue(200, """{"id":"c1","server_id":"s1","version":1,"contact":{}}""")
        ContactApi(core).getContact("s1")
        assertPath("/api/contacts/s1")
    }

    @Test
    fun contactApi_patchContact_path() {
        server.enqueue(200, """{"id":"c1","server_id":"s1","version":1,"contact":{}}""")
        ContactApi(core).patchContact("s1", com.google.gson.JsonObject(), null)
        assertPath("/api/contacts/s1")
    }

    @Test
    fun contactApi_deleteContact_path() {
        server.enqueue(200, """{}""")
        ContactApi(core).deleteContact("s1", null)
        assertPath("/api/contacts/s1")
    }

    @Test
    fun contactApi_mergeContact_path() {
        server.enqueue(200, """{"id":"c1","server_id":"s1","version":1,"contact":{}}""")
        ContactApi(core).mergeContact("t1", listOf("m1"), null)
        assertPath("/api/contacts/t1/merge")
    }

    @Test
    fun contactApi_listContacts_path() {
        server.enqueue(200, """{"items":[],"next_since":0}""")
        ContactApi(core).listContacts(null, 50)
        assertPath("/api/contacts?limit=50")
    }

    // ============ BackupApi（Phase 4 将精修为 /api/user/backups） ============

    @Test
    fun backupApi_listBackups_path() {
        server.enqueue(200, """{"backups":[]}""")
        BackupApi(core).listBackups()
        assertPath("/api/backups")
    }

    @Test
    fun backupApi_uploadBackup_path() {
        server.enqueue(200, """{"id":"1","name":"n","size":1,"created_at":"t"}""")
        BackupApi(core).uploadBackup("""{}""")
        assertPath("/api/backups")
    }

    @Test
    fun backupApi_downloadBackup_path() {
        server.enqueue(200, """{}""")
        BackupApi(core).downloadBackup("1")
        assertPath("/api/backups/1")
    }

    @Test
    fun backupApi_deleteBackup_path() {
        server.enqueue(200, """{}""")
        BackupApi(core).deleteBackup("1")
        assertPath("/api/backups/1")
    }

    // ============ AiApi（代理，纯前缀替换，✅ 已正确） ============

    @Test
    fun aiApi_tagGenerate_path() {
        server.enqueue(200, """{"tags":[]}""")
        AiApi(core).tagGenerate("bio", emptyList())
        assertPath("/api/proxy/ai/tasks/tag_generate")
    }

    @Test
    fun aiApi_contactOcr_path() {
        server.enqueue(200, """{"name":null,"other":[]}""")
        AiApi(core).contactOcr(text = "hi")
        assertPath("/api/proxy/ai/tasks/contact_ocr")
    }

    // ============ ShortLinkApi（代理，纯前缀替换，✅ 已正确） ============

    @Test
    fun shortLinkApi_list_path() {
        server.enqueue(200, """{}""")
        ShortLinkApi(core).shortioList()
        assertPath("/api/proxy/shortio/links")
    }

    @Test
    fun shortLinkApi_update_path() {
        server.enqueue(200, """{}""")
        ShortLinkApi(core).shortioUpdate("id", "https://example.com")
        assertPath("/api/proxy/shortio/links/id")
    }

    @Test
    fun shortLinkApi_domains_path() {
        server.enqueue(200, """{}""")
        ShortLinkApi(core).shortioDomains()
        assertPath("/api/proxy/shortio/domains")
    }

    @Test
    fun shortLinkApi_create_path() {
        server.enqueue(200, """{}""")
        ShortLinkApi(core).shortioCreate("https://example.com")
        assertPath("/api/proxy/shortio/links")
    }

    // ============ V2DomainApi（Phase 3 将精修为 /api/user/tags|collections） ============

    @Test
    fun v2DomainApi_createTag_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).createTag("n", "c", "p")
        assertPath("/api/tags")
    }

    @Test
    fun v2DomainApi_patchTag_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).patchTag(1L, name = "n")
        assertPath("/api/tags/1")
    }

    @Test
    fun v2DomainApi_deleteTag_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).deleteTag(1L)
        assertPath("/api/tags/1")
    }

    @Test
    fun v2DomainApi_createCollection_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).createCollection("n")
        assertPath("/api/collections")
    }

    @Test
    fun v2DomainApi_patchCollection_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).patchCollection(1L, name = "n")
        assertPath("/api/collections/1")
    }

    @Test
    fun v2DomainApi_deleteCollection_path() {
        server.enqueue(200, """{}""")
        V2DomainApi(core).deleteCollection(1L)
        assertPath("/api/collections/1")
    }
}
