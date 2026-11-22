package top.mcxiafeng.badger.pages.card

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.sync.RemoteAssetReference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w400dp-h900dp-mdpi")
class CollectionBackgroundDraftTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val directory = TemporaryFolder()

    @OptIn(DelicateCoilApi::class)
    @After fun clearImageLoader() { SingletonImageLoader.reset() }

    @Test fun cancellingRemovalPreservesRemoteReference() {
        val file = RemoteAssetReference(UUID.randomUUID().toString(), "a".repeat(64), 100)
            .write(File(directory.root, "background${RemoteAssetReference.SUFFIX}"))
        removeThenCancel(file)
    }

    @Test fun cancellingRemovalPreservesLocalOriginal() {
        val file = directory.newFile("background.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        removeThenCancel(file)
    }

    private fun removeThenCancel(file: File) {
        val original = file.readBytes().toList()
        var saved: CardCollection? = null
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            MiuixTheme {
                if (shown) EditCollectionDialog(CardCollection(id = 1, name = "测试名片夹", backgroundImagePath = file.path),
                    onDismiss = { shown = false }, onConfirm = { saved = it; shown = false })
            }
        }
        compose.onNodeWithText("移除背景").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertThat(saved).isNull()
        assertThat(file.isFile).isTrue()
        assertThat(file.readBytes().toList()).isEqualTo(original)
    }
}
