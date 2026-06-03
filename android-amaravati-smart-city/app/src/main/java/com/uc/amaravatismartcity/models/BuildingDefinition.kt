package com.uc.amaravatismartcity.models

data class BuildingDefinition(
    val id: String,
    val category: BuildingCategory,
    val title: String,
    val assetPath: String,
    val cost: Long,
    val width: Int = 1,
    val depth: Int = 1,
    val roadUpgrade: RoadUpgrade = RoadUpgrade.None,
    val populationImpact: Int = 0,
    val happinessImpact: Int = 0,
    val sustainabilityImpact: Int = 0,
    val powerImpact: Int = 0,      // + production, - consumption
    val waterImpact: Int = 0,      // + production, - consumption
    val wasteImpact: Int = 0,       // + generation, - collection/management
    val pollutionImpact: Int = 0,   // + generation, - cleaning
    val jobs: Int = 0,
    val housingCapacity: Int = 0,
    val taxIncome: Long = 0,
    val serviceCoverage: Int = 0,
    val unlockPopulation: Int = 0  // 0 means unlocked by default
)

enum class RoadUpgrade(
    val displayName: String,
    val capacity: Int,
    val speed: Float,
    val pollutionMultiplier: Float,
    val unlockPopulation: Int = 0
) {
    None("None", 0, 0f, 1f),
    Basic("Basic Road", 45, 1f, 1f),
    Smart("Smart Road", 70, 1.18f, 0.85f),
    BusLane("Bus Lane", 95, 1.05f, 0.72f, unlockPopulation = 800),
    Flyover("Flyover", 130, 1.35f, 0.92f, unlockPopulation = 1200)
}
