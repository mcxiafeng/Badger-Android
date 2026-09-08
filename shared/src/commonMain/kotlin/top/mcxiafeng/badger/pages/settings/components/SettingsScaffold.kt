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

/**
 * 设置子页统一脚手架。
 *
 * 消除原先在 15 个子页中逐字复制的
 * `Scaffold + TopAppBar(title, scrollBehavior, navigationIcon = ArrowLeft)` 样板。
 *
 * - TopBar 标题取自 [SettingsPage.title]（由调用方传入，禁止再硬编码）。
 * - 返回箭头固定 Lucide.ArrowLeft，回调 [onBack]（路由级 BackHandler 在 AppRoutes 兜底）。
 * - 可选 [actions]（如搜索 / 排序 IconButton）。
 * - 可选 [snackbarHostState]：传入即挂 SnackbarHost，配合 [SettingsMessageEffect] 使用。
 * - [content] 拿到 Scaffold 内边距；二级页内的列表通常再用
 *   [SettingsListScaffold] 或 `BadgerFloatingBarList + badgerListContentPadding`。
 */
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

/**
 * 设置子页列表脚手架：[SettingsSubPageScaffold] + [BadgerFloatingBarList] + 底部避让。
 *
 * 适用于内容为单一可滚动列表的子页（多数设置页）。非列表页（TagManager tabs / LogViewer 文本）
 * 仍用 [SettingsSubPageScaffold] 自行组织内容。
 */
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
