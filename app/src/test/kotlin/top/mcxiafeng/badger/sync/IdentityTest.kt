package top.mcxiafeng.badger.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

/**
 * [T07] RemoteIdentity 映射回归：serverId 两义字段必须正确映射到密封类型。
 */
class IdentityTest {

    private fun contact(serverId: String?, isLocalOnly: Boolean) = ContactCacheEntity(
        id = 1L,
        serverId = serverId,
        name = "张三",
        createTime = 1L,
        updateTime = 1L,
        isLocalOnly = isLocalOnly,
    )

    @Test
    fun `synced contact maps to Synced`() {
        assertThat(contact("srv-1", isLocalOnly = false).identity())
            .isEqualTo(RemoteIdentity.Synced("srv-1"))
    }

    @Test
    fun `localOnly contact with clientUuid maps to PendingCreate`() {
        assertThat(contact("client-uuid", isLocalOnly = true).identity())
            .isEqualTo(RemoteIdentity.PendingCreate("client-uuid"))
    }

    @Test
    fun `contact without serverId maps to Unidentified`() {
        assertThat(contact(null, isLocalOnly = true).identity())
            .isEqualTo(RemoteIdentity.Unidentified)
        assertThat(contact("", isLocalOnly = false).identity())
            .isEqualTo(RemoteIdentity.Unidentified)
    }

    @Test
    fun `tag and collection map identically`() {
        val tag = TagCacheEntity(id = 1L, serverId = "t-1", name = "朋友", createTime = 1L, isLocalOnly = false)
        assertThat(tag.identity()).isEqualTo(RemoteIdentity.Synced("t-1"))

        val pendingTag = TagCacheEntity(id = 2L, serverId = "c-2", name = "同事", createTime = 1L, isLocalOnly = true)
        assertThat(pendingTag.identity()).isEqualTo(RemoteIdentity.PendingCreate("c-2"))

        val collection = CardCollectionCacheEntity(id = 3L, serverId = "col-1", name = "工作", createTime = 1L, isLocalOnly = false)
        assertThat(collection.identity()).isEqualTo(RemoteIdentity.Synced("col-1"))

        val blankCollection = CardCollectionCacheEntity(id = 4L, serverId = null, name = "空", createTime = 1L, isLocalOnly = true)
        assertThat(blankCollection.identity()).isEqualTo(RemoteIdentity.Unidentified)
    }
}
