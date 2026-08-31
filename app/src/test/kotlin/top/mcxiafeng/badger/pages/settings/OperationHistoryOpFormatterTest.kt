package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact

/** OperationHistoryOpFormatter（只读日志版）测试。 */
class OperationHistoryOpFormatterTest {

    private fun historyEntity(
        opId: String = "op-id",
        opType: String,
        opStatus: String = "DONE",
    ) = OperationHistoryEntity(
        opId = opId,
        contactId = 1L,
        opType = opType,
        opLabel = OperationTypes.labelOf(opType),
        payloadJson = "{}",
        snapshotBeforeJson = "{}",
        snapshotAfterJson = "{}",
        createdAt = 0L,
        opStatus = opStatus,
        canUndo = false,
        canReplay = false,
    )

    @Test
    fun `status labels map to chinese`() {
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("PENDING")).isEqualTo("等待中")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("DONE")).isEqualTo("成功")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("CONFLICT")).isEqualTo("冲突")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("FAILED")).isEqualTo("失败")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("FAILED_PERMANENT")).isEqualTo("永久失败")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("WITHDRAWN")).isEqualTo("已撤销")
        assertThat(OperationHistoryOpFormatter.formatStatusLabel("UNKNOWN")).isEqualTo("UNKNOWN")
    }

    @Test
    fun `pending status classification includes failed`() {
        assertThat(OperationHistoryOpFormatter.isPendingStatus("CONFLICT")).isTrue()
        assertThat(OperationHistoryOpFormatter.isPendingStatus("FAILED")).isTrue()
        assertThat(OperationHistoryOpFormatter.isPendingStatus("FAILED_PERMANENT")).isTrue()
        assertThat(OperationHistoryOpFormatter.isPendingStatus("DONE")).isFalse()
    }

    @Test
    fun `contact name falls back for deleted contact`() {
        assertThat(OperationHistoryOpFormatter.formatContactName("Alice")).isEqualTo("Alice")
        assertThat(OperationHistoryOpFormatter.formatContactName(null)).isEqualTo("(已删除)")
    }

    @Test
    fun `list subtitle joins time and op label`() {
        val item = OperationHistoryWithContact(
            history = historyEntity(opType = OperationTypes.UPDATE_NAME, opStatus = "DONE"),
            contactName = "Alice",
        )
        val subtitle = OperationHistoryOpFormatter.formatListSubtitle(item)
        assertThat(subtitle).contains(OperationTypes.labelOf(OperationTypes.UPDATE_NAME))
    }

    @Test
    fun `detail summary joins contact and op label`() {
        val item = OperationHistoryWithContact(
            history = historyEntity(opType = OperationTypes.DELETE_CONTACT, opStatus = "DONE"),
            contactName = "Bob",
        )
        val summary = OperationHistoryOpFormatter.formatDetailSummary(item)
        assertThat(summary).contains("Bob")
        assertThat(summary).contains(OperationTypes.labelOf(OperationTypes.DELETE_CONTACT))
    }

    @Test
    fun `filter labels map to chinese`() {
        assertThat(OperationHistoryOpFormatter.formatFilterLabel(HistoryFilter.All)).isEqualTo("全部")
        assertThat(OperationHistoryOpFormatter.formatFilterLabel(HistoryFilter.Pending)).isEqualTo("待处理")
    }
}
