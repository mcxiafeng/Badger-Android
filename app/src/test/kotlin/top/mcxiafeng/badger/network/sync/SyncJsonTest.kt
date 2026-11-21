package top.mcxiafeng.badger.network.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncJsonTest {
    @Test fun duplicateFieldsAreRejectedBeforeUse() {
        assertThrows(IllegalArgumentException::class.java) { SyncJson.parse("""{"value":{"name":"A","name":"B"}}""") }
    }
    @Test fun versionsRemainExactAboveFloatingPointPrecision() {
        val version = "9007199254740993"
        assertThat(SyncJson.counter(version).toString()).isEqualTo(version)
        assertThat(SyncJson.text(SyncJson.parse("""{"version":"$version"}"""), "version")).isEqualTo(version)
    }
    @Test fun alternateCounterSpellingsAreRejected() {
        listOf("01", "1.0", "1e2", "-1", "9223372036854775808").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { SyncJson.counter(value) }
        }
    }
    @Test fun numericVersionsCannotMasqueradeAsStrings() {
        assertThrows(IllegalArgumentException::class.java) { SyncJson.text(SyncJson.parse("""{"version":1}"""), "version") }
    }
    @Test fun rowDigestUsesExactWireBytesWithEscapedBrackets() {
        val rows = """[{"note":"引号\"和]括号","confidence":1.0}]"""
        val page = """{"nextPageToken":"token","rows":$rows,"revision":"2"}"""
        assertThat(String(SyncJson.wireRows(page), Charsets.UTF_8)).isEqualTo(rows)
    }
    @Test fun unpairedSurrogatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SyncJson.parse("{\"note\":\"\\ud800\"}") }
    }
    @Test fun unexpectedTrailingContentIsRejected() {
        assertThrows(Exception::class.java) { SyncJson.parse("{} {}") }
    }
}
