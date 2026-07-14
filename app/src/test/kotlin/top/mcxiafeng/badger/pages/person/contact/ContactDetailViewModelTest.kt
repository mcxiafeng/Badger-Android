package top.mcxiafeng.badger.pages.person.contact

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactFieldValue
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.testutil.MainDispatcherRule
import top.mcxiafeng.badger.utils.PinyinUtils

class ContactDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: ContactRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var fieldRepository: FieldRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var aiTagGenerator: AiTagGenerator
    private lateinit var viewModel: ContactDetailViewModel

    private val testContact = Contact(
        id = 1L,
        name = "测试用户",
        avatarPath = null,
        avatarUrl = null,
        note = "测试备注",
        createTime = 1000L,
        updateTime = 2000L
    )

    private val testFieldDisplay = ContactFieldDisplay(
        valueId = 100L,
        fieldId = 1L,
        customFieldId = null,
        fieldName = "手机",
        fieldKey = "phone",
        icon = null,
        fieldType = null,
        value = "13800138000",
        sortOrder = 0
    )

    private val testFieldValue = ContactFieldValue(
        id = 100L,
        contactId = 1L,
        fieldId = 1L,
        customFieldId = null,
        value = "13800138000",
        createTime = 1000L,
        updateTime = 2000L
    )

    private val testContactWithFields = ContactWithFields(
        contact = testContact,
        fieldValues = listOf(testFieldDisplay)
    )

    private val testPlatform = ContactPlatform(
        contactId = 1L,
        platformKey = "QQ",
        value = "123456",
        displayName = "测试QQ",
        jumpLink = "https://qq.com/123456",
        originalLink = null,
        avatarUrl = null
    )

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        collectionRepository = mockk(relaxed = true)
        fieldRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        aiTagGenerator = mockk(relaxed = true)
        viewModel = ContactDetailViewModel(
            repository = repository,
            collectionRepository = collectionRepository,
            fieldRepository = fieldRepository,
            tagRepository = tagRepository,
            aiTagGenerator = aiTagGenerator,
            userProfileTicker = UserProfileTicker(),
        )
    }

    // ========== loadContact ==========

    @Test
    fun loadContact_success_setsState() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns listOf(testPlatform)

        viewModel.loadContact(1L)
        advanceUntilIdle()

        assertThat(viewModel.isLoading.value).isFalse()
        assertThat(viewModel.contactWithFields.value?.contact?.name).isEqualTo("测试用户")
        assertThat(viewModel.contactWithFields.value?.contact?.id).isEqualTo(1L)
        assertThat(viewModel.platformData.value).hasSize(1)
        assertThat(viewModel.platformData.value[0].platformKey).isEqualTo("QQ")
    }

    @Test
    fun loadContact_nullContact_setsNullState() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns null
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()

        viewModel.loadContact(1L)
        advanceUntilIdle()

        assertThat(viewModel.isLoading.value).isFalse()
        assertThat(viewModel.contactWithFields.value).isNull()
        assertThat(viewModel.platformData.value).isEmpty()
    }

    @Test
    fun loadContact_setsLoadingDuringFetch() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()

        assertThat(viewModel.isLoading.value).isTrue() // initial state
        viewModel.loadContact(1L)
        advanceUntilIdle()

        assertThat(viewModel.isLoading.value).isFalse()
    }

    // ========== updateName ==========

    @Test
    fun updateName_updatesContactViaRepository() = runTest {
        coEvery { repository.getContactById(1L) } returns testContact
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()

        viewModel.loadContact(1L)
        advanceUntilIdle()

        viewModel.updateName(1L, "新名字")
        advanceUntilIdle()

        coVerify { repository.updateContact(match { it.name == "新名字" && it.id == 1L }) }
        assertThat(viewModel.contactWithFields.value?.contact?.name).isEqualTo("新名字")
    }

    @Test
    fun updateName_nullContact_doesNotUpdate() = runTest {
        coEvery { repository.getContactById(1L) } returns null

        viewModel.updateName(1L, "新名字")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateContact(any()) }
    }

    // ========== applyAvatarUpdate ==========

    @Test
    fun applyAvatarUpdate_updatesContactAndState() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()

        viewModel.loadContact(1L)
        advanceUntilIdle()

        viewModel.applyAvatarUpdate(1L, "/path/to/avatar.webp")
        advanceUntilIdle()

        coVerify { repository.updateContact(match { it.avatarPath == "/path/to/avatar.webp" }) }
        assertThat(viewModel.contactWithFields.value?.contact?.avatarPath).isEqualTo("/path/to/avatar.webp")
    }

    @Test
    fun applyAvatarUpdate_nullContact_doesNotUpdate() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns null

        viewModel.applyAvatarUpdate(1L, "/path/to/avatar.webp")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateContact(any()) }
    }

    // ========== deleteFieldValue ==========

    @Test
    fun deleteFieldValue_findsAndDeletes() = runTest {
        coEvery { fieldRepository.getFieldValuesByContactOnce(1L) } returns listOf(testFieldValue)

        viewModel.deleteFieldValue(1L, 100L)
        advanceUntilIdle()

        coVerify { fieldRepository.deleteFieldValue(testFieldValue) }
    }

    @Test
    fun deleteFieldValue_notFound_doesNotDelete() = runTest {
        coEvery { fieldRepository.getFieldValuesByContactOnce(1L) } returns listOf(testFieldValue)

        viewModel.deleteFieldValue(1L, 999L)
        advanceUntilIdle()

        coVerify(exactly = 0) { fieldRepository.deleteFieldValue(any()) }
    }

    // ========== updateFieldValue ==========

    @Test
    fun updateFieldValue_findsAndUpdates() = runTest {
        coEvery { fieldRepository.getFieldValuesByContactOnce(1L) } returns listOf(testFieldValue)

        viewModel.updateFieldValue(1L, 100L, "13900139000")
        advanceUntilIdle()

        coVerify { fieldRepository.updateFieldValue(match { it.value == "13900139000" }) }
    }

    @Test
    fun updateFieldValue_notFound_doesNotUpdate() = runTest {
        coEvery { fieldRepository.getFieldValuesByContactOnce(1L) } returns listOf(testFieldValue)

        viewModel.updateFieldValue(1L, 999L, "13900139000")
        advanceUntilIdle()

        coVerify(exactly = 0) { fieldRepository.updateFieldValue(any()) }
    }

    // ========== removePlatform ==========

    @Test
    fun removePlatform_callsRepository() = runTest {
        viewModel.removePlatform(1L, "QQ")
        advanceUntilIdle()

        coVerify { repository.removeContactPlatform(1L, "QQ") }
    }

    // ========== addOrUpdatePlatform ==========

    @Test
    fun addOrUpdatePlatform_callsRepository() = runTest {
        val entry = PlatformEntry(value = "123", jumpLink = "https://qq.com/123")

        viewModel.addOrUpdatePlatform(1L, "QQ", entry)
        advanceUntilIdle()

        coVerify { repository.updateContactPlatform(1L, "QQ", entry) }
    }

    // ========== updateCollections ==========

    @Test
    fun updateCollections_addsAndRemoves() = runTest {
        viewModel.updateCollections(contactId = 1L, addedIds = listOf(10L, 20L), removedIds = listOf(30L))
        advanceUntilIdle()

        coVerify { collectionRepository.addContactToCollection(1L, 10L, "manual") }
        coVerify { collectionRepository.addContactToCollection(1L, 20L, "manual") }
        coVerify { collectionRepository.removeContactFromCollection(1L, 30L) }
    }

    @Test
    fun updateCollections_emptyLists_noRepositoryCalls() = runTest {
        viewModel.updateCollections(contactId = 1L, addedIds = emptyList(), removedIds = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { collectionRepository.addContactToCollection(any(), any(), any()) }
        coVerify(exactly = 0) { collectionRepository.removeContactFromCollection(any(), any()) }
    }

    // ========== reloadContact ==========

    @Test
    fun reloadContact_refreshesState() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns listOf(testPlatform)

        viewModel.reloadContact(1L)
        advanceUntilIdle()

        assertThat(viewModel.contactWithFields.value?.contact?.name).isEqualTo("测试用户")
        assertThat(viewModel.platformData.value).hasSize(1)
    }

    // ========== updateContact ==========

    @Test
    fun updateContact_callsRepositoryAndUpdatesState() = runTest {
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()

        viewModel.loadContact(1L)
        advanceUntilIdle()

        // [修复防御]: 改 name 后 ContactDetailViewModel.updateContact 会主动按 name 重算
        // pinyinInitial(PinyinUtils.getContactPinyinInitial("更新后") -> '#' 因为 '更' 是汉字
        // 但 ICU 解出拼音首字母可能因环境而异;这里直接传入期望值保证断言稳定)。
        // 期望的更新后实体由测试自己负责正确的 pinyinInitial,与 ViewModel 的归一化契约保持一致。
        val updated = testContact.copy(
            name = "更新后",
            pinyinInitial = PinyinUtils.getContactPinyinInitial("更新后"),
        )

        viewModel.updateContact(updated)
        advanceUntilIdle()

        coVerify { repository.updateContact(updated) }
        assertThat(viewModel.contactWithFields.value?.contact?.name).isEqualTo("更新后")
    }

    // ========== applySyncResult ==========

    @Test
    fun applySyncResult_withNameAndAvatar_updatesContact() = runTest {
        coEvery { repository.getContactById(1L) } returns testContact

        viewModel.applySyncResult(1L, "同步名字", "/path/sync_avatar.webp")
        advanceUntilIdle()

        coVerify {
            repository.updateContact(match {
                it.name == "同步名字" && it.avatarPath == "/path/sync_avatar.webp"
            })
        }
    }

    @Test
    fun applySyncResult_withOnlyName_updatesNameOnly() = runTest {
        coEvery { repository.getContactById(1L) } returns testContact

        viewModel.applySyncResult(1L, "同步名字", null)
        advanceUntilIdle()

        coVerify {
            repository.updateContact(match {
                it.name == "同步名字" && it.avatarPath == null
            })
        }
    }

    @Test
    fun applySyncResult_noChanges_doesNotUpdate() = runTest {
        coEvery { repository.getContactById(1L) } returns testContact

        viewModel.applySyncResult(1L, null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateContact(any()) }
    }

    @Test
    fun applySyncResult_nullContact_doesNotUpdate() = runTest {
        coEvery { repository.getContactById(1L) } returns null

        viewModel.applySyncResult(1L, "名字", "/path/avatar.webp")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateContact(any()) }
    }

    // ========== emitToast ==========

    @Test
    fun emitToast_emitsShowToastEvent() = runTest {
        viewModel.emitToast("操作成功")
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertThat(event).isInstanceOf(ContactDetailEvent.ShowToast::class.java)
        assertThat((event as ContactDetailEvent.ShowToast).message).isEqualTo("操作成功")
    }

    // ========== emitRefresh ==========

    @Test
    fun emitRefresh_emitsRefreshDataEvent() = runTest {
        viewModel.emitRefresh()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertThat(event).isEqualTo(ContactDetailEvent.RefreshData)
    }

    // ========== tags 自动跟随 tag 表变更刷新（修复"改色后详情页不更新"）==========

    /**
     * 修复说明：在 loadContact 内启动 tagRepository.observeTagsByContact 的 collect，
     * 让 tag 表任意字段（color / name / showDot）变更自动写入 viewModel.tags。
     * 本测试验证初次订阅后 _tags 等于 Room Flow 第一帧。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadContact_subscribesToObserveTagsByContact() = runTest {
        val tag1 = Tag(id = 10L, name = "同学", color = 0xFF1976D2L, showDot = true)
        val tag2 = Tag(id = 20L, name = "同事", color = 0xFF388E3CL, showDot = false)
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()
        coEvery { tagRepository.observeTagsByContact(1L) } returns flowOf(listOf(tag1, tag2))

        viewModel.loadContact(1L)
        advanceUntilIdle()

        coVerify { tagRepository.observeTagsByContact(1L) }
        assertThat(viewModel.tags.value).hasSize(2)
        assertThat(viewModel.tags.value.map { it.name }).containsExactly("同学", "同事").inOrder()
    }

    /**
     * 关键回归测试：Tag 颜色 / 名字 / showDot 变更通过 Room Flow 自动推送到 viewModel.tags。
     *
     * 模拟场景：在 TagManagerDialog 内点"改色"→ setTagColor 写库 → Room 检测到 tags 表变更
     * → observeTagsByContact 的 JOIN 查询自动重发 → _tags.value 更新 → 详情页 chip 立刻刷新。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeTagsByContact_emitsNewTagState_whenTagTableChanges() = runTest {
        val original = Tag(id = 10L, name = "高中同学", color = 0xFF1976D2L, showDot = true)
        val updated = original.copy(color = 0xFFE64A19L) // 用户改了颜色
        val tagFlow = MutableSharedFlow<List<Tag>>(replay = 1)
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactPlatforms(1L) } returns emptyList()
        coEvery { tagRepository.observeTagsByContact(1L) } returns tagFlow

        // 初始:发射原 tag
        tagFlow.emit(listOf(original))
        viewModel.loadContact(1L)
        advanceUntilIdle()
        assertThat(viewModel.tags.value.first().color).isEqualTo(0xFF1976D2L)

        // 模拟用户在 TagManagerDialog 内改色 → Room 检测到 tags 表变更 → Flow 自动推新值
        tagFlow.emit(listOf(updated))
        advanceUntilIdle()

        // 关键断言:viewModel.tags 自动跟随新颜色
        assertThat(viewModel.tags.value).hasSize(1)
        assertThat(viewModel.tags.value.first().color).isEqualTo(0xFFE64A19L)
    }

    /**
     * loadContact 重复调用应该取消旧 collect 协程,避免泄漏 + 避免旧 contactId 的 tag
     * 推送到新 contactId 的 viewModel.tags(交叉污染)。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadContact_repeated_resubscribesToNewContactId() = runTest {
        val tagA = Tag(id = 10L, name = "A的tag", color = 0xFF1976D2L)
        val tagB = Tag(id = 20L, name = "B的tag", color = 0xFF388E3CL)
        coEvery { repository.getContactWithFieldsById(1L) } returns testContactWithFields
        coEvery { repository.getContactWithFieldsById(2L) } returns testContactWithFields.copy(contact = testContact.copy(id = 2L))
        coEvery { repository.getContactPlatforms(any()) } returns emptyList()
        coEvery { tagRepository.observeTagsByContact(1L) } returns flowOf(listOf(tagA))
        coEvery { tagRepository.observeTagsByContact(2L) } returns flowOf(listOf(tagB))

        viewModel.loadContact(1L)
        advanceUntilIdle()
        assertThat(viewModel.tags.value.map { it.name }).containsExactly("A的tag")

        viewModel.loadContact(2L)
        advanceUntilIdle()
        // 切换 contactId 后必须显示新联系人的 tag,而不是旧联系人的
        assertThat(viewModel.tags.value.map { it.name }).containsExactly("B的tag")
    }
}
