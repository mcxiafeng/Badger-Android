package top.mcxiafeng.badger.pages.todo

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.ui.glass.RetainedPopupHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TodoDatePickerDialogTest {
    @get:Rule val compose = createComposeRule()
    private var confirmed: String? = null

    @Test fun wheelDialogKeepsTheStoredDateAndConfirmsWithoutChangingIt() {
        mount(current = "2026-12-01")
        compose.onNodeWithText("选择日期").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        assertEquals("2026-12-01", confirmed)
    }

    @Test fun invalidStoredDateFallsBackToTodayInsteadOfCrashing() {
        mount(current = "not-a-date")
        compose.onNodeWithText("选择日期").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        assertTrue(confirmed != null && Regex("\\d{4}-\\d{2}-\\d{2}").matches(confirmed!!))
    }

    @Test fun cancelLeavesTheStoredDateUntouched() {
        mount(current = "2026-12-01")
        compose.onNodeWithText("取消").performClick()
        assertEquals(null, confirmed)
    }

    private fun mount(current: String) {
        compose.setContent {
            MiuixTheme {
                RetainedPopupHost {
                    TodoDatePickerDialog(show = true, current = current, onDismiss = {}, onConfirm = { confirmed = it })
                }
            }
        }
    }
}
