package top.mcxiafeng.badger.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import top.mcxiafeng.badger.shared.R

@Composable
actual fun AppIcon(modifier: Modifier) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher),
        contentDescription = "Badger",
        modifier = modifier,
    )
}
