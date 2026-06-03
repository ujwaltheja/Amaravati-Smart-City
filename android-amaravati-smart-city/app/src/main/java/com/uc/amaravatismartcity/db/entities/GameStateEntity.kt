package com.uc.amaravatismartcity.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_state")
data class GameStateEntity(
    @PrimaryKey val id: Int = 0, // Single row for global state
    val money: Long,
    val population: Int,
    val happiness: Int,
    val water: Int,
    val power: Int,
    val waste: Int,
    val pollution: Int,
    val sustainabilityScore: Int,
    val powerBalance: Int = 0,
    val waterBalance: Int = 0,
    val wasteBalance: Int = 0,
    val jobs: Int = 0,
    val housingCapacity: Int = 0,
    val taxIncome: Long = 0,
    val serviceCoverage: Int = 0,
    val emergencyDelay: Int = 0,
    val cityName: String,
    val rank: String,
    val dayTime: Float,
    val trafficDensity: Int = 35,
    val activeMissionIndex: Int = 0,
    val activeEmergency: String = "",
    val graphicsQuality: Int = 1,
    val totalBuildings: Int = 0,
    val activeGoal: String = "",
    val currentNews: String = ""
)
