package com.uc.amaravatismartcity.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uc.amaravatismartcity.db.GameDatabase
import com.uc.amaravatismartcity.db.entities.GameStateEntity
import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import io.github.sceneview.math.Position
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val db = GameDatabase.getDatabase(application)
    private val dao = db.gameDao()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _placedItems = MutableStateFlow<List<PlacedItem>>(emptyList())
    val placedItems: StateFlow<List<PlacedItem>> = _placedItems.asStateFlow()

    private val _currentNews = MutableStateFlow("Welcome to Amaravati. The Krishna riverfront awaits your vision.")
    val currentNews: StateFlow<String> = _currentNews.asStateFlow()

    private val _activeGoal = MutableStateFlow("Establish core infrastructure: Place 2 roads and a residential block.")
    val activeGoal: StateFlow<String> = _activeGoal.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _simSpeed = MutableStateFlow(1f)
    val simSpeed: StateFlow<Float> = _simSpeed.asStateFlow()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    private val eventPool = listOf(
        "Heavy monsoon rain: Traffic slowed, happiness -4.",
        "Tech investor visit: Commercial demand up.",
        "Power fluctuation in Sector 3. Engineers dispatched.",
        "New bus route proposed - add transport for +happiness.",
        "Riverfront festival boosted tourism income.",
        "Industrial spill risk: Pollution rising.",
        "Citizens praise new green corridor.",
        "School exam results high - education impact +3.",
        "FIRE BREAKOUT in Sector 1! Emergency services needed.",
        "Medical emergency reported in Residential Block B."
    )

    init {
        loadGame()
    }

    fun saveGame() {
        viewModelScope.launch {
            val current = _gameState.value
            val items = _placedItems.value

            dao.saveGameState(
                GameStateEntity(
                    money = current.money,
                    population = current.population,
                    happiness = current.happiness,
                    water = current.water,
                    power = current.power,
                    waste = current.waste,
                    pollution = current.pollution,
                    sustainabilityScore = current.sustainabilityScore,
                    cityName = current.cityName,
                    rank = current.rank,
                    dayTime = current.dayTime,
                    trafficDensity = current.trafficDensity,
                    totalBuildings = current.totalBuildings,
                    activeGoal = _activeGoal.value,
                    currentNews = _currentNews.value
                )
            )

            dao.clearPlacedItems()
            dao.insertPlacedItems(
                items.map {
                    PlacedItemEntity(
                        id = it.id,
                        buildingId = it.definition.id,
                        posX = it.position.x,
                        posY = it.position.y,
                        posZ = it.position.z,
                        rotationY = it.rotationY,
                        scale = it.scale
                    )
                }
            )
            _currentNews.value = "City data synchronized with secure archives."
        }
    }

    fun loadGame() {
        viewModelScope.launch {
            val savedState = dao.getGameState()
            if (savedState != null) {
                _gameState.update {
                    it.copy(
                        money = savedState.money,
                        population = savedState.population,
                        happiness = savedState.happiness,
                        water = savedState.water,
                        power = savedState.power,
                        waste = savedState.waste,
                        pollution = savedState.pollution,
                        sustainabilityScore = savedState.sustainabilityScore,
                        cityName = savedState.cityName,
                        rank = savedState.rank,
                        dayTime = savedState.dayTime,
                        trafficDensity = savedState.trafficDensity,
                        totalBuildings = savedState.totalBuildings
                    )
                }
                if (savedState.activeGoal.isNotBlank()) _activeGoal.value = savedState.activeGoal
                if (savedState.currentNews.isNotBlank()) _currentNews.value = savedState.currentNews
            }
        }
    }

    fun syncLoadedItems(entities: List<PlacedItemEntity>, catalog: List<BuildingDefinition>) {
        val reconstructed = entities.mapNotNull { entity ->
            val def = catalog.firstOrNull { it.id == entity.buildingId }
            def?.let {
                PlacedItem(
                    id = entity.id,
                    definition = it,
                    position = Position(entity.posX, entity.posY, entity.posZ),
                    rotationY = entity.rotationY,
                    scale = entity.scale
                )
            }
        }
        _placedItems.value = reconstructed
        updateTotalBuildings(reconstructed.size)
    }

    fun getSavedItemsFlow(): Flow<List<PlacedItemEntity>> = dao.getAllPlacedItems()

    fun updateNews(news: String) {
        _currentNews.value = news
    }

    fun triggerRandomEvent() {
        val event = eventPool.random()
        _currentNews.value = event
        _events.update { (it + event).takeLast(4) }

        when {
            event.contains("monsoon") || event.contains("Traffic") -> {
                updateHappiness(-4)
                updateTraffic(12)
            }
            event.contains("investor") || event.contains("festival") -> {
                updateMoney(1800)
                updateHappiness(5)
            }
            event.contains("Power") -> updatePower(-8)
            event.contains("spill") || event.contains("Pollution") -> {
                updatePollution(7)
                updateHappiness(-2)
            }
            event.contains("green") -> {
                updateHappiness(6)
                updateSustainability(4)
            }
            event.contains("education") -> {
                updateHappiness(3)
                updateSustainability(2)
            }
        }
        recalculateSmartScore()
    }

    fun checkGoals(state: GameState) {
        val newRank = when {
            state.population >= 4200 -> "Smart Metropolis"
            state.population >= 1800 -> "Future City"
            state.population >= 850 -> "Major Township"
            state.population >= 380 -> "Growing District"
            else -> "Rising Settlement"
        }
        if (newRank != state.rank) {
            _gameState.update { it.copy(rank = newRank) }
            _currentNews.value = "CITY UPGRADE: Amaravati is now a $newRank!"
        }

        val goal = _activeGoal.value
        if (state.population >= 280 && goal.contains("core infrastructure")) {
            _activeGoal.value = "Improve liveability: Reach 85 Happiness with parks and services."
            _currentNews.value = "MILESTONE: Foundational infrastructure in place."
        } else if (state.happiness >= 85 && goal.contains("85 Happiness")) {
            _activeGoal.value = "Expand public transport and reduce pollution below 25%."
            _currentNews.value = "Citizens are thriving!"
        } else if (state.pollution < 25 && state.trafficDensity < 55 && goal.contains("pollution")) {
            _activeGoal.value = "Grow to 1200 citizens while maintaining sustainability."
            _currentNews.value = "Clean air milestone achieved."
        }
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setSimSpeed(speed: Float) {
        _simSpeed.value = speed.coerceIn(0.5f, 4f)
    }

    fun updateMoney(delta: Long) {
        _gameState.update { it.copy(money = max(0, it.money + delta)) }
    }

    fun updatePopulation(delta: Int) {
        _gameState.update { it.copy(population = max(20, it.population + delta)) }
    }

    fun updateHappiness(delta: Int) {
        _gameState.update { it.copy(happiness = (it.happiness + delta).coerceIn(10, 100)) }
    }

    fun updateWater(delta: Int) {
        _gameState.update { it.copy(water = (it.water + delta).coerceIn(20, 100)) }
    }

    fun updatePower(delta: Int) {
        _gameState.update { it.copy(power = (it.power + delta).coerceIn(0, 100)) }
    }

    fun updateWaste(delta: Int) {
        _gameState.update { it.copy(waste = (it.waste + delta).coerceIn(0, 100)) }
    }

    fun updatePollution(delta: Int) {
        _gameState.update { it.copy(pollution = (it.pollution + delta).coerceIn(0, 100)) }
    }

    fun addPlacedItem(item: PlacedItem) {
        _placedItems.update { it + item }
        updateTotalBuildings(_placedItems.value.size)
    }

    fun removePlacedItem(item: PlacedItem) {
        _placedItems.update { list -> list.filter { it.id != item.id } }
        updateTotalBuildings(_placedItems.value.size)
    }

    fun removeLastPlacedItem() {
        _placedItems.update { if (it.isNotEmpty()) it.dropLast(1) else it }
        updateTotalBuildings(_placedItems.value.size)
    }

    fun updateSustainability(delta: Int) {
        _gameState.update {
            it.copy(sustainabilityScore = (it.sustainabilityScore + delta).coerceIn(10, 100))
        }
    }

    fun updateTraffic(delta: Int) {
        _gameState.update {
            it.copy(trafficDensity = (it.trafficDensity + delta).coerceIn(5, 95))
        }
    }

    fun updateDayTime(deltaHours: Float) {
        _gameState.update {
            it.copy(dayTime = (it.dayTime + deltaHours) % 24f)
        }
    }

    fun updateTotalBuildings(count: Int) {
        _gameState.update { it.copy(totalBuildings = count) }
    }

    fun recalculateSmartScore() {
        _gameState.update {
            val base = it.happiness * 0.35 + (100 - it.pollution) * 0.30 + it.sustainabilityScore * 0.20
            val trafficFactor = (100 - min(it.trafficDensity, 70)) * 0.15
            val score = (base + trafficFactor).toInt()
            it.copy(sustainabilityScore = score.coerceIn(15, 100))
        }
    }

    fun addIncome(amount: Long, reason: String? = null) {
        updateMoney(amount)
        if (!reason.isNullOrBlank() && Random.nextFloat() < 0.6f) {
            _currentNews.value = "+₹$amount - $reason"
        }
    }

    fun advanceSimulation(deltaSeconds: Float) {
        if (_isPaused.value) return

        val speed = _simSpeed.value
        val effDelta = deltaSeconds * speed
        val current = _gameState.value
        val items = _placedItems.value
        val placedCount = items.size

        updateDayTime(effDelta * 0.1f)

        val incomePerSec = (current.population * 0.9 + placedCount * 28 + current.happiness * 1.8).toLong()
        if (effDelta > 0) {
            val income = (incomePerSec * effDelta).toLong().coerceAtLeast(1)
            if (System.currentTimeMillis() - current.lastIncomeTick > 650) {
                addIncome(income, if (placedCount > 6) "Economy thriving" else "Daily commerce")
                _gameState.update { it.copy(lastIncomeTick = System.currentTimeMillis()) }
            }
        }

        val producers = items.filter { it.definition.powerImpact > 0 || it.definition.waterImpact > 0 }
        val consumers = items.filter { it.definition.powerImpact < 0 || it.definition.waterImpact < 0 }

        var connectedPowerCons = 0
        var connectedWaterCons = 0
        val totalPowerCons = consumers.sumOf { it.definition.powerImpact.coerceAtMost(0) }
        val totalWaterCons = consumers.sumOf { it.definition.waterImpact.coerceAtMost(0) }

        consumers.forEach { consumer ->
            val isPowered = producers.any { p ->
                p.definition.powerImpact > 0 && distSq(p.position, consumer.position) < 144f
            }
            val hasWater = producers.any { p ->
                p.definition.waterImpact > 0 && distSq(p.position, consumer.position) < 144f
            }

            if (isPowered) connectedPowerCons += consumer.definition.powerImpact
            if (hasWater) connectedWaterCons += consumer.definition.waterImpact
        }

        val gridEff = if (totalPowerCons < 0) connectedPowerCons.toFloat() / totalPowerCons else 1f
        val waterEff = if (totalWaterCons < 0) connectedWaterCons.toFloat() / totalWaterCons else 1f

        val netPower = items.sumOf { it.definition.powerImpact }
        val netWater = items.sumOf { it.definition.waterImpact }
        val netWaste = items.sumOf { it.definition.wasteImpact }

        val powerDrift = if (netPower >= 0 && gridEff > 0.75f) (if (current.power < 100) 1 else 0) else -1
        val waterDrift = if (netWater >= 0 && waterEff > 0.75f) (if (current.water < 100) 1 else 0) else -1
        val wasteDrift = if (netWaste > 0) 1 else if (netWaste < 0 && current.waste > 0) -1 else 0

        if (Random.nextFloat() < 0.3f * effDelta) {
            if (powerDrift != 0) updatePower(powerDrift)
            if (waterDrift != 0) updateWater(waterDrift)
            if (wasteDrift != 0) updateWaste(wasteDrift)
        }

        if (_currentNews.value.contains("FIRE") || _currentNews.value.contains("Medical emergency")) {
            val hasCoverage = when {
                _currentNews.value.contains("FIRE") -> items.any {
                    it.definition.id.contains("police") || it.definition.category == BuildingCategory.Emergency
                }
                else -> items.any {
                    it.definition.id.contains("hospital") || it.definition.id.contains("ambulance")
                }
            }
            if (!hasCoverage && Random.nextFloat() < 0.1f * effDelta) {
                updateHappiness(-1)
                updateMoney(-150)
            }
        }

        val targetTraffic = (28 + placedCount * 3 + if (current.pollution > 55) 18 else 0).coerceAtMost(92)
        val trafficDrift = ((targetTraffic - current.trafficDensity) * 0.018f * effDelta).toInt()
        if (trafficDrift != 0) updateTraffic(trafficDrift)

        val pollutionDrift = when {
            items.any { it.definition.id.contains("factory") } && current.pollution < 85 -> 1
            current.waste > 60 -> 1
            current.trafficDensity > 75 -> 1
            current.pollution > 20 && Random.nextFloat() < 0.2f -> -1
            else -> 0
        }
        if (pollutionDrift != 0) updatePollution(pollutionDrift)

        val happyDrift = when {
            current.power < 40 -> -2
            current.water < 40 -> -2
            current.waste > 70 -> -1
            current.pollution > 65 -> -1
            current.trafficDensity > 78 -> -1
            gridEff < 0.65f -> -1
            current.happiness < 55 && current.water > 80 && current.power > 80 -> 1
            current.dayTime in 7f..19f && Random.nextFloat() < 0.2f -> 1
            else -> 0
        }
        if (happyDrift != 0) updateHappiness(happyDrift)

        if (current.happiness > 75 && current.power > 70 && current.water > 70 && current.population < 8000 && Random.nextFloat() < 0.5f) {
            updatePopulation(1 + (placedCount / 8))
        } else if ((current.happiness < 35 || current.power < 20 || current.water < 20) && Random.nextFloat() < 0.3f) {
            updatePopulation(-1)
        }

        recalculateSmartScore()
        checkGoals(current.copy(population = current.population))

        if (Random.nextFloat() < 0.012f * effDelta) {
            triggerRandomEvent()
        }
    }

    private fun distSq(p1: Position, p2: Position): Float {
        val dx = p1.x - p2.x
        val dz = p1.z - p2.z
        return dx * dx + dz * dz
    }
}
