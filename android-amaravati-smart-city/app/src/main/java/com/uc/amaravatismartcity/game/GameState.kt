package com.uc.amaravatismartcity.game

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val money: Long = 50000,
    val population: Int = 100,
    val happiness: Int = 75, // Percentage
    val water: Int = 100, // Supply percentage
    val power: Int = 100, // Supply percentage
    val pollution: Int = 10, // Percentage
    val sustainabilityScore: Int = 50, // 0-100
    val cityName: String = "Amaravati",
    val rank: String = "Rising Settlement"
)
