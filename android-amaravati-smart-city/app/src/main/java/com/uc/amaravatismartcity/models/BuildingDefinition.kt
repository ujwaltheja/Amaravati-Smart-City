package com.uc.amaravatismartcity.models

data class BuildingDefinition(
    val id: String,
    val category: BuildingCategory,
    val title: String,
    val assetPath: String,
    val cost: Long,
    val populationImpact: Int = 0,
    val happinessImpact: Int = 0,
    val sustainabilityImpact: Int = 0,
    val powerImpact: Int = 0,      // + production, - consumption
    val waterImpact: Int = 0,      // + production, - consumption
    val wasteImpact: Int = 0,       // + generation, - collection/management
    val pollutionImpact: Int = 0,   // + generation, - cleaning
    val unlockPopulation: Int = 0  // 0 means unlocked by default
    )
