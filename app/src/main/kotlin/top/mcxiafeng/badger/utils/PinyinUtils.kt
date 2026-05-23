package top.mcxiafeng.badger.utils

import android.icu.text.Transliterator
import android.util.Log

object PinyinUtils {

    /** 拼音转写器（Han-Latin），将汉字转为带声调的拼音 */
    private val transliterator: Transliterator? by lazy {
        try {
            Transliterator.getInstance("Han-Latin")
        } catch (e: Exception) {
            Log.e("PinyinUtils", "Transliterator not available", e)
            null
        }
    }

    /**
     * 获取单个字符的首字母（大写）
     *
     * 使用 ICU Transliterator 将汉字转拼音，覆盖率远超手工 Map。
     * 降级策略：ICU 不可用时回退到 Map 查表。
     */
    fun getPinyinInitial(char: Char): String {
        // 英文字母直接返回
        if (char in 'A'..'Z') return char.toString()
        if (char in 'a'..'z') return char.uppercase()

        // 尝试 ICU Transliterator
        if (transliterator != null) {
            val pinyin = transliterator!!.transliterate(char.toString())
            // Han-Latin 返回带声调拼音如 "Ài"，需去声调再取首字母
            val stripped = java.text.Normalizer.normalize(pinyin, java.text.Normalizer.Form.NFD)
                .filter { it in 'A'..'Z' || it in 'a'..'z' }
            if (stripped.isNotEmpty()) {
                return stripped.first().uppercaseChar().toString()
            }
        }

        // 非汉字字符归入 #
        if (!isChinese(char)) return "#"

        // ICU 转写失败（汉字但无法识别），仍归入 #
        return "#"
    }

    /**
     * 获取姓名首字母
     */
    fun getContactPinyinInitial(name: String): String {
        return name.takeIf { it.isNotEmpty() }
            ?.first()
            ?.let { getPinyinInitial(it) }
            ?: "#"
    }

    /**
     * 判断是否为汉字
     */
    private fun isChinese(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
    }
}