package top.mcxiafeng.badger.network.core

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ServerAddressStoreTest {

    private fun createStore(): Pair<ServerAddressStore, SharedPreferences.Editor> {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        // Editor 的链式 builder 每次都返回自身，否则 verify 不到链上的调用
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.clear() } returns editor
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return ServerAddressStore(context) to editor
    }

    @Test
    fun setActive_marksConfiguredAndPersists() {
        val (store, editor) = createStore()

        assertThat(store.hasActiveServer.value).isFalse()

        store.setActive("https://badger.example.com:51026")

        assertThat(store.hasActiveServer.value).isTrue()
        assertThat(store.activeUrl.value).isEqualTo("https://badger.example.com:51026")
        verify {
            editor.putString("active_url", "https://badger.example.com:51026")
            editor.putBoolean("active_configured", true)
        }
    }

    @Test
    fun setActive_resetsVerification() {
        val (store, _) = createStore()
        store.markUrlVerified()
        assertThat(store.isUrlVerified.value).isTrue()

        store.setActive("http://other")

        assertThat(store.isUrlVerified.value).isFalse()
    }

    @Test
    fun addHistory_deduplicatesAndCaps() {
        val (store, _) = createStore()

        store.addHistory("http://a")
        store.addHistory("http://b")
        store.addHistory("http://a")
        store.addHistory("http://c")
        store.addHistory("http://d")
        store.addHistory("http://e")

        // 去重 + 最近在前 + 上限 4
        assertThat(store.history.value)
            .containsExactly("http://e", "http://d", "http://c", "http://a")
            .inOrder()
    }

    @Test
    fun removeHistory_removesEntry() {
        val (store, _) = createStore()
        store.addHistory("http://a")
        store.addHistory("http://b")

        store.removeHistory("http://a")

        assertThat(store.history.value).containsExactly("http://b")
    }
}
