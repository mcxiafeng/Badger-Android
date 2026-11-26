package top.mcxiafeng.badger.pages.social

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.domain.PlatformProfile
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-mdpi")
class SocialCardInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<Int>()
    private var addNavigationCount = 0
    private val platforms = listOf(
        "qq" to PlatformEntry(value = "123", displayName = "QQ昵称"),
        "bilibili" to PlatformEntry(value = "456", displayName = "B站昵称")
    )

    @Test
    fun swipeUpdatesCardThroughPlatformsAndAddWithoutLeakingPreviousSignature() {
        mountScreen(platformCount = 2)
        assertCard("QQ昵称", "QQ签名")

        swipePage(left = true)
        assertCard("B站昵称", "B站签名")
        swipePage(left = true)
        assertCard("添加", null)
        swipePage(left = false)
        assertCard("B站昵称", "B站签名")
        swipePage(left = false)
        assertCard("QQ昵称", "QQ签名")
        composeRule.runOnIdle {
            assertThat(selections).doesNotContain(2)
            assertThat(selections).containsAtLeast(1, 0)
        }
    }

    @Test
    fun singlePlatformCanRestOnAddAndSwipeBackWithoutSnapLoop() {
        mountScreen(platformCount = 1)
        swipePage(left = true)
        assertCard("添加", null)
        composeRule.waitForIdle()
        assertCard("添加", null)
        swipePage(left = false)
        assertCard("QQ昵称", "QQ签名")
        composeRule.runOnIdle { assertThat(selections).isEmpty() }
    }

    @Test
    fun emptyPlatformListDisplaysOnlyAddCardAndCannotChangePlatform() {
        mountScreen(platformCount = 0)
        assertCard("添加", null)
        swipePage(left = true)
        swipePage(left = false)
        assertCard("添加", null)
        composeRule.runOnIdle { assertThat(selections).isEmpty() }
    }

    @Test
    fun addCardRequestsAddPlatformPage() {
        mountScreen(platformCount = 0)
        composeRule.onNodeWithTag("social_business_card").performClick()
        composeRule.runOnIdle { assertThat(addNavigationCount).isEqualTo(1) }
    }

    @Test
    fun titleAndSignatureStayAtBottomLeftWithAndWithoutBackgroundImage() {
        var cover by mutableStateOf<Bitmap?>(null)
        composeRule.setContent {
            MiuixTheme {
                Scaffold {
                    BlueBusinessCard(
                        cardBitmap = cover,
                        profileName = "QQ昵称",
                        profileBio = "QQ签名",
                        profileLoaded = true,
                        showNfcMenu = false,
                        linkUpdateState = LinkUpdateState.IDLE,
                        nfcSupported = false,
                        onCardClick = {},
                        onShowNfcMenu = {},
                        onDismissNfcMenu = {},
                        onPickImage = {}
                    )
                }
            }
        }
        assertBottomLeft()
        composeRule.runOnIdle { cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888) }
        assertBottomLeft()
    }

    private fun mountScreen(platformCount: Int) {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(SocialUiState(
                    profile = UserProfile(name = "全局名字", bio = "全局简介"),
                    platforms = platforms.take(platformCount),
                    platformProfiles = mapOf(
                        "qq" to PlatformProfile("QQ昵称", "QQ签名"),
                        "bilibili" to PlatformProfile("B站昵称", "B站签名")
                    )
                ))
            }
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    SocialScreen(
                        uiState = state,
                        onNavigateToAddPlatform = { addNavigationCount++ },
                        onSelectPlatform = { index ->
                            selections += index
                            state = state.copy(selectedPlatformIndex = index)
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipePage(left: Boolean) {
        val distance = with(composeRule.density) { 132.dp.toPx() }
        composeRule.onNodeWithTag("social_platform_pager").performTouchInput {
            swipe(center, center.copy(x = center.x + if (left) -distance else distance), 600L)
        }
        composeRule.waitForIdle()
    }

    private fun assertCard(name: String, signature: String?) {
        composeRule.onNodeWithTag("social_business_card_name", useUnmergedTree = true).assertTextEquals(name)
        val signatureNode = composeRule.onNodeWithTag("social_business_card_signature", useUnmergedTree = true)
        if (signature == null) signatureNode.assertDoesNotExist() else signatureNode.assertTextEquals(signature)
    }

    private fun assertBottomLeft() {
        composeRule.waitForIdle()
        val card = composeRule.onNodeWithTag("social_business_card").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithTag("social_business_card_name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val signature = composeRule.onNodeWithTag("social_business_card_signature", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val inset = with(composeRule.density) { 24.dp.toPx() }
        assertThat(title.left - card.left).isWithin(1f).of(inset)
        assertThat(card.bottom - signature.bottom).isWithin(1f).of(inset)
        assertThat(signature.left).isEqualTo(title.left)
        assertThat(signature.top).isAtLeast(title.bottom)
    }
}
