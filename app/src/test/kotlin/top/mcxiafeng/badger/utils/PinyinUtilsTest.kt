package top.mcxiafeng.badger.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun getContactPinyinInitial_emptyName_returnsHash() {
        assertThat(PinyinUtils.getContactPinyinInitial("")).isEqualTo("#")
    }

    @Test
    fun getContactPinyinInitial_englishName_returnsFirstChar() {
        assertThat(PinyinUtils.getContactPinyinInitial("Alice")).isEqualTo("A")
        assertThat(PinyinUtils.getContactPinyinInitial("bob")).isEqualTo("B")
    }

    @Test
    fun getContactPinyinInitial_chineseName_returnsPinyinInitial() {
        val initial = PinyinUtils.getContactPinyinInitial("张三")
        assertThat(initial).isNotEmpty()
        assertThat(initial).isNotEqualTo("#")
    }

    @Test
    fun getPinyinInitial_multipleChars_onlyProcessesFirst() {
        val result = PinyinUtils.getContactPinyinInitial("ABC")
        assertThat(result).isEqualTo("A")
    }

    @Test
    fun getPinyinInitial_digit_returnsHash() {
        assertThat(PinyinUtils.getPinyinInitial('5')).isEqualTo("#")
    }

    @Test
    fun getPinyinInitial_specialChar_returnsHash() {
        assertThat(PinyinUtils.getPinyinInitial('@')).isEqualTo("#")
        assertThat(PinyinUtils.getPinyinInitial(' ')).isEqualTo("#")
    }
}
