package com.joseleandro.scaniqo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal fun scaniqoShapes(dimensions: ScaniqoDimensions): Shapes = Shapes(
    extraSmall = RoundedCornerShape(dimensions.cornerXs),
    small = RoundedCornerShape(dimensions.cornerSm),
    medium = RoundedCornerShape(dimensions.cornerMd),
    large = RoundedCornerShape(dimensions.cornerLg),
    extraLarge = RoundedCornerShape(dimensions.cornerXl),
)
