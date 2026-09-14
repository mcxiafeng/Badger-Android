package top.mcxiafeng.badger.shared.util

expect object PinyinUtils {
    
    fun getPinyinInitial(char: Char): String

    
    fun getContactPinyinInitial(name: String): String
}
