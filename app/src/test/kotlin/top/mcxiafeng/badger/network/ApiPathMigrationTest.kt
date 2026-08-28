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

    // ============ ResolverApi（Phase 4：/api/resolve/ + 尾斜杠 + 壳） ============

    @Test
    fun resolverApi_resolveIdentifyBatch_path() {
        server.enqueue(200, """{"code":200,"data":{"results":[{"platform":"qq","contacts":{}}]}}""")
        ResolverApi(core).resolveIdentifyBatch(listOf("12345"))
        assertPath("/api/resolve/")
    }

    @Test
    fun resolverApi_platforms_path() {
        server.enqueue(200, """{"code":200,"data":[]}""")
        ResolverApi(core).platforms()
        assertPath("/api/resolve/platforms")
    }

    @Test
    fun serverApi_platforms_path() {
        server.enqueue(200, """{"code":200,"data":[]}""")
        ServerApi(server.baseUrl, OkHttpClient(), { null }).platforms()
        assertPath("/api/resolve/platforms")
    }

    // ============ BackupApi（Phase 4：/api/user/backups + ApiResult 壳） ============

    @Test
    fun backupApi_listBackups_path() {
        server.enqueue(200, """{"code":200,"data":{"backups":[]}}""")
        BackupApi(core).listBackups()
        assertPath("/api/user/backups")
    }

    @Test
    fun backupApi_uploadBackup_path() {
        server.enqueue(200, """{"code":200,"data":{"id":"1","name":"n","size":1,"created_at":"t"}}""")
        BackupApi(core).uploadBackup("""{}""")
        assertPath("/api/user/backups")
    }

    @Test
    fun backupApi_downloadBackup_path() {
        server.enqueue(200, """{}""")
        BackupApi(core).downloadBackup("1")
        assertPath("/api/user/backups/1")
    }

    @Test
    fun backupApi_deleteBackup_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        BackupApi(core).deleteBackup("1")
        assertPath("/api/user/backups/1")
    }

    // ============ NotificationApi（B1：/api/user/notifications） ============

    @Test
    fun notificationApi_unreadCount_path() {
        server.enqueue(200, """{"code":200,"data":{"unread":0}}""")
        NotificationApi(core).getUnreadCount()
        assertPath("/api/user/notifications/unread-count")
    }

    @Test
    fun notificationApi_list_path() {
        server.enqueue(200, """{"code":200,"data":[]}""")
        NotificationApi(core).listNotifications()
        assertPath("/api/user/notifications")
    }

    @Test
    fun notificationApi_markAsRead_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        NotificationApi(core).markAsRead("n-1")
        assertPath("/api/user/notifications/n-1/read")
    }

    @Test
    fun notificationApi_delete_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        NotificationApi(core).delete("n-1")
        assertPath("/api/user/notifications/n-1")
    }

    // ============ DeviceApi（B3：/api/user/devices） ============

    @Test
    fun deviceApi_list_path() {
        server.enqueue(200, """{"code":200,"data":[]}""")
        DeviceApi(core).listDevices()
        assertPath("/api/user/devices")
    }

    @Test
    fun deviceApi_rename_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        DeviceApi(core).renameDevice("d-1", "new-name")
        assertPath("/api/user/devices/d-1")
    }

    @Test
    fun deviceApi_delete_path() {
        server.enqueue(200, """{"code":200,"data":null}""")
        DeviceApi(core).deleteDevice("d-1")
        assertPath("/api/user/devices/d-1")
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
