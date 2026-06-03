package com.uc.amaravatismartcity.game

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val money: Long = 52000,
    val population: Int = 120,
    val happiness: Int = 72,
    val water: Int = 95,
    val power: Int = 88,
    val waste: Int = 12, // 0-100 percentage of waste accumulation
    val pollution: Int = 14,
    val sustainabilityScore: Int = 58,
    val powerBalance: Int = 0,
    val waterBalance: Int = 0,
    val wasteBalance: Int = 0,
    val jobs: Int = 0,
    val housingCapacity: Int = 0,
    val taxIncome: Long = 0,
    val serviceCoverage: Int = 0,
    val emergencyDelay: Int = 0,
    val cityName: String = "Amaravati",
    val rank: String = "Rising Settlement",
    // Realism additions
    val dayTime: Float = 9.5f, // 0-24 hour float for day/night
    val trafficDensity: Int = 35, // 0-100
    val activeMissionIndex: Int = 0,
    val activeEmergency: String = "",
    val graphicsQuality: Int = 1,
    val lastIncomeTick: Long = 0L,
    val totalBuildings: Int = 3
)
