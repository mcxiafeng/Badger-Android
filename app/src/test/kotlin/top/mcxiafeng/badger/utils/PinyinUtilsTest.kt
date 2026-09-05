package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.shared.util.PinyinUtils

// [K08-B] android.icu.Transliterator 需要 Android runtime——Robolectric 提供 android.icu
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PinyinUtilsTest {

    @Test
    fun getPinyinInitial_englishUppercase_returnsSame() {
        assertThat(PinyinUtils.getPinyinInitial('A')).isEqualTo("A")
        assertThat(PinyinUtils.getPinyinInitial('Z')).isEqualTo("Z")
    }

    @Test
    fun getPinyinInitial_englishLowercase_returnsUppercase() {
        assertThat(PinyinUtils.getPinyinInitial('a')).isEqualTo("A")
        assertThat(PinyinUtils.getPinyinInitial('z')).isEqualTo("Z")
    }

    @Test
    fun getPinyinInitial_chineseCharacter_returnsPinyinInitial() {
        val initial = PinyinUtils.getPinyinInitial('张')
        assertThat(initial).isNotEmpty()
        assertThat(initial).isNotEqualTo("#")
        assertThat(initial).hasLength(1)
        assertThat(initial[0]).isIn('A'..'Z')
    }

    @Test
    fun getPinyinInitial_nonChineseNonEnglish_returnsHash() {
        val result = PinyinUtils.getPinyinInitial('1')
        assertThat(result).isEqualTo("#")
    }

    @Test
    fun getPinyinInitial_digit_returnsHash() {
        assertThat(PinyinUtils.getPinyinInitial('9')).isEqualTo("#")
    }

    @Test
    fun getPinyinInitial_specialChar_returnsHash() {
        assertThat(PinyinUtils.getPinyinInitial('!')).isEqualTo("#")
    }

    @Test
    fun getPinyinInitial_multipleChars_onlyProcessesFirst() {
        // 仅处理首个字符;此处不应被按序列处理
        assertThat(PinyinUtils.getPinyinInitial('a')).isEqualTo("A")
        assertThat(PinyinUtils.getPinyinInitial('A')).isEqualTo("A")
    }

    @Test
    fun getContactPinyinInitial_emptyName_returnsHash() {
        assertThat(PinyinUtils.getContactPinyinInitial("")).isEqualTo("#")
    }

    @Test
    fun getContactPinyinInitial_chineseName_returnsPinyinInitial() {
        assertThat(PinyinUtils.getContactPinyinInitial("张三")).isEqualTo("Z")
    }

    @Test
    fun getContactPinyinInitial_englishName_returnsFirstChar() {
        assertThat(PinyinUtils.getContactPinyinInitial("alice")).isEqualTo("A")
    }
}