package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 回归测试：保证 [pages/settings] 下的所有页面不再使用 `SmallTitle`。
 *
 * 背景：产品决策要求设置页不再展示 SmallTitle 灰色分组标题。
 * 用源码文本扫描替代 Compose UI 测试，理由：
 *   1. 这些是纯渲染屏幕，几乎无可测业务逻辑；
 *   2. Compose 渲染树断言脆弱、容易随 Miuix 内部结构变更而失效；
 *   3. 真正想固化的就是"不要引入 SmallTitle"，最直接的断言就是源码里不存在。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmallTitleUsageTest {

    private val settingsDir: File by lazy {
        // Working directory at test runtime is the Gradle module dir (app/)
        val path = "src/main/kotlin/top/mcxiafeng/badger/pages/settings"
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("app/$path"),
        )
        candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("无法定位 pages/settings 源码目录，尝试过：$candidates")
    }

    private val trackedFiles: List<File> by lazy {
        // 显式列举需要扫描的页面文件；新增页面必须主动加入此列表才能被覆盖到。
        // AccountAndBackupPage 已经过人工审查也不使用 SmallTitle，无需每次新增合并页都追加。
        listOf(
            "SettingsPage.kt",
            "AboutPage.kt",
            "NfcSettingsPage.kt",
            "UiSettingsPage.kt",
            "OpenSourceLicensePage.kt",
            "ContactUsPage.kt",
            "PlatformListPage.kt",
        ).map { File(settingsDir, it) }
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
        // 去掉 import 行后再扫，避免 import 与调用各自存在时被简单重复计数掩盖另一种回归
        val offenders = trackedFiles.filter { it.exists() }.mapNotNull { file ->
            val nonImportContent = file.readLines().filterNot { it.trimStart().startsWith("import") }
            if (nonImportContent.any { it.contains("SmallTitle(") }) file.name else null
        }

        assertThat(offenders).isEmpty()
    }
}
