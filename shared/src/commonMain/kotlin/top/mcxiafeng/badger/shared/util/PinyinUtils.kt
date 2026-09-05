package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08-B] 拼音首字母（联系人排序用）。
 *
 * Android actual = ICU Transliterator（Han-Latin，NFD 去声调）；
 * iOS actual 骨架：拉丁字母直接返回，汉字暂归 `#`（排序桶），K16 接 NSLocale/拼音库。
 */
expect object PinyinUtils {
    /** 获取单个字符的首字母（大写）。无法识别归入 `#`。 */
    fun getPinyinInitial(char: Char): String

    /** 获取姓名首字母。 */
    fun getContactPinyinInitial(name: String): String
}
