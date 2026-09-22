package top.mcxiafeng.badger.page.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowBigUp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.BadgeX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MoveLeft
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash2
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SyncPage {

    companion object {

        @Composable
        fun PageSync(
            onBackClick: () -> Unit,
        ) {
//            val viewModel: SyncViewModel = viewModel { SyncViewModel() }
//            val state by viewModel.uiState.collectAsStateWithLifecycle()
            Column (
                Modifier.fillMaxWidth().fillMaxHeight().background(color = MiuixTheme.colorScheme.surface)
            ){
                SmallTopAppBar(
                    title = "同步" ,
                    navigationIcon = {
                        IconButton(onClick = { onBackClick }) {
                            Icon(Lucide.MoveLeft, contentDescription = "返回")
                        }
                    },
                )
                LazyColumn(
                    contentPadding = PaddingValues(BadgerSpacing.cardPadding),
                ) {
                    item {
                        Card(
                            modifier = Modifier.height(100.dp).fillMaxWidth(),
                            colors = CardDefaults.defaultColors(
//                                color = BadgerDesignTheme.colors.successContainer
//                                color = BadgerDesignTheme.colors.warningContainer
                                color = MiuixTheme.colorScheme.errorContainer
                            ),
                        ){
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(BadgerSpacing.cardPadding),
                            ) {
                                Row(
                                    modifier = Modifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
//                                    Icon(
//                                        imageVector = Lucide.BadgeCheck,
//                                        contentDescription = "状态",
//                                        modifier = Modifier.size(BadgerSize.coverIconSize),
//                                        tint = BadgerDesignTheme.colors.onSuccessContainer
//                                    )
//                                    Icon(
//                                        imageVector = Lucide.BadgeAlert,
//                                        contentDescription = "状态",
//                                        modifier = Modifier.size(BadgerSize.coverIconSize),
//                                        tint = BadgerDesignTheme.colors.onWarningContainer
//                                    )
                                    Icon(
                                        imageVector = Lucide.BadgeX,
                                        contentDescription = "状态",
                                        modifier = Modifier.size(BadgerSize.coverIconSize),
                                        tint = MiuixTheme.colorScheme.error
                                    )

                                    Spacer(modifier = Modifier.width(BadgerSpacing.xs))

                                    Column {
//                                        Text(
//                                            text = "所有文件都是最新的",
//                                            color = BadgerDesignTheme.colors.onSuccessContainer,
//                                            style = MiuixTheme.textStyles.title3,
//                                            fontWeight = FontWeight.Bold,
//                                        )
//                                        Text(
//                                            text = "目前未发现未同步更改",
//                                            color = BadgerDesignTheme.colors.onSuccessContainer,
//                                            style = MiuixTheme.textStyles.body1
//                                        )

//                                        WARNING

//                                        Text(
//                                            text = "文件正在传输中...",
//                                            color = BadgerDesignTheme.colors.onWarningContainer,
//                                            style = MiuixTheme.textStyles.title3,
//                                            fontWeight = FontWeight.Bold,
//                                        )
//                                        Text(
//                                            text = "剩余 9999 个文件",
//                                            color = BadgerDesignTheme.colors.onWarningContainer,
//                                            minLines = 1,
//                                            style = MiuixTheme.textStyles.body1
//                                        )

                                        Text(
                                            text = "同步失败",
                                            color = MiuixTheme.colorScheme.error,
                                            style = MiuixTheme.textStyles.title3,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "无法连接至服务器",
                                            color = MiuixTheme.colorScheme.error,
                                            minLines = 1,
                                            style = MiuixTheme.textStyles.body1
                                        )
                                    }
                                }
                            }

                        }
                        Spacer(modifier = Modifier.height(BadgerSpacing.cardPadding))
                    }
                    item {
                        Card(
                            insideMargin = PaddingValues(BadgerSpacing.cardPadding)
                        ){
                            SyncDataCard("联系人",100,9999)
                            SyncDataCard("名片夹",200,9999)
                            SyncDataCard("标签",300,9999)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(BadgerSpacing.cardGap))
                    }

                    item {
                        Card {
                            BasicComponent(
                                title = "立即同步",
                                summary = "立刻执行双边同步",
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = Lucide.RefreshCw,
                                        contentDescription = "立即同步",
                                        tint = MiuixTheme.colorScheme.onBackground
                                    )
                                },
                                onClick = { /* 处理点击事件 */ }
                            )
                            BasicComponent(
                                title = "上传本地更改",
                                summary = "将本地 n 条数据上传至云端",
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = Lucide.ArrowBigUp,
                                        contentDescription = "上传本地更改",
                                        tint = MiuixTheme.colorScheme.onBackground
                                    )
                                },
                                onClick = { /* 处理点击事件 */ }
                            )
                            BasicComponent(
                                title = "下载远程更改",
                                summary = "将云端 n 条数据下载至本地",
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = Lucide.ArrowDownToLine,
                                        contentDescription = "推送本地更改",
                                        tint = MiuixTheme.colorScheme.onBackground
                                    )
                                },
                                onClick = { /* 处理点击事件 */ }
                            )
                            BasicComponent(
                                title = "重置并全量同步",
                                titleColor = BasicComponentDefaults.titleColor(
                                    color = MiuixTheme.colorScheme.error
                                ),
                                summary = "清空全部本地未上传的修改，并重新从云端拉取信息",
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = Lucide.Trash2,
                                        contentDescription = "重置并全量同步",
                                        tint = MiuixTheme.colorScheme.error
                                    )
                                },
                                onClick = { /* 处理点击事件，此方法须有弹窗说明 */ }
                            )
                        }
                    }


                    item {
                        Spacer(modifier = Modifier.height(500.dp))
                    }
                }
            }
        }

    }
}

@Composable
private fun SyncDataCard(subtitle: String, localNum: Int,hostNum: Int){
    Column {
        SmallTitle(
            text = subtitle,
            insideMargin = PaddingValues(0.dp, 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp) // 中间空隙
        ) {
            // 左边
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                colors = CardColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(BadgerSpacing.cardPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$localNum",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MiuixTheme.colorScheme.primary,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本地",
                        style = MiuixTheme.textStyles.subtitle
                    )
                }
            }

            // 右边
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                colors = CardColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(BadgerSpacing.cardPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$hostNum",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MiuixTheme.colorScheme.primary,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "云端",
                        style = MiuixTheme.textStyles.subtitle
                    )
                }
            }
        }
    }
}
