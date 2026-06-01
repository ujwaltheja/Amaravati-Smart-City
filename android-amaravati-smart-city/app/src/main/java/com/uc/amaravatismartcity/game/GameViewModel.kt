package com.uc.amaravatismartcity.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun updateMoney(delta: Long) {
        _gameState.update { it.copy(money = it.money + delta) }
    }

    fun updatePopulation(delta: Int) {
        _gameState.update { it.copy(population = it.population + delta) }
    }

    fun updateHappiness(delta: Int) {
        _gameState.update { it.copy(happiness = (it.happiness + delta).coerceIn(0, 100)) }
    }

    fun updateWater(delta: Int) {
        _gameState.update { it.copy(water = (it.water + delta).coerceIn(0, 100)) }
    }

    fun updatePower(delta: Int) {
        _gameState.update { it.copy(power = (it.power + delta).coerceIn(0, 100)) }
    }

    fun updatePollution(delta: Int) {
        _gameState.update { it.copy(pollution = (it.pollution + delta).coerceIn(0, 100)) }
    }
    
    fun updateSustainability(delta: Int) {
        _gameState.update { it.copy(sustainabilityScore = (it.sustainabilityScore + delta).coerceIn(0, 100)) }
    }
}
