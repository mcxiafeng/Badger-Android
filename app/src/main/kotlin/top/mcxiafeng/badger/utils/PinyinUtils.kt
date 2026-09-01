package top.mcxiafeng.badger.utils

import android.icu.text.Transliterator
import android.util.Log
import java.text.Normalizer

object PinyinUtils {

    /** 拼音转写器（Han-Latin），将汉字转为带声调的拼音 */
    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }
            .onFailure { Log.e("PinyinUtils", "Transliterator not available", it) }
            .getOrNull()
    }

    /**
     * 获取单个字符的首字母（大写）。
     *
     * 使用 ICU Transliterator 将汉字转拼音，覆盖率远超手工 Map。
     * ICU 不可用或无法识别时归入 `#`。
     */
    fun getPinyinInitial(char: Char): String {
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

    /**
     * 获取姓名首字母。
     */
    fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
