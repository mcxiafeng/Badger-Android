package top.mcxiafeng.badger.pages.person.contact

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
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
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountScope
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.account.AccountWriteLocks
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import top.mcxiafeng.badger.domain.ParsePlatformInputUseCase

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
        fields = FieldRepositoryImpl(db.contactFieldDao(), db.customFieldDao(), db.contactFieldValueDao(), db.contactPlatformDao())
        tags = TagRepositoryImpl(db.tagDao(), db.contactTagDao(), db.tagFtsDao(), db.contactDao(), db)
        collections = CollectionRepositoryImpl(db.cardCollectionDao(), db.scanResultDao(), db.contactDao())
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
        contacts, folders, fields, tags, db, AccountContext(RuntimeEnvironment.getApplication(), scope),
        ParsePlatformInputUseCase(), handle, scope, store,
    ).also { viewModels += it }

    @After
    fun teardown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun nicknameAloneRemainsUnfiledWithoutCreatingDefaultFolder() = runBlocking<Unit> {
        val id = vm.persist(ContactDraft(name = "小猫"), null)
        assertThat(contacts.getContactById(id)?.name).isEqualTo("小猫")
        assertThat(collections.getContactCollectionIds(id).first()).isEmpty()
        assertThat(collections.getAllCollectionsOnce()).hasSize(2)
    }

    @Test
    fun allDraftEditsRemainLocalUntilFinalTransaction() = runBlocking<Unit> {
        vm.updateName("小猫")
        vm.updateNote("朋友")
        vm.updateInfo("birthday", "2000-01-02")
        vm.updateInfo("country", "中国")
        vm.stageField("phone", null, "电话", "13800000000")
        vm.stageField("phone", null, "电话", "13900000000")
        vm.stageField("email", null, "邮箱", "test@example.invalid")
        vm.stageField(null, null, "工作地点", "图书馆")
        vm.setTags(setOf("朋友", "同学"))
        vm.setCollections(setOf(1L, 2L))
        assertThat(vm.stagePlatform("wechat", "example_id", "猫咪")).isTrue()
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(tags.getAllTagsOnce()).isEmpty()
        assertThat(db.customFieldDao().getAllEnabledCustomFields().first()).isEmpty()
        val id = vm.persist(vm.uiState.value.draft, null)
        assertThat(contacts.getContactById(id)?.note).isEqualTo("朋友")
        assertThat(fields.getFieldValueByContactAndKey(id, "birthday")).isEqualTo("2000-01-02")
        val phoneId = requireNotNull(fields.getFieldByKey("phone")).id
        assertThat(fields.getFieldValuesByContactOnce(id).filter { it.fieldId == phoneId }.map { it.value })
            .containsExactly("13800000000", "13900000000")
        assertThat(contacts.getContactPlatforms(id).single().value).isEqualTo("example_id")
        assertThat(tags.getTagsByContact(id).map { it.name }).containsExactly("朋友", "同学")
        assertThat(collections.getContactCollectionIds(id).first()).containsExactly(1L, 2L)
    }

    @Test
    fun invalidFolderRollsBackContactFieldsPlatformsTagsCustomDefinitionsAndAvatar() = runBlocking<Unit> {
        vm.updateName("小猫")
        vm.updateInfo("gender", "其他")
        vm.stageField(null, null, "新字段", "内容")
        vm.setTags(setOf("新标签"))
        vm.setCollections(setOf(1L, MISSING_FOLDER))
        vm.stagePlatform("qq", "12345", "")
        val context = RuntimeEnvironment.getApplication()
        val before = context.filesDir.listFiles().orEmpty().map(File::getName).toSet()
        val avatar = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        expectFailure { vm.persist(vm.uiState.value.draft, avatar) }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(tags.getAllTagsOnce()).isEmpty()
        assertThat(db.customFieldDao().getAllEnabledCustomFields().first()).isEmpty()
        assertThat(context.filesDir.listFiles().orEmpty().map(File::getName).toSet()).isEqualTo(before)
        assertThat(vm.uiState.value.draft.name).isEqualTo("小猫")
    }

    @Test
    fun navigatedFolderSurvivesRecreationAndUserSelectionIsNotOverwritten() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.initializeTarget(2L)
        assertThat(original.uiState.value.draft.collectionIds).containsExactly(2L)
        original.setCollections(setOf(1L))
        original.updateName("来自名片夹")
        val restored = makeViewModel(handle)
        restored.initializeTarget(2L)
        assertThat(restored.uiState.value.draft.collectionIds).containsExactly(1L)
        val id = restored.persist(restored.uiState.value.draft, null)
        assertThat(collections.getContactCollectionIds(id).first()).containsExactly(1L)
    }

    @Test
    fun platformSaveDoesNotAcquireRepositoryWriteMutexInsideRoomTransaction() = runBlocking<Unit> {
        vm.updateName("小猫")
        vm.stagePlatform("wechat", "example_id", "")
        val lock = AccountWriteLocks.forDao(db.contactDao())
        lock.lock()
        try {
            withTimeout(TEST_TIMEOUT_MS) {
                val id = vm.persist(vm.uiState.value.draft, null)
                assertThat(contacts.getContactPlatforms(id)).hasSize(1)
            }
        } finally { lock.unlock() }
    }

    @Test
    fun groupPlatformKeysAndAuxiliaryAccountPersistWithoutBecomingPersonalAccounts() = runBlocking<Unit> {
        vm.updateName("群联系人")
        assertThat(vm.stagePlatform("qqGroup", "12345", "群聊")).isTrue()
        assertThat(vm.stagePlatform("douyin", "https://example.invalid/profile", "昵称", "search_id")).isTrue()
        val id = vm.persist(vm.uiState.value.draft, null)
        val platforms = contacts.getContactPlatforms(id).associateBy { it.platformKey }
        assertThat(platforms.keys).containsExactly("qqGroup", "douyin")
        assertThat(platforms["douyin"]?.value).isEqualTo("search_id")
        assertThat(platforms["douyin"]?.originalLink).isEqualTo("https://example.invalid/profile")
    }

    @Test
    fun draftRestoreKeepsFieldsPlatformsTagsFoldersAndDoesNotSplitEmoji() {
        val handle = SavedStateHandle()
        val original = makeViewModel(handle)
        original.updateName("🐾".repeat(CONTACT_NAME_LIMIT + 1))
        original.updateNote("备注")
        original.updateInfo("region", "广东珠海")
        original.stageField("phone", null, "电话", "12345")
        original.stagePlatform("douyin", "https://example.invalid/profile", "昵称", "example_id")
        original.setTags(setOf("朋友"))
        original.setCollections(setOf(1L, 2L))
        val restored = makeViewModel(handle)
        assertThat(restored.uiState.value.draft).isEqualTo(original.uiState.value.draft)
        val name = restored.uiState.value.draft.name
        assertThat(name.codePointCount(0, name.length)).isEqualTo(CONTACT_NAME_LIMIT)
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
        val failingFolders = object : CollectionRepository by collections {
            override suspend fun getCollectionById(id: Long): CardCollection? = null
        }
        val failing = makeViewModel(folders = failingFolders)
        failing.initializeTarget(1L)
        failing.updateName("可重试")
        failing.setTags(setOf("保留标签"))
        awaitReady(failing)
        failing.save()
        withTimeout(TEST_TIMEOUT_MS) { failing.uiState.first { !it.isSaving && it.error != null } }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(failing.uiState.value.draft.tags).containsExactly("保留标签")
        failing.setCollections(emptySet())
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
        switching.updateName("旧账号草稿")
        switching.setTags(setOf("不应落库"))
        switching.setCollections(setOf(1L))
        expectFailure { switching.persist(switching.uiState.value.draft, null) }
        assertThat(contacts.getAllContacts().first()).isEmpty()
        assertThat(tags.getAllTagsOnce()).isEmpty()
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
        const val AVATAR_SIZE = 16
    }
}
