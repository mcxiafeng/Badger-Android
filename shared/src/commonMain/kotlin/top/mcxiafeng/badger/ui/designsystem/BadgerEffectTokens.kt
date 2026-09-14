package top.mcxiafeng.badger.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class BadgerMaterialSpec(
    
    val blurRadius: Dp,
    
    val saturation: Float,
    
    val tintLight: Color,
    
    val tintDark: Color,
) {
    fun tintFor(isDark: Boolean): Color = if (isDark) tintDark else tintLight
}

object BadgerMaterials {

    
    val ultraThin = BadgerMaterialSpec(
        blurRadius = 8.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.20f), tintDark = Color.Black.copy(alpha = 0.28f),
    )

    
    val thin = BadgerMaterialSpec(
        blurRadius = 12.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.28f), tintDark = Color.Black.copy(alpha = 0.38f),
    )

    
    val regular = BadgerMaterialSpec(
        blurRadius = 8.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.40f), tintDark = Color.Black.copy(alpha = 0.50f),
    )

    
    val thick = BadgerMaterialSpec(
        blurRadius = 20.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.48f), tintDark = Color.Black.copy(alpha = 0.58f),
    )

    
    val chrome = BadgerMaterialSpec(
        blurRadius = 24.dp, saturation = 1.8f,
        tintLight = Color.White.copy(alpha = 0.55f), tintDark = Color.Black.copy(alpha = 0.65f),
    )
}

@Immutable
data class BadgerGlassSpec(
    
    val base: BadgerMaterialSpec,
    
    val edgeWidth: Dp,
    
    val refractionHeight: Dp,
    
    val refractionAmount: Dp,
)

object BadgerGlass {

    
    val glassRegular = BadgerGlassSpec(
        base = BadgerMaterials.regular,
        edgeWidth = 12.dp,
        refractionHeight = 8.dp,
        refractionAmount = (-10).dp,
    )

    
    val glassClear = BadgerGlassSpec(
        base = BadgerMaterials.regular,
        edgeWidth = 16.dp,
        refractionHeight = 12.dp,
        refractionAmount = (-16).dp,
    )
}
