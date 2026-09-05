package top.mcxiafeng.badger.shared.util

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * [KMP K08-B] Android actual：ICU Transliterator（Han-Latin），覆盖率远超手工 Map。
 */
actual object PinyinUtils {

    /** 拼音转写器（Han-Latin），将汉字转为带声调的拼音 */
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
