package top.mcxiafeng.badger.pages.person.contact

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    private var syncCount = 0
    private var dismissCount = 0

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
    fun customFieldKeepsCopyAndDeleteWithoutAnEmptyLaunchAction() {
        mount(field = field(null, "公司", "测试公司"))

        composeRule.onNodeWithText("复制").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("编辑").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onNodeWithText("跳转").assertDoesNotExist()
        composeRule.onNodeWithText("复制并打开").assertDoesNotExist()
        composeRule.onNodeWithText("扫码添加").assertDoesNotExist()
        composeRule.runOnIdle { assertThat(copyCount).isEqualTo(1) }
    }

    @Test
    fun platformPopupShowsCopyEditSyncDeleteWhenSyncAvailable() {
        mountPlatform(
            fieldKey = "qq",
            entry = PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            displayName = "QQ",
            summary = "123456",
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("同步信息").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun platformPopupHidesSyncWhenSyncNotAvailable() {
        mountPlatform(
            fieldKey = "wechat",
            entry = PlatformEntry(value = "wxid_test", jumpLink = "https://u.wechat.com/test"),
            displayName = "微信",
            summary = "wxid_test",
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
        composeRule.onNodeWithText("同步信息").assertDoesNotExist()
    }

    @Test
    fun platformPopupCopyInvokesCallback() {
        mountPlatform(
            fieldKey = "qq",
            entry = PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            displayName = "QQ",
            summary = "123456",
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("复制").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertThat(copyCount).isEqualTo(1) }
    }

    @Test
    fun platformPopupSyncInvokesCallbackDirectly() {
        // 去掉二次确认框后，点"同步信息"应直接触发同步回调，不再弹出同步设置弹窗
        mountPlatform(
            fieldKey = "qq",
            entry = PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            displayName = "QQ",
            summary = "123456",
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("同步信息").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertThat(syncCount).isEqualTo(1) }
        // 不应出现同步设置弹窗
        composeRule.onNodeWithText("同步设置").assertDoesNotExist()
        composeRule.onNodeWithText("同步昵称").assertDoesNotExist()
    }

    @Test
    fun platformPopupDoesNotShowWhenNotSelected() {
        mountPlatform(
            fieldKey = "qq",
            entry = PlatformEntry(value = "123456", jumpLink = "https://example.com/qq"),
            displayName = "QQ",
            summary = "123456",
            selected = false,
        )

        composeRule.waitForIdle()
        // 行本身仍在，但未选中时不挂载弹窗内容
        composeRule.onNodeWithText("QQ").assertIsDisplayed()
        composeRule.onNodeWithText("复制").assertDoesNotExist()
    }

    private fun mount(field: ContactFieldDisplay? = null) {
        composeRule.setContent {
            MiuixTheme {
                Scaffold(floatingToolbar = {
                    ContactDetailFloatingToolbars(
                        showFieldToolbar = field != null,
                        selectedField = field,
                        onFieldCopy = { copyCount++ },
                        onFieldSync = {}, onFieldDelete = {},
                        onDismissActions = { dismissCount++ },
                    )
                }) {}
            }
        }
    }

    private fun mountPlatform(
        fieldKey: String,
        entry: PlatformEntry,
        displayName: String,
        summary: String,
        selected: Boolean = true,
    ) {
        composeRule.setContent {
            MiuixTheme {
                Scaffold {
                    PlatformInfoRow(
                        fieldKey = fieldKey,
                        entry = entry,
                        displayName = displayName,
                        summary = summary,
                        selectedPlatformKey = if (selected) fieldKey else null,
                        onClick = {},
                        onLongClick = {},
                        onDismissRequest = { dismissCount++ },
                        onCopy = { copyCount++ },
                        onEdit = {},
                        onSync = { syncCount++ },
                        onDelete = {},
                    )
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
}
