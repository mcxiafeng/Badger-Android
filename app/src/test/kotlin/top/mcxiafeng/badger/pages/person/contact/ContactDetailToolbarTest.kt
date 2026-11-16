package top.mcxiafeng.badger.pages.person.contact

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-mdpi")
class ContactDetailToolbarTest {
    @get:Rule val composeRule = createComposeRule()
    private var copyCount = 0
    private var dismissCount = 0
    private val launchAttempts = mutableListOf<Intent>()

    @Test
    fun phoneCanOpenDialerFromTheToolbarWithoutADialog() {
        mount(field = field("phone", "电话", "13800138000"))

        composeRule.onNodeWithText("复制并打开").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(intent.dataString).isEqualTo("tel:13800138000")
        composeRule.runOnIdle { assertThat(dismissCount).isEqualTo(1) }
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun websiteCanOpenFromTheToolbarWithoutADialog() {
        mount(platform = "website" to PlatformEntry(jumpLink = WEBSITE))

        composeRule.onNodeWithText("跳转").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.dataString).isEqualTo(WEBSITE)
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun wechatQrStillOffersScanAndExistingManagementActions() {
        mount(platform = "wechat" to PlatformEntry(jumpLink = "https://u.wechat.com/test"))

        composeRule.onNodeWithText("扫码添加").assertIsDisplayed()
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun customFieldKeepsCopyAndEditWithoutAnEmptyLaunchAction() {
        mount(field = field(null, "公司", "测试公司"))

        composeRule.onNodeWithText("复制").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onNodeWithText("跳转").assertDoesNotExist()
        composeRule.onNodeWithText("复制并打开").assertDoesNotExist()
        composeRule.onNodeWithText("扫码添加").assertDoesNotExist()
        composeRule.runOnIdle { assertThat(copyCount).isEqualTo(1) }
    }

    @Test
    fun failedPlatformLaunchKeepsCopyAvailableAfterAllFallbacks() {
        mount(platform = "qq" to PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            failLaunches = true)

        composeRule.onNodeWithText("跳转").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertThat(launchAttempts).hasSize(3)
            assertThat(dismissCount).isEqualTo(0)
        }
        composeRule.onNodeWithText("复制").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertThat(copyCount).isEqualTo(1) }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w320dp-h800dp-mdpi")
    fun actionsRemainReachableOnNarrowScreensWithDoubleFontScale() {
        mount(platform = "qq" to PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            fontScale = 2f)

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("复制", "编辑", "同步信息", "删除", "跳转").forEach { label ->
            val bounds = composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertThat(bounds.left).isAtLeast(root.left)
            assertThat(bounds.right).isAtMost(root.right)
            assertThat(bounds.bottom).isAtMost(root.bottom)
        }
    }

    private fun mount(
        field: ContactFieldDisplay? = null,
        platform: Pair<String, PlatformEntry>? = null,
        failLaunches: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val context = LocalContext.current
            val actionContext = remember(context, failLaunches) {
                if (failLaunches) object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) {
                        launchAttempts += intent
                        throw ActivityNotFoundException("No application installed in this test")
                    }
                } else context
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides actionContext,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                MiuixTheme {
                    Scaffold(floatingToolbar = {
                        ContactDetailFloatingToolbars(
                            showFieldToolbar = field != null,
                            selectedField = field,
                            onFieldCopy = { copyCount++ },
                            onFieldEdit = {}, onFieldSync = {}, onFieldDelete = {},
                            showPlatformToolbar = platform != null,
                            selectedPlatform = platform,
                            onPlatformCopy = { copyCount++ },
                            onPlatformEdit = {}, onPlatformSync = {}, onPlatformDelete = {},
                            onDismissActions = { dismissCount++ },
                        )
                    }) {}
                }
            }
        }
    }

    private fun field(key: String?, name: String, value: String) = ContactFieldDisplay(
        valueId = 1L,
        fieldId = if (key == null) null else 1L,
        customFieldId = if (key == null) 1L else null,
        fieldKey = key,
        fieldName = name,
        icon = null,
        fieldType = "text",
        value = value,
        sortOrder = 0,
    )

    private companion object {
        const val WEBSITE = "https://example.com/contact"
    }
}
