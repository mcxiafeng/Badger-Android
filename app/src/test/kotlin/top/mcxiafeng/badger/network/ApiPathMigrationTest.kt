package top.mcxiafeng.badger.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [Phase 1] 传输层路径迁移（v1 前缀 → api 前缀）回归锚点。
 *
 * [Phase 3] 适配：ContactApi → PersonApi（/api/user/persons），V2DomainApi 改
 * uuid 签名 + /api/user 路径。每个 API 方法发出的 HTTP path 逐一断言。
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

    // ============ PersonApi（/api/user/persons，ApiResult 壳） ============

    @Test
    fun personApi_createPerson_path() {
        server.enqueue(200, """{"code":200,"data":{"uuid":"p1"}}""")
        PersonApi(core).createPerson("n", null, "client-uuid")
        assertPath("/api/user/persons")
    }

    @Test
    fun personApi_getPerson_path() {
        server.enqueue(200, """{"code":200,"data":{"uuid":"p1","name":"n","self":false}}""")
        PersonApi(core).getPerson("p1")
        assertPath("/api/user/persons/p1")
    }

    @Test
    fun personApi_updatePerson_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        PersonApi(core).updatePerson("p1", name = "n", profile = null)
        assertPath("/api/user/persons/p1")
    }

    @Test
    fun personApi_deletePerson_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        PersonApi(core).deletePerson("p1")
        assertPath("/api/user/persons/p1")
    }

    @Test
    fun personApi_mergePersons_path() {
        server.enqueue(200, """{"code":200,"data":{"uuid":"t1"}}""")
        PersonApi(core).mergePersons("t1", listOf("m1"))
        assertPath("/api/user/persons/t1/merge")
    }

    @Test
    fun personApi_listPersons_path() {
        server.enqueue(200, """{"code":200,"data":[]}""")
        PersonApi(core).listPersons()
        assertPath("/api/user/persons")
    }

    // ============ SyncApi（/api/user/sync） ============

    @Test
    fun syncApi_syncSince_path() {
        server.enqueue(200, """{"code":200,"data":{"version":10,"changes":[],"hasMore":false}}""")
        SyncApi(core).syncSince(since = 0L)
        assertPath("/api/user/sync?since=0&limit=500")
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

    // ============ V2DomainApi（/api/user/tags|collections，uuid 签名） ============

    @Test
    fun v2DomainApi_createTag_path() {
        server.enqueue(200, """{"code":200,"data":{"uuid":"t1"}}""")
        V2DomainApi(core).createTag("n", colorHash = "0xFF0000", personMembers = null)
        assertPath("/api/user/tags")
    }

    @Test
    fun v2DomainApi_patchTag_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        V2DomainApi(core).patchTag("t1", name = "n", colorHash = null)
        assertPath("/api/user/tags/t1")
    }

    @Test
    fun v2DomainApi_deleteTag_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        V2DomainApi(core).deleteTag("t1")
        assertPath("/api/user/tags/t1")
    }

    @Test
    fun v2DomainApi_createCollection_path() {
        server.enqueue(200, """{"code":200,"data":{"uuid":"c1"}}""")
        V2DomainApi(core).createCollection("n", description = null, backgroundURL = null, personMembers = null)
        assertPath("/api/user/collections")
    }

    @Test
    fun v2DomainApi_patchCollection_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        V2DomainApi(core).patchCollection("c1", name = "n", description = null, backgroundURL = null)
        assertPath("/api/user/collections/c1")
    }

    @Test
    fun v2DomainApi_deleteCollection_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        V2DomainApi(core).deleteCollection("c1")
        assertPath("/api/user/collections/c1")
    }
}
