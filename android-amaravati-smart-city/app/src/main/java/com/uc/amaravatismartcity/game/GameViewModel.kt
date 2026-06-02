package com.uc.amaravatismartcity.game

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _currentNews = MutableStateFlow("Welcome to Amaravati! Start building your smart city.")
    val currentNews: StateFlow<String> = _currentNews.asStateFlow()

    private val _activeGoal = MutableStateFlow("Build a Residential area to house 100 people.")
    val activeGoal: StateFlow<String> = _activeGoal.asStateFlow()

    fun updateNews(news: String) {
        _currentNews.value = news
    }

    fun checkGoals(state: GameState) {
        if (state.population >= 100 && _activeGoal.value.contains("100 people")) {
            _activeGoal.value = "Reach 80% Happiness by adding Green Spaces."
            _currentNews.value = "GOAL REACHED: Population milestone met!"
        } else if (state.happiness >= 80 && _activeGoal.value.contains("80% Happiness")) {
            _activeGoal.value = "Establish a Government Node for better administration."
            _currentNews.value = "GOAL REACHED: Citizens are happy!"
        }
    }

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
        _gameState.update {
            val newScore = (it.sustainabilityScore + delta).coerceIn(0, 100)
            it.copy(sustainabilityScore = newScore)
        }
    }

    fun recalculateSmartScore() {
        _gameState.update {
            val score = (it.happiness * 0.4 + (100 - it.pollution) * 0.4 + it.sustainabilityScore * 0.2).toInt()
            it.copy(sustainabilityScore = score.coerceIn(0, 100))
        }
    }
}
