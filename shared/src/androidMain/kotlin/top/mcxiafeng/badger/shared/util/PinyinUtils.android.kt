package top.mcxiafeng.badger.shared.util

import android.icu.text.Transliterator
import java.text.Normalizer

actual object PinyinUtils {

    
    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }
            .onFailure { android.util.Log.e("PinyinUtils", "Transliterator not available", it) }
            .getOrNull()
    }

    actual fun getPinyinInitial(char: Char): String {
        if (char in 'A'..'Z') return char.toString()
        if (char in 'a'..'z') return char.uppercaseChar().toString()

        val pinyin = transliterator
            ?.transliterate(char.toString())
            ?.let { value ->
                Normalizer.normalize(value, Normalizer.Form.NFD)
                    .filter(Char::isLetter)
            }

        return pinyin
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "#"
    }

    actual fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
