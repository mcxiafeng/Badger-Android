package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspacePullRefresh(
    refreshing: StateFlow<Boolean>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isRefreshing by refreshing.collectAsStateWithLifecycle()
    val refreshState = rememberPullToRefreshState()
    // Miuix 0.9.3 consumes all scrolling while its refresh indicator is running.
    Box(modifier.pullToRefresh(isRefreshing, refreshState, enabled, onRefresh = onRefresh)) {
        content()
        PullToRefreshDefaults.Indicator(
            state = refreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter).semantics {
                if (isRefreshing) contentDescription = "正在同步资料"
            },
            containerColor = MiuixTheme.colorScheme.surfaceContainer,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}
