package top.mcxiafeng.badger.domain

import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncPullResult

/**
 * 下拉刷新统一入口（联系人 / 名片夹列表页共用）。
 *
 * 已登录才触发一轮完整同步（[SyncEngine.syncOnce] = 回填 CREATE → push → pull）；
 * 结果落 Room cache 后由各页已有的 Room Flow 自动推给 UI，无需手动重载列表。
 */
class RefreshFromServerUseCase(
    private val syncEngine: SyncEngine,
    private val userAuthRepository: UserAuthRepository,
) {
    /** 刷新三态：未登录 / 成功（applied = 本次 pull 应用的变更数）/ 失败。 */
    sealed interface Result {
        data object NotSignedIn : Result
        data class Done(val applied: Int) : Result
        data object Failed : Result
    }

    suspend operator fun invoke(): Result = withContext(BadgerDispatchers.io) {
        if (userAuthRepository.state.value !is AuthState.SignedIn) {
            return@withContext Result.NotSignedIn
        }
        when (val pull = syncEngine.syncOnce().pull) {
            is SyncPullResult.Done -> Result.Done(pull.applied)
            // Skipped 仅由 syncOnceIfIdle 返回，syncOnce 路径不会走到，兜底归入 Done
            SyncPullResult.Skipped -> Result.Done(applied = 0)
            is SyncPullResult.Failed -> Result.Failed
        }
    }
}
