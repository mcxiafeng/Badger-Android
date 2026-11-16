package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.ContactPlatform
import top.mcxiafeng.badger.data.ContactWithFields
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h1000dp-mdpi")
class ContactDetailInteractionTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var viewModel: ContactDetailViewModel
    private val pickerOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Before
    fun setUp() {
        val pickerFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = checkNotNull(modelClass.cast(
                when (modelClass) {
                    CountryPickerViewModel::class.java -> CountryPickerViewModel(mockk(relaxed = true))
                    RegionPickerViewModel::class.java -> RegionPickerViewModel(mockk(relaxed = true))
                    else -> error("Unexpected picker: $modelClass")
                },
            ))
        }
        val pickers = ViewModelProvider(pickerOwner, pickerFactory)
        pickers[CountryPickerViewModel::class.java]
        pickers[RegionPickerViewModel::class.java]
        val repository = mockk<ContactRepository>(relaxed = true)
        val collections = mockk<CollectionRepository>(relaxed = true)
        val tags = mockk<TagRepository>(relaxed = true)
        coEvery { repository.getContactWithFieldsById(CONTACT_ID) } returns ContactWithFields(
            contact = Contact(id = CONTACT_ID, name = "测试联系人"),
            fieldValues = listOf(field("phone", "电话", PHONE), field(null, "公司", "测试公司")),
        )
        coEvery { repository.getContactPlatforms(CONTACT_ID) } returns listOf(
            ContactPlatform(contactId = CONTACT_ID, platformKey = "website", jumpLink = WEBSITE),
        )
        every { collections.getContactCollectionIds(CONTACT_ID) } returns flowOf(emptyList())
        every { tags.observeTagsByContact(CONTACT_ID) } returns flowOf(emptyList())
        viewModel = ContactDetailViewModel(repository, collections, mockk(relaxed = true), tags,
            mockk(relaxed = true), UserProfileTicker())
    }

    @Test
    fun tappingPhoneOpensDialerWithoutShowingToolbarOrDialog() {
        mount()
        composeRule.onNodeWithText("电话").performScrollTo().performClick()
        composeRule.waitForIdle()

        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertThat(intent?.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(intent?.dataString).isEqualTo("tel:$PHONE")
        assertNoToolbarOrDialog()
    }

    @Test
    fun tappingPlatformOpensItsLinkWithoutShowingToolbarOrDialog() {
        mount()
        composeRule.onNodeWithText("网站").performScrollTo().performClick()
        composeRule.waitForIdle()

        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertThat(intent?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent?.dataString).isEqualTo(WEBSITE)
        assertNoToolbarOrDialog()
    }

    @Test
    fun holdingPhoneShowsToolbarWithoutLaunchingDialer() {
        mount()
        composeRule.onNodeWithText("电话").performScrollTo().performTouchInput { longClick() }
        assertToolbarWithoutLaunch()
    }

    @Test
    fun holdingPlatformShowsToolbarWithoutOpeningLink() {
        mount()
        composeRule.onNodeWithText("网站").performScrollTo().performTouchInput { longClick() }
        assertToolbarWithoutLaunch()
    }

    @Test
    fun nonLaunchableFieldOnlyShowsToolbarAfterLongPress() {
        mount()
        val company = composeRule.onNodeWithText("公司").performScrollTo()
        company.performClick()
        composeRule.waitForIdle()
        assertThat(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity).isNull()
        assertNoToolbarOrDialog()

        company.performTouchInput { longClick() }
        assertToolbarWithoutLaunch()
    }

    private fun mount() {
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides pickerOwner) {
                MiuixTheme { ContactDetailPage(contactId = CONTACT_ID, onBack = {}, viewModel = viewModel) }
            }
        }
        composeRule.waitUntil { !viewModel.isLoading.value }
    }

    private fun assertNoToolbarOrDialog() {
        composeRule.onNodeWithText("复制").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertDoesNotExist()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private fun assertToolbarWithoutLaunch() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        assertThat(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity).isNull()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private fun field(key: String?, name: String, value: String) = ContactFieldDisplay(
        valueId = if (key == null) 2L else 1L,
        fieldId = if (key == null) null else 1L,
        customFieldId = if (key == null) 1L else null,
        fieldName = name, fieldKey = key, icon = null, fieldType = "text", value = value, sortOrder = 0,
    )

    private companion object {
        const val CONTACT_ID = 42L
        const val PHONE = "13800138000"
        const val WEBSITE = "https://example.com/contact"
    }
}
