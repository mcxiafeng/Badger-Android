package top.mcxiafeng.badger.data.repository

sealed class CommitResult {
    
    data object SentSuccess : CommitResult()

    
    data class Written(val contactId: Long) : CommitResult()

    
    data class SentFailed(val reason: String) : CommitResult()

    
    data object NotFound : CommitResult()
}
