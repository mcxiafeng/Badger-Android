package top.mcxiafeng.badger.domain

import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncPullResult

class RefreshFromServerUseCase(
    private val syncEngine: SyncEngine,
    private val userAuthRepository: UserAuthRepository,
) {
    
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
            
            SyncPullResult.Skipped -> Result.Done(applied = 0)
            is SyncPullResult.Failed -> Result.Failed
        }
    }
}
