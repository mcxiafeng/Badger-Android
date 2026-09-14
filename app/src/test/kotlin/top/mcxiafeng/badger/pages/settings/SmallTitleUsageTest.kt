package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmallTitleUsageTest {

    private val settingsDir: File by lazy {
        
        val path = "../shared/src/commonMain/kotlin/top/mcxiafeng/badger/pages/settings"
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("app/$path"),
        )
        candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("无法定位 pages/settings 源码目录，尝试过：$candidates")
    }

    private val trackedFiles: List<File> by lazy {
        
        
        listOf(
            "SettingsPage.kt",
            "AboutPage.kt",
            "UiSettingsPage.kt",
            "UserSettingsPage.kt",
            "OpenSourceLicensePage.kt",
            "ContactUsPage.kt",
            "devices/DeviceListPage.kt",
            "history/OperationHistoryPage.kt",
            "sync/SyncStatusPage.kt",
            "notification/NotificationPage.kt",
        ).map { File(settingsDir, it) }
    }

    @Before
    fun setUp() {
        
        
        
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin {
            modules(
                module {
                    single { org.robolectric.RuntimeEnvironment.getApplication() }
                },
            )
        }
    }

    @Test
    fun allTrackedSettingsFiles_exist() {
        trackedFiles.forEach { file ->
            assertThat(file.exists()).isTrue()
        }
    }

    @Test
    fun settingsPages_doNotImportSmallTitle() {
        val offenders = trackedFiles.filter { it.exists() }
            .filter { it.readLines().any { line -> line.contains("import") && line.contains("SmallTitle") } }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun settingsPages_doNotInvokeSmallTitle() {
        
        val offenders = trackedFiles.filter { it.exists() }.mapNotNull { file ->
            val nonImportContent = file.readLines().filterNot { it.trimStart().startsWith("import") }
            if (nonImportContent.any { it.contains("SmallTitle(") }) file.name else null
        }

        assertThat(offenders).isEmpty()
    }
}
