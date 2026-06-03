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
    val cityName: String,
    val rank: String,
    val dayTime: Float
)
