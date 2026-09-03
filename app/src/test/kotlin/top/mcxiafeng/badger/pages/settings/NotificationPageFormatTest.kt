package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.pages.settings.notification.formatNotificationTime

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.ui.formatUnreadBadge

/**
 * [B2] 纯函数：未读角标文案 + 通知时间格式化。不依赖 Compose / Koin。
 */
class NotificationPageFormatTest {

    @Test
    fun formatUnreadBadge_zeroAndNegativeAreHidden() {
        assertThat(formatUnreadBadge(0)).isNull()
        assertThat(formatUnreadBadge(-1)).isNull()
    }

    @Test
    fun formatUnreadBadge_capsAt99Plus() {
        assertThat(formatUnreadBadge(1)).isEqualTo("1")
        assertThat(formatUnreadBadge(99)).isEqualTo("99")
        assertThat(formatUnreadBadge(100)).isEqualTo("99+")
        assertThat(formatUnreadBadge(1234)).isEqualTo("99+")
    }

    @Test
    fun formatNotificationTime_blankIsEmpty() {
        assertThat(formatNotificationTime(null)).isEmpty()
        assertThat(formatNotificationTime("")).isEmpty()
        assertThat(formatNotificationTime("   ")).isEmpty()
    }

    @Test
    fun formatNotificationTime_epochMillis() {
        val formatted = formatNotificationTime("1719792000000")
        assertThat(formatted).matches("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }

    @Test
    fun formatNotificationTime_isoString() {
        val formatted = formatNotificationTime("2026-08-29T10:15:30Z")
        assertThat(formatted).isEqualTo("2026-08-29 10:15")
    }
}
