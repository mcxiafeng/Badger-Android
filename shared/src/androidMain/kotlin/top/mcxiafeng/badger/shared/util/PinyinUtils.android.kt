package top.mcxiafeng.badger.shared.util

import android.icu.text.Transliterator
import android.os.Build

actual object PinyinUtils {
    private var transliterator: Transliterator? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                transliterator = Transliterator.getInstance("Han-Latin; Latin-ASCII")
            } catch (_: Throwable) {
            }
        }
    }

    actual fun getPinyinInitial(char: Char): String {
        if (char in 'A'..'Z') return char.toString()
        if (char in 'a'..'z') return char.uppercaseChar().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pinyin = transliterator?.transliterate(char.toString())
                if (!pinyin.isNullOrEmpty()) {
                    val first = pinyin.first()
                    if (first in 'A'..'Z' || first in 'a'..'z') {
                        return first.uppercaseChar().toString()
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return "#"
    }

    actual fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
