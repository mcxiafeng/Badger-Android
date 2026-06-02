package top.mcxiafeng.badger.data

/**
 * 获取有效的 collectionId：优先使用指定值，否则取第一个名片夹，没有则自动创建
 */
suspend fun ensureCollectionId(repository: ContactRepository, preferredId: Long?): Long {
    if (preferredId != null && preferredId > 0L) {
        val exists = repository.getAllCollectionsOnce().any { it.id == preferredId }
        if (exists) return preferredId
    }
    val collections = repository.getAllCollectionsOnce()
    if (collections.isNotEmpty()) return collections.first().id
    return repository.insertCollection(CardCollection(name = "默认名片夹"))
}
