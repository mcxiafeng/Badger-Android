package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [CountryCatalog] 过滤逻辑测试。
 *
 * 国家列表是联系人「国家」字段的唯一数据源,必须保证:
 * 非空、去重、关键字过滤正确、大小写/空串行为符合预期。
 */
class CountryCatalogTest {

    @Test
    fun all_isNotEmpty_andHasNoDuplicates() {
        val all = CountryCatalog.ALL

        assertThat(all).isNotEmpty()
        assertThat(all).contains("中国")
        assertThat(all).contains("美国")
        assertThat(all).containsNoDuplicates()
        assertThat(all.all { it.isNotBlank() }).isTrue()
    }

    @Test
    fun filter_blankKeyword_returnsAll() {
        assertThat(CountryCatalog.filter("")).isEqualTo(CountryCatalog.ALL)
        assertThat(CountryCatalog.filter("   ")).isEqualTo(CountryCatalog.ALL)
    }

    @Test
    fun filter_matchesChineseSubstring() {
        assertThat(CountryCatalog.filter("中国")).containsAtLeast("中国", "中国香港", "中国澳门", "中国台湾")
        assertThat(CountryCatalog.filter("韩国")).containsExactly("韩国")
        assertThat(CountryCatalog.filter("新加坡")).containsExactly("新加坡")
    }

    @Test
    fun filter_isCaseInsensitiveForAscii() {
        // 目录为中文,但过滤实现不应因大小写或空格产生意外
        assertThat(CountryCatalog.filter(" 中国 ")).isNotEmpty()
    }

    @Test
    fun filter_noMatch_returnsEmpty() {
        assertThat(CountryCatalog.filter("不存在的国家名XYZ")).isEmpty()
    }

    @Test
    fun filter_everyResultContainsKeyword() {
        val keyword = "国"
        val result = CountryCatalog.filter(keyword)

        assertThat(result).isNotEmpty()
        assertThat(result.all { it.contains(keyword) }).isTrue()
    }

    @Test
    fun domesticAdcode_matchesDomesticRoots_only() {
        assertThat(CountryCatalog.domesticAdcode("  中国  ")).isEqualTo("100000")
        assertThat(CountryCatalog.domesticAdcode("中国香港")).isEqualTo("810000")
        assertThat(CountryCatalog.domesticAdcode("中国澳门")).isEqualTo("820000")
        assertThat(CountryCatalog.domesticAdcode("中国台湾")).isEqualTo("710000")
        assertThat(CountryCatalog.domesticAdcode("蒙古")).isNull()
        assertThat(CountryCatalog.domesticAdcode("日本")).isNull()
    }
}
