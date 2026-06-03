package com.uc.amaravatismartcity.models

import io.github.sceneview.math.Position

data class PlacedItem(
    val id: Long,
    val definition: BuildingDefinition,
    val position: Position,
    val rotationY: Float = 0f,
    val scale: Float = 1f
)
