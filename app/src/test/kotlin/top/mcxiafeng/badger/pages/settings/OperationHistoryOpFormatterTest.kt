package top.mcxiafeng.badger.pages.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes

class OperationHistoryOpFormatterTest {

    private fun historyEntity(
        opId: String = "op-id",
        opType: String,
        opStatus: String = "WITHDRAWN",
        canUndo: Boolean = true,
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
        canUndo = canUndo,
        canReplay = false,
    )

    @Test
    fun `local-only undo is true for sub-table ops withdrawn without remote sync`() {
        val op = historyEntity(opType = OperationTypes.ADD_PLATFORM, opStatus = "WITHDRAWN")
        assertThat(OperationHistoryOpFormatter.isLocalOnlyUndo(op)).isTrue()
        assertThat(OperationHistoryOpFormatter.localOnlySuffix(op)).contains("仅本地")
    }

    @Test
    fun `local-only undo is true for STAR REMOVE_TAG UPDATE_FIELD_VALUE etc`() {
        listOf(
            OperationTypes.STAR,
            OperationTypes.UNSTAR,
            OperationTypes.ADD_TAG,
            OperationTypes.REMOVE_TAG,
            OperationTypes.UPDATE_FIELD_VALUE,
            OperationTypes.REMOVE_FIELD_VALUE,
            OperationTypes.UPDATE_PLATFORM,
            OperationTypes.REMOVE_PLATFORM,
        ).forEach { opType ->
            val op = historyEntity(opType = opType, opStatus = "WITHDRAWN")
            assertThat(OperationHistoryOpFormatter.isLocalOnlyUndo(op)).isTrue()
        }
    }

    @Test
    fun `local-only undo is false for fully synced undo paths`() {
        listOf(
            OperationTypes.UPDATE_NAME,
            OperationTypes.UPDATE_BIO,
            OperationTypes.UPDATE_NOTE,
            OperationTypes.CREATE_CONTACT,
        ).forEach { opType ->
            val op = historyEntity(opType = opType, opStatus = "WITHDRAWN")
            assertThat(OperationHistoryOpFormatter.isLocalOnlyUndo(op)).isFalse()
            assertThat(OperationHistoryOpFormatter.localOnlySuffix(op)).isEmpty()
        }
    }

    @Test
    fun `local-only undo suffix is empty for non-WITHDRAWN statuses even on sub-table ops`() {
        listOf("PENDING", "IN_FLIGHT", "DONE", "FAILED", "FAILED_PERMANENT", "CONFLICT").forEach { status ->
            val op = historyEntity(opType = OperationTypes.ADD_PLATFORM, opStatus = status)
            assertThat(OperationHistoryOpFormatter.isLocalOnlyUndo(op)).isFalse()
            assertThat(OperationHistoryOpFormatter.localOnlySuffix(op)).isEmpty()
        }
    }
}
