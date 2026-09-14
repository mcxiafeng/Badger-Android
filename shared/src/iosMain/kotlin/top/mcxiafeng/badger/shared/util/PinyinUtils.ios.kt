package top.mcxiafeng.badger.shared.util

import platform.Foundation.NSString
import platform.Foundation.stringByApplyingTransform

actual object PinyinUtils {

    
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
            
            if (first.isLetter()) {
                return first.uppercaseChar().toString()
            }
        }
        return "#"
    }

    actual fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
