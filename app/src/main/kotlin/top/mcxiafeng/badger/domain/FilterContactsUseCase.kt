package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity as Contact
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject

/**
 * 联系人过滤排序 UseCase
 *
 * 根据搜索关键词和排序类型过滤联系人列表。
 * 纯函数，无副作用。
 */
class FilterContactsUseCase @Inject constructor() {

    operator fun invoke(
        contacts: List<Contact>,
        query: String,
        sortType: Int
    ): List<Contact> {
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                    contact.note?.contains(query, ignoreCase = true) == true
            }
        }

        return if (sortType == 1) {
            filtered.sortedByDescending { PinyinUtils.getContactPinyinInitial(it.name) + it.name }
        } else {
            filtered.sortedBy { PinyinUtils.getContactPinyinInitial(it.name) + it.name }
        }
    }
}
