package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ThemeController

@Immutable
data class BadgerDesignColors(
    // 成功色系
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    // 警告色系
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
) {
    companion object {
        val Light = BadgerDesignColors(
            success = Color(0xFF16A34A),
            onSuccess = Color.White,
            successContainer = Color(0xFFDCFCE7),
            onSuccessContainer = Color(0xFF16A34A),

            warning = Color(0xFFCA8A04),
            onWarning = Color.White,
            warningContainer = Color(0xFFFEF9C3),
            onWarningContainer = Color(0xFFCA8A04)
        )

        val Dark = BadgerDesignColors(
            success = Color(0xFF22C55E),
            onSuccess = Color.Black,
            successContainer = Color(0xFF064E3B),
            onSuccessContainer = Color(0xFFBBF7D0),

            warning = Color(0xFFEAB308),
            onWarning = Color.Black,
            warningContainer = Color(0xFF422E00),
            onWarningContainer = Color(0xFFFDE68A)
        )
    }
}

private val LocalBadgerDesignColors = staticCompositionLocalOf { BadgerDesignColors.Light }

object BadgerDesignTheme {
    val colors: BadgerDesignColors
        @Composable
        get() = LocalBadgerDesignColors.current
}

@Composable
fun ProvideBadgerDesignColors(
    controller: ThemeController,
    content: @Composable () -> Unit
) {
    val isDark = controller.isDark
    val colorSet = if (isDark == true) BadgerDesignColors.Dark else BadgerDesignColors.Light
    CompositionLocalProvider(
        LocalBadgerDesignColors provides colorSet
    ) {
        content()
    }
}