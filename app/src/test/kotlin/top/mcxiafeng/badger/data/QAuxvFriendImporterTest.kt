package top.mcxiafeng.badger.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.data.importer.QAuxvFriendEntry
import top.mcxiafeng.badger.data.importer.QAuxvFriendImporter

/**
 * QAuxvFriendImporter 解析器单测（纯 JUnit，无需 Robolectric）。
 *
 * 覆盖：
 *   - JSON 正常/损坏/空数组
 *   - CSV 无转义/含双引号转义/空 remark/CRLF
 *   - displayName 优先级：remark → nick → uin
 *   - 无效过滤：uin=0、uin 非数字、列数 < 3
 *   - status 原样保留（不参与过滤）
 */
class QAuxvFriendImporterTest {

    // ========== JSON ==========

    @Test
    fun parse_jsonEmptyArray_returnsEmpty() {
        val result = QAuxvFriendImporter.parse("[]")
        assertThat(result).isEmpty()
    }

    @Test
    fun parse_jsonNormal4Entries_returns4() {
        val json = """
            [
              {"uin":10001,"remark":"小明","nick":"Ming","status":4},
              {"uin":10002,"remark":"小红","nick":"Hong","status":3},
              {"uin":10003,"remark":"","nick":"Wang","status":5},
              {"uin":10004,"remark":null,"nick":"Li","status":6}
            ]
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(json)
        assertThat(result).hasSize(4)
        assertThat(result[0].displayName).isEqualTo("小明")
        assertThat(result[0].uin).isEqualTo(10001L)
        assertThat(result[0].status).isEqualTo(4)
        // 空 remark 回退 nick
        assertThat(result[2].displayName).isEqualTo("Wang")
        // null remark 回退 nick
        assertThat(result[3].displayName).isEqualTo("Li")
    }

    @Test
    fun parse_jsonRemarkAndNickBothBlank_usesUin() {
        val json = """[{"uin":123456,"remark":"","nick":"","status":4}]"""
        val result = QAuxvFriendImporter.parse(json)
        assertThat(result).hasSize(1)
        assertThat(result[0].displayName).isEqualTo("123456")
    }

    @Test
    fun parse_jsonBroken_throwsIllegalArgument() {
        try {
            QAuxvFriendImporter.parse("[{bad json")
            error("should have thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun parse_jsonStatusAllValues_preserved() {
        val json = """
            [
              {"uin":1,"remark":"a","nick":"a","status":0},
              {"uin":2,"remark":"b","nick":"b","status":1},
              {"uin":3,"remark":"c","nick":"c","status":2},
              {"uin":4,"remark":"d","nick":"d","status":3},
              {"uin":5,"remark":"e","nick":"e","status":4},
              {"uin":6,"remark":"f","nick":"f","status":5},
              {"uin":7,"remark":"g","nick":"g","status":6},
              {"uin":8,"remark":"h","nick":"h","status":7}
            ]
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(json)
        // 全部 8 条都应保留（按用户决策 status 不参与过滤）
        assertThat(result).hasSize(8)
        assertThat(result.map { it.status }.toSet()).isEqualTo(setOf(0, 1, 2, 3, 4, 5, 6, 7))
    }

    @Test
    fun parse_jsonZeroUin_skipped() {
        val json = """[{"uin":0,"remark":"a","nick":"a","status":4},{"uin":1,"remark":"b","nick":"b","status":4}]"""
        val result = QAuxvFriendImporter.parse(json)
        assertThat(result).hasSize(1)
        assertThat(result[0].uin).isEqualTo(1L)
    }

    @Test
    fun parse_jsonNegativeUin_skipped() {
        val json = """[{"uin":-5,"remark":"a","nick":"a","status":4},{"uin":1,"remark":"b","nick":"b","status":4}]"""
        val result = QAuxvFriendImporter.parse(json)
        assertThat(result).hasSize(1)
        assertThat(result[0].uin).isEqualTo(1L)
    }

    @Test
    fun parse_jsonMalformedElement_skipped() {
        // 第二条缺 status 字段（asInt 默认 0）；其他都正常
        val json = """
            [
              {"uin":1,"remark":"a","nick":"a","status":4},
              {"uin":2,"remark":"b","nick":"b"}
            ]
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(json)
        assertThat(result).hasSize(2)
    }

    // ========== CSV ==========

    @Test
    fun parse_csvNoEscape_3Lines() {
        val csv = """
            10001,小明,Ming,4
            10002,小红,Hong,3
            10003,,Wang,5
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(3)
        assertThat(result[0].displayName).isEqualTo("小明")
        assertThat(result[2].displayName).isEqualTo("Wang")
    }

    @Test
    fun parse_csvCrlfLineEndings_normalized() {
        val csv = "10001,小明,Ming,4\r\n10002,小红,Hong,3\r\n"
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(2)
    }

    @Test
    fun parse_csvQuotedFieldWithComma() {
        // 字段包含逗号时按 csvenc 规则被双引号包裹
        val csv = """10001,"张三,三",Zhangsan,4"""
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].displayName).isEqualTo("张三,三")
        assertThat(result[0].rawNick).isEqualTo("Zhangsan")
    }

    @Test
    fun parse_csvQuotedFieldWithEscapedDoubleQuote() {
        // 字段含双引号时：字段被双引号包裹，内部 " 写成 ""
        // 字段内容是一个双引号字符 -> 包裹后为 """"，前面加 uin 和逗号 -> 完整字符串 10001,"""",Alice,4
        // 原始字符串无法表达 """，故用普通字符串 + \"
        val csv = "10001,\"\"\"\",Alice,4"
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].rawRemark).isEqualTo("\"")
    }

    @Test
    fun parse_csvQuotedFieldWithSpace() {
        // csvenc: 字段含空格也要 quote
        val csv = """10001," hello ",nick,4"""
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].rawRemark).isEqualTo(" hello ")
    }

    @Test
    fun parse_csvEmptyLines_skipped() {
        val csv = """

            10001,小明,Ming,4

            10002,小红,Hong,3

        """.trimIndent()
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(2)
    }

    @Test
    fun parse_csvNonNumericUin_skipped() {
        val csv = """
            abc,小明,Ming,4
            10002,小红,Hong,3
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].uin).isEqualTo(10002L)
    }

    @Test
    fun parse_csvZeroUin_skipped() {
        val csv = """
            0,小明,Ming,4
            10002,小红,Hong,3
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].uin).isEqualTo(10002L)
    }

    @Test
    fun parse_csvInsufficientColumns_skipped() {
        val csv = """
            10001,小明
            10002,小红,Hong,3
        """.trimIndent()
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].uin).isEqualTo(10002L)
    }

    @Test
    fun parse_csvRemarkBlankNickBlank_usesUin() {
        val csv = "10001,,,4"
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].displayName).isEqualTo("10001")
    }

    @Test
    fun parse_csvMissingStatusColumn_defaultsTo0() {
        val csv = "10001,小明,Ming"
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
        assertThat(result[0].status).isEqualTo(0)
    }

    @Test
    fun parse_csvAutoSniffNotJson() {
        // 既不是 '[' 开头又不是 JSON
        val csv = "10001,小明,Ming,4"
        val result = QAuxvFriendImporter.parse(csv)
        assertThat(result).hasSize(1)
    }

    // ========== splitCsvLine 单元 ==========

    @Test
    fun splitCsvLine_simple() {
        val r = QAuxvFriendImporter.splitCsvLine("a,b,c")
        assertThat(r).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun splitCsvLine_quoted() {
        val r = QAuxvFriendImporter.splitCsvLine("\"a,b\",c")
        assertThat(r).containsExactly("a,b", "c").inOrder()
    }

    @Test
    fun splitCsvLine_escapedQuote() {
        val r = QAuxvFriendImporter.splitCsvLine("\"a\"\"b\",c")
        assertThat(r).containsExactly("a\"b", "c").inOrder()
    }

    @Test
    fun splitCsvLine_emptyFields() {
        val r = QAuxvFriendImporter.splitCsvLine(",,")
        assertThat(r).containsExactly("", "", "").inOrder()
    }

    // ========== statusLabel ==========

    @Test
    fun entry_statusLabel_chineseMapping() {
        val cases = mapOf(
            0 to "错误数据",
            3 to "历史好友",
            4 to "互为好友",
            5 to "我加对方",
            6 to "对方加我",
            7 to "黑名单",
            99 to "未知(99)",
        )
        for ((code, label) in cases) {
            val entry = QAuxvFriendEntry(1L, "x", null, null, code)
            assertThat(entry.statusLabel).isEqualTo(label)
        }
    }
}