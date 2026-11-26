package top.mcxiafeng.badger.pages.person.contact

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.ContactField
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import java.util.concurrent.atomic.AtomicInteger

/**
 * 编辑器只负责文本三件套（姓名/备注/介绍）+ 新建时的目标名片夹。
 * 头像/字段/平台/标签由详情页直接编辑，编辑器保存不得触碰。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CreateContactPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var vm: CreateContactViewModel
    private lateinit var contacts: ContactRepositoryImpl
    private lateinit var fields: FieldRepositoryImpl
    private lateinit var tags: TagRepositoryImpl
    private lateinit var collections: CollectionRepositoryImpl
    private lateinit var store: AccountStore
    private val owner = AccountScope.local("create-contact-test")
    private val active = MutableStateFlow(owner)
    private val viewModels = mutableListOf<CreateContactViewModel>()

    @Before
    fun setup() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        contacts = ContactRepositoryImpl(db.contactDao(), db.contactFieldDao(), db.customFieldDao(),
            db.contactFieldValueDao(), db.scanResultDao(), db.contactPlatformDao(), db.contactFtsDao())
        fields = FieldRepositoryImpl(db.contactFieldDao(), db.customFieldDao(), db.contactFieldValueDao(), db.contactPlatformDao(), db)
        tags = TagRepositoryImpl(db.tagDao(), db.contactTagDao(), db.tagFtsDao(), db.contactDao(), db)
        collections = CollectionRepositoryImpl(db.cardCollectionDao(), db.scanResultDao(), db.contactDao())
        // 系统预置字段（详情页基础信息/联系方式编辑依赖这些定义）。
        listOf("phone", "email", "gender", "birthday", "country", "region").forEach {
            db.contactFieldDao().insertField(ContactField(fieldName = it, fieldKey = it, isSystem = true))
        }
        db.cardCollectionDao().insertCollection(CardCollection(id = 1, name = "已有名片夹"))
        db.cardCollectionDao().insertCollection(CardCollection(id = 2, name = "其他名片夹"))
        store = mockk {
            every { current() } answers { active.value }
            every { scopeFlow } returns active
        }
        vm = makeViewModel()
    }

    private fun makeViewModel(handle: SavedStateHandle = SavedStateHandle(), folders: CollectionRepository = collections,
        scope: AccountScope = owner): CreateContactViewModel = CreateContactViewModel(
        contacts, folders, db, handle, scope, store,
    ).also { viewModels += it }

    @After
    fun teardown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun nicknameAloneRemainsUnfiledWithoutCreatingDefaultFolder() = runBlocking<Unit> {
        val id = vm.persist(ContactDraft(name = "小猫"))
        assertThat(contacts.getContactById(id)?.name).isEqualTo("小猫")
        assertThat(collections.getContactCollectionIds(id).first()).isEmpty()
        assertThat(collections.getAllCollectionsOnce()).hasSize(2)
    }

    @Test
    fun allDraftEditsRemainLocalUntilFinalTransaction() = runBlocking<Unit> {
        vm.updateName("小猫")
        vm.updateNote("朋友")
        vm.updateBio("这是一段很长的个人介绍")
        assertThat(contacts.getAllContacts().first()).isEmpty()
        val id = vm.persist(vm.uiState.value.draft)
        assertThat(contacts.getContactById(id)?.name).isEqualTo("小猫")
        assertThat(contacts.getContactById(id)?.note).isEqualTo("朋友")
        assertThat(contacts.getContactById(id)?.bio).isEqualTo("这是一段很长的个人介绍")
        // 文本编辑器不写字段/平台/标签
        assertThat(fields.getFieldValuesByContactOnce(id)).isEmpty()
        assertThat(contacts.getContactPlatforms(id)).isEmpty()
        assertThat(tags.getTagsByContact(id)).isEmpty()
    }

    @Test
    fun invalidFolderRollsBackContactCreationAndKeepsDraft() = runBlocking<Unit> {
        vm.initializeTarget(MISSING_FOLDER)
        vm.updateName("小猫")
        expectFailure { vm.persist(vm.uiState.value.draft) }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(vm.uiState.value.draft.name).isEqualTo("小猫")
        assertThat(vm.uiState.value.draft.collectionIds).containsExactly(MISSING_FOLDER)
    }

    @Test
    fun navigatedFolderSurvivesRecreationAndIsNotDuplicated() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.initializeTarget(2L)
        assertThat(original.uiState.value.draft.collectionIds).containsExactly(2L)
        original.updateName("来自名片夹")
        val restored = makeViewModel(handle)
        restored.initializeTarget(2L)
        assertThat(restored.uiState.value.draft.collectionIds).containsExactly(2L)
        val id = restored.persist(restored.uiState.value.draft)
        assertThat(collections.getContactCollectionIds(id).first()).containsExactly(2L)
    }

    @Test
    fun duplicateSubmitAndRestoredSuccessCannotCreateAnotherContact() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.initializeTarget(1L)
        original.updateName("只保存一次")
        awaitReady(original)
        original.save()
        original.save()
        val saved = withTimeout(TEST_TIMEOUT_MS) { original.uiState.first { it.savedContactId != null } }
        assertThat(contacts.getAllContacts().first()).hasSize(1)
        val restored = makeViewModel(handle)
        restored.initializeTarget(1L)
        restored.save()
        assertThat(restored.uiState.value.savedContactId).isEqualTo(saved.savedContactId)
        assertThat(contacts.getAllContacts().first()).hasSize(1)
    }

    @Test
    fun failedSubmissionRetainsDraftAndCanRetry() = runBlocking<Unit> {
        // 第一次校验失败（名片夹查询返回 null），重试时恢复真实查询。
        val failures = AtomicInteger(1)
        val failingFolders = object : CollectionRepository by collections {
            override suspend fun getCollectionById(id: Long): CardCollection? =
                if (failures.getAndDecrement() > 0) null else collections.getCollectionById(id)
        }
        val failing = makeViewModel(folders = failingFolders)
        failing.initializeTarget(1L)
        failing.updateName("可重试")
        failing.updateBio("保留介绍")
        awaitReady(failing)
        failing.save()
        withTimeout(TEST_TIMEOUT_MS) { failing.uiState.first { !it.isSaving && it.error != null } }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(failing.uiState.value.draft.name).isEqualTo("可重试")
        assertThat(failing.uiState.value.draft.bio).isEqualTo("保留介绍")
        failing.save()
        withTimeout(TEST_TIMEOUT_MS) { failing.uiState.first { it.savedContactId != null } }
        assertThat(contacts.getAllContacts().first()).hasSize(1)
    }

    @Test
    fun accountSwitchDuringTransactionRollsBackAndBlocksOldDraft() = runBlocking<Unit> {
        val switchingFolders = object : CollectionRepository by collections {
            override suspend fun getCollectionById(id: Long): CardCollection? {
                active.value = AccountScope.local("different-account")
                return collections.getCollectionById(id)
            }
        }
        val switching = makeViewModel(folders = switchingFolders)
        switching.initializeTarget(1L)
        switching.updateName("旧账号草稿")
        expectFailure { switching.persist(switching.uiState.value.draft) }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(switching.canNavigate()).isFalse()
        active.value = owner
        switching.save()
        assertThat(switching.uiState.value.accountChanged).isTrue()
        assertThat(switching.uiState.value.draft.name).isEqualTo("旧账号草稿")
    }

    @Test
    fun draftSavedStateCannotRestoreIntoAnotherAccount() {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.updateName("旧账号资料")
        original.initializeTarget(1L)
        val other = AccountScope.local("new-account")
        active.value = other
        val restored = makeViewModel(handle, scope = other)
        assertThat(restored.uiState.value.draft.hasChanges).isFalse()
        assertThat(restored.uiState.value.savedContactId).isNull()
    }

    @Test
    fun draftRestoreKeepsTextAndFoldersAndDoesNotSplitEmoji() {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.updateName("🐾".repeat(CONTACT_NAME_LIMIT + 1))
        original.updateNote("备注")
        original.updateBio("介绍")
        original.initializeTarget(1L)
        val restored = makeViewModel(handle)
        assertThat(restored.uiState.value.draft).isEqualTo(original.uiState.value.draft)
        val name = restored.uiState.value.draft.name
        assertThat(name.codePointCount(0, name.length)).isEqualTo(CONTACT_NAME_LIMIT)
    }

    @Test
    fun editModeLoadsTextAndUpdatesOnSave() = runBlocking<Unit> {
        vm.updateName("原名字")
        vm.updateBio("原介绍")
        val id = vm.persist(vm.uiState.value.draft)

        val handle = SavedStateHandle().apply { this[EDITING_ID_KEY] = id }
        val editVm = makeViewModel(handle)
        editVm.initializeEditing(id)
        withTimeout(TEST_TIMEOUT_MS) { editVm.uiState.first { it.editInitialized } }

        assertThat(editVm.uiState.value.draft.name).isEqualTo("原名字")
        assertThat(editVm.uiState.value.draft.bio).isEqualTo("原介绍")

        editVm.updateName("新名字")
        editVm.updateBio("新介绍")
        editVm.save()
        val saved = withTimeout(TEST_TIMEOUT_MS) { editVm.uiState.first { it.savedContactId != null } }
        assertThat(saved.savedContactId).isEqualTo(id)
        assertThat(contacts.getContactById(id)?.name).isEqualTo("新名字")
        assertThat(contacts.getContactById(id)?.bio).isEqualTo("新介绍")
        assertThat(contacts.getAllContacts().first()).hasSize(1)
    }

    @Test
    fun editModeSaveLeavesPlatformsFieldsTagsAndCollectionsUntouched() = runBlocking<Unit> {
        // 预置详情页直接编辑的数据：平台 + 字段 + 标签 + 名片夹。
        vm.updateName("联系人")
        val id = vm.persist(vm.uiState.value.draft)
        contacts.updateContactPlatform(id, "wechat",
            PlatformEntry(value = "old_id", displayName = "旧昵称", jumpLink = ""))
        fields.updateFieldValueByKey(id, "phone", "12345")
        val tagId = tags.upsertTag("旧标签")
        tags.addTagToContact(id, tagId)
        collections.addContactToCollection(id, 1L, sourceType = "manual")

        val handle = SavedStateHandle().apply { this[EDITING_ID_KEY] = id }
        val editVm = makeViewModel(handle)
        editVm.initializeEditing(id)
        withTimeout(TEST_TIMEOUT_MS) { editVm.uiState.first { it.editInitialized } }

        editVm.updateName("新名字")
        editVm.save()
        withTimeout(TEST_TIMEOUT_MS) { editVm.uiState.first { it.savedContactId != null } }

        // 编辑器只改文本，其余数据原样保留。
        assertThat(contacts.getContactPlatforms(id).single().value).isEqualTo("old_id")
        assertThat(fields.getFieldValueByContactAndKey(id, "phone")).isEqualTo("12345")
        assertThat(tags.getTagsByContact(id).map { it.name }).containsExactly("旧标签")
        assertThat(collections.getContactCollectionIds(id).first()).containsExactly(1L)
    }

    private suspend fun awaitReady(model: CreateContactViewModel) {
        withTimeout(TEST_TIMEOUT_MS) { model.uiState.first { !it.isLoading } }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var failure: Exception? = null
        try { block() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.d("CreateContactPersistenceTestTester", "观察到预期事务失败", error)
            failure = error
        }
        assertThat(failure).isNotNull()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 5_000L
        const val MISSING_FOLDER = 999L
        // 与 CreateContactViewModel.EDITING_ID_KEY 保持一致（private，测试直接引用字面量）。
        const val EDITING_ID_KEY = "contact_draft_editing_id"
    }
}
