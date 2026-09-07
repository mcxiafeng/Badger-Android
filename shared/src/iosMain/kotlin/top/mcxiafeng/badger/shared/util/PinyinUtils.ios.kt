package top.mcxiafeng.badger.shared.util

import platform.Foundation.NSString
import platform.Foundation.stringByApplyingTransform

/**
 * [KMP K08-B→K16] iOS actual：NSString Han-Latin 转写获取拼音首字母。
 *
 * 使用 NSString.stringByApplyingTransform("Han-Latin") 将汉字转拼音（带声调），
 * 取首字母并归一化——声调作为组合字符附在元音上（如 ā），首字符通常为辅音字母；
 * 元音开头时经 isLetter 判定后取 upperCase。
 */
actual object PinyinUtils {

    /** K/N 自动桥接 String → NSString 参数 */
    private fun hanToLatin(char: String): String? =
        (char as NSString).stringByApplyingTransform("Han-Latin", reverse = false)

    actual fun getPinyinInitial(char: Char): String {
        if (char in 'A'..'Z') return char.toString()
        if (char in 'a'..'z') return char.uppercaseChar().toString()

        val latin = hanToLatin(char.toString())
        if (latin != null && latin.isNotEmpty()) {
            val first = latin.first()
            if (first in 'A'..'Z' || first in 'a'..'z') {
                return first.uppercaseChar().toString()
            }
            // 元音开头的带声调字符（如 ā/ē/ī/ō/ū/ǖ）——经 isLetter 判定后取 upperCase
            if (first.isLetter()) {
                return first.uppercaseChar().toString()
            }
        }
        return "#"
    }

    actual fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
