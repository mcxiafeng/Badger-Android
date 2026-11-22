package top.mcxiafeng.badger.pages.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.sync.SyncProgress

class AccountPresentationTest {
    @Test fun authorizationFailureOffersLoginWithoutExposingCode() {
        val status = presentAccountSync(false, SyncProgress(message = "UNAUTHORIZED", pending = true))
        assertThat(status.action).isEqualTo(AccountSyncAction.LOGIN)
        assertThat(status.title).isEqualTo("需要重新登录")
        assertThat(status.detail).doesNotContain("UNAUTHORIZED")
    }

    @Test fun pendingChangesCannotClaimSynchronizationComplete() {
        val status = presentAccountSync(false, SyncProgress(message = "已同步", pending = true))
        assertThat(status.tone).isNotEqualTo(AccountSyncTone.SUCCESS)
        assertThat(status.title).isEqualTo("有资料待同步")
        assertThat(status.detail).isNull()
    }

    @Test fun confirmedSynchronizationCanShowSuccess() {
        val status = presentAccountSync(false, SyncProgress(message = "已同步"))
        assertThat(status.tone).isEqualTo(AccountSyncTone.SUCCESS)
        assertThat(status.action).isEqualTo(AccountSyncAction.SYNC)
    }

    @Test fun unknownTechnicalErrorGetsAnActionableMessage() {
        val status = presentAccountSync(false, SyncProgress(message = "INTERNAL_SERVER_ERROR", pending = true))
        assertThat(status.title).isEqualTo("同步未完成")
        assertThat(status.detail).doesNotContain("INTERNAL_SERVER_ERROR")
        assertThat(status.actionLabel).isEqualTo("重试同步")
    }

    @Test fun meaningfulChineseErrorIsRetained() {
        val message = "此项资料超过云端容量限制，请拆分后重试"
        assertThat(presentAccountSync(false, SyncProgress(message = message, pending = true)).detail).isEqualTo(message)
    }
}
