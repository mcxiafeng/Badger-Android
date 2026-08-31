package top.mcxiafeng.badger.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionNodeTest {
    @Test
    fun safe_log_url_redacts_region_source_url() {
        assertEquals(
            "https://raw.githubusercontent.com/<path-redacted>",
            SafeLog.url("https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json/states.json"),
        )
    }
}
