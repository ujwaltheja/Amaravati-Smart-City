package com.uc.amaravatismartcity.models

data class BuildingDefinition(
    val id: String,
    val category: BuildingCategory,
    val title: String,
    val assetPath: String,
    val cost: Long,
    val populationImpact: Int = 0,
    val happinessImpact: Int = 0,
    val sustainabilityImpact: Int = 0
)
