package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import top.mcxiafeng.badger.ui.components.BadgerFloatingBarList
import top.mcxiafeng.badger.ui.components.badgerBottomBarPadding
import top.mcxiafeng.badger.ui.components.badgerListContentPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Composable
internal fun SettingsListScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(BadgerSpacing.md),
    content: LazyListScope.() -> Unit,
) {
    SettingsSubPageScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        actions = actions,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        BadgerFloatingBarList(
            modifier = Modifier.badgerBottomBarPadding(),
            contentPadding = badgerListContentPadding(
                scaffoldTop = innerPadding.calculateTopPadding(),
                scaffoldBottom = innerPadding.calculateBottomPadding(),
                topExtra = BadgerSpacing.sm,
                bottomExtra = BadgerSpacing.sm,
                start = BadgerSpacing.md,
                end = BadgerSpacing.md,
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
