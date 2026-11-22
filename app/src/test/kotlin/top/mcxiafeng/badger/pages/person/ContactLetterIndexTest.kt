package top.mcxiafeng.badger.pages.person

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.LetterCount
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ContactLetterIndexTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var state: LazyListState
    private val contacts = buildList {
        add(Contact(id = 1, name = "100", pinyinInitial = "#"))
        add(Contact(id = 2, name = "101", pinyinInitial = "#"))
        listOf("A", "B", "Z").forEachIndexed { group, letter ->
            repeat(GROUP_SIZE) { item ->
                add(Contact(id = (group * GROUP_SIZE + item + 3).toLong(),
                    name = letter + item.toString().padStart(2, '0'), pinyinInitial = letter))
            }
        }
    }

    @Test
    fun targetsMatchRealContactPositionsIncludingProfileAndNumberGroup() {
        mount()
        select("定位到 Z 开头的联系人")
        composeRule.onNodeWithText("Z00").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(state.firstVisibleItemIndex).isEqualTo(FIXED_COUNT + 2 + GROUP_SIZE * 2) }
        select("定位到 # 开头的联系人")
        composeRule.onNodeWithText("100").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(state.firstVisibleItemIndex).isEqualTo(FIXED_COUNT) }
        select("定位到我的名片")
        composeRule.onNodeWithText("固定2").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(state.firstVisibleItemIndex).isEqualTo(PROFILE_INDEX) }
    }

    @Test
    fun draggingThroughUnavailableLettersEndsAtTheLatestAvailableGroup() {
        mount()
        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            swipe(Offset(center.x, 1f), Offset(center.x, height - 1f), durationMillis = 400)
        }
        composeRule.onNodeWithText("Z00").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(state.firstVisibleItemIndex).isEqualTo(FIXED_COUNT + 2 + GROUP_SIZE * 2) }
    }

    private fun select(label: String) {
        val actions = composeRule.onNodeWithContentDescription(DESCRIPTION)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { check(actions.single { it.label == label }.action()) }
    }

    private fun mount() {
        composeRule.setContent {
            MiuixTheme {
                state = rememberLazyListState()
                Row(Modifier.fillMaxWidth().height(400.dp)) {
                    LazyColumn(Modifier.weight(1f), state = state) {
                        items(FIXED_COUNT) { Text("固定$it", Modifier.height(48.dp)) }
                        items(contacts, key = { it.id }) { Text(it.name, Modifier.height(48.dp)) }
                    }
                    ContactLetterIndex(contacts.groupingBy { it.pinyinInitial }.eachCount().map { LetterCount(it.key, it.value) },
                        state, FIXED_COUNT, PROFILE_INDEX, Modifier.fillMaxHeight())
                }
            }
        }
    }

    private companion object {
        const val DESCRIPTION = "联系人字母索引"
        const val GROUP_SIZE = 20
        const val FIXED_COUNT = 3
        const val PROFILE_INDEX = 2
    }
}
