package top.mcxiafeng.badger.pages.person

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersonScrollRestorePolicyTest {
    @Test fun databaseRankIncludesFixedRowsAndPreservesPixelOffset() {
        assertThat(PersonScrollRestorePolicy.target(7_550, 10_000, 3, 23))
            .isEqualTo(PersonScrollRestorePolicy.Target(7_553, 23))
    }

    @Test fun deletingTheLastAnchorClampsToFinalRowInsteadOfTop() {
        assertThat(PersonScrollRestorePolicy.target(179, 179, 3, 12))
            .isEqualTo(PersonScrollRestorePolicy.Target(181, 0))
    }

    @Test fun transientOrRealEmptyResultDoesNotConsumeAnInvalidScrollTarget() {
        assertThat(PersonScrollRestorePolicy.target(90, 0, 3, 12)).isNull()
    }

    @Test fun negativeOffsetAndRankAreBounded() {
        assertThat(PersonScrollRestorePolicy.target(-1, 30, 2, -5))
            .isEqualTo(PersonScrollRestorePolicy.Target(2, 0))
    }
}
