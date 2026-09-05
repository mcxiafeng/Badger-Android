package top.mcxiafeng.badger.shared.util

/**
 * [KMP K08-B] iOS actual 骨架：拉丁字母直接映射；汉字暂归 `#`（排序桶）。
 * K16（iosApp 工程）接入 NSLocale / 拼音转换库后补齐 Han-Latin 转写。
 */
actual object PinyinUtils {

    actual fun getPinyinInitial(char: Char): String {
        if (char in 'A'..'Z') return char.toString()
        if (char in 'a'..'z') return char.uppercaseChar().toString()
        return "#"
    }

    actual fun getContactPinyinInitial(name: String): String =
        name.firstOrNull()?.let(::getPinyinInitial) ?: "#"
}
