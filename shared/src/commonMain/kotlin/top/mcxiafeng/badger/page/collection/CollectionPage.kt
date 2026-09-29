package top.mcxiafeng.badger.page.collection

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

class CollectionPage {

    companion object {
        @Composable
        fun PageCollection() {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "名片夹",
                    )
                },
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            ) { paddingValues ->

            }
        }
    }
}