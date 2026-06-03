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
                    powerBalance = current.powerBalance,
                    waterBalance = current.waterBalance,
                    wasteBalance = current.wasteBalance,
                    jobs = current.jobs,
                    housingCapacity = current.housingCapacity,
                    taxIncome = current.taxIncome,
                    serviceCoverage = current.serviceCoverage,
                    emergencyDelay = current.emergencyDelay,
                    cityName = current.cityName,
                    rank = current.rank,
                    dayTime = current.dayTime,
                    trafficDensity = current.trafficDensity,
                    activeMissionIndex = current.activeMissionIndex,
                    activeEmergency = current.activeEmergency,
                    graphicsQuality = current.graphicsQuality,
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
                        powerBalance = savedState.powerBalance,
                        waterBalance = savedState.waterBalance,
                        wasteBalance = savedState.wasteBalance,
                        jobs = savedState.jobs,
                        housingCapacity = savedState.housingCapacity,
                        taxIncome = savedState.taxIncome,
                        serviceCoverage = savedState.serviceCoverage,
                        emergencyDelay = savedState.emergencyDelay,
                        cityName = savedState.cityName,
                        rank = savedState.rank,
                        dayTime = savedState.dayTime,
                        trafficDensity = savedState.trafficDensity,
                        activeMissionIndex = savedState.activeMissionIndex,
                        activeEmergency = savedState.activeEmergency,
                        graphicsQuality = savedState.graphicsQuality,
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
        val emergency = EmergencyType.entries.random()
        val event = if (Random.nextFloat() < 0.45f) emergency.displayName else eventPool.random()
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
            event == emergency.displayName -> {
                _gameState.update { it.copy(activeEmergency = emergency.displayName) }
                updateHappiness(-2)
                if (emergency == EmergencyType.Spill) updatePollution(8)
                if (emergency == EmergencyType.Outage) updatePower(-10)
            }
        }
        recalculateSmartScore()
    }

    fun checkGoals(state: GameState) {
        val items = _placedItems.value
        val graph = buildRoadGraph(items)
        val newRank = rankForPopulation(state.population)
        if (newRank != state.rank) {
            _gameState.update { it.copy(rank = newRank) }
            _currentNews.value = "CITY UPGRADE: Amaravati is now a $newRank!"
        }

        val missionIndex = state.activeMissionIndex.coerceIn(0, cityMissions.lastIndex)
        val mission = cityMissions[missionIndex]
        _activeGoal.value = "${mission.title}: ${mission.description}"
        if (mission.isComplete(state, items, graph)) {
            val next = (missionIndex + 1).coerceAtMost(cityMissions.lastIndex)
            _gameState.update { it.copy(activeMissionIndex = next) }
            _activeGoal.value = "${cityMissions[next].title}: ${cityMissions[next].description}"
            _currentNews.value = "MISSION COMPLETE: ${mission.title}."
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

    fun placeBuilding(definition: BuildingDefinition, position: Position, rotationY: Float): Boolean {
        val pos = snapToGrid(position)
        val state = _gameState.value
        if (state.money < definition.cost) {
            _currentNews.value = "Insufficient funds for ${definition.title}."
            return false
        }
        if (state.population < definition.unlockPopulation) {
            _currentNews.value = "${definition.title} unlocks at ${definition.unlockPopulation} citizens."
            return false
        }
        if (!canPlaceOnGrid(definition, pos, _placedItems.value)) {
            _currentNews.value = "Placement blocked. Choose a clear grid tile."
            return false
        }

        val id = System.currentTimeMillis()
        addPlacedItem(PlacedItem(id = id, definition = definition, position = pos, rotationY = rotationY))
        updateMoney(-definition.cost)
        updatePopulation(definition.populationImpact)
        updateHappiness(definition.happinessImpact)
        updateSustainability(definition.sustainabilityImpact)
        refreshCitySystems()
        _currentNews.value = "${definition.title} placed on grid."
        return true
    }

    fun removePlacedItem(item: PlacedItem) {
        _placedItems.update { list -> list.filter { it.id != item.id } }
        updateTotalBuildings(_placedItems.value.size)
    }

    fun bulldoze(item: PlacedItem): Long {
        removePlacedItem(item)
        val refund = (item.definition.cost * if (item.definition.cost >= 8000) 0.35f else 0.5f).toLong()
        updateMoney(refund)
        updatePopulation(-item.definition.populationImpact)
        updateHappiness((-item.definition.happinessImpact / 2).coerceAtMost(0))
        refreshCitySystems()
        _currentNews.value = "${item.definition.title} removed. Refunded ₹${refund}."
        return refund
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

    fun setGraphicsQuality(quality: Int) {
        _gameState.update { it.copy(graphicsQuality = quality.coerceIn(0, 2)) }
        _currentNews.value = when (quality.coerceIn(0, 2)) {
            0 -> "Graphics set to Battery Saver."
            2 -> "Graphics set to High Detail."
            else -> "Graphics set to Balanced."
        }
    }

    fun clearEmergency() {
        val active = _gameState.value.activeEmergency
        if (active.isBlank()) {
            _currentNews.value = "No active emergency."
            return
        }
        val graph = buildRoadGraph(_placedItems.value)
        val hasEmergencyService = _placedItems.value.any { it.definition.category == BuildingCategory.Emergency }
        val recoveryCost = if (graph.emergencyDelay > 70) 1400L else 650L
        if (!hasEmergencyService) {
            updateHappiness(-3)
            _currentNews.value = "Emergency response failed. Build fire, police, or hospital coverage."
            return
        }
        updateMoney(-recoveryCost)
        updateHappiness(if (graph.emergencyDelay < 55) 4 else -1)
        _gameState.update { it.copy(activeEmergency = "") }
        _currentNews.value = "$active resolved. Recovery cost ₹$recoveryCost."
    }

    fun refreshCitySystems() {
        val items = _placedItems.value
        val balances = calculateBalances(items)
        val graph = buildRoadGraph(items)
        val coverage = serviceCoveragePercent(items)
        _gameState.update {
            it.copy(
                powerBalance = balances.power,
                waterBalance = balances.water,
                wasteBalance = balances.waste,
                jobs = balances.jobs,
                housingCapacity = balances.housing,
                taxIncome = balances.taxIncome,
                serviceCoverage = coverage,
                emergencyDelay = graph.emergencyDelay,
                trafficDensity = graph.averageCongestion.coerceIn(5, 95),
                totalBuildings = items.count { placed -> placed.definition.cost > 0 }
            )
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
        val balances = calculateBalances(items)
        val roadGraph = buildRoadGraph(items)

        updateDayTime(effDelta * 0.1f)

        val incomePerSec = (balances.taxIncome / 12f + current.population * 0.28f + current.happiness * 1.2f).toLong()
        if (effDelta > 0) {
            val income = (incomePerSec * effDelta).toLong().coerceAtLeast(1)
            if (System.currentTimeMillis() - current.lastIncomeTick > 650) {
                addIncome(income, if (placedCount > 6) "Economy thriving" else "Daily commerce")
                _gameState.update { it.copy(lastIncomeTick = System.currentTimeMillis()) }
            }
        }

        _gameState.update {
            it.copy(
                powerBalance = balances.power,
                waterBalance = balances.water,
                wasteBalance = balances.waste,
                jobs = balances.jobs,
                housingCapacity = balances.housing,
                taxIncome = balances.taxIncome,
                serviceCoverage = serviceCoveragePercent(items),
                emergencyDelay = roadGraph.emergencyDelay,
                trafficDensity = roadGraph.averageCongestion.coerceIn(5, 95),
                totalBuildings = items.count { placed -> placed.definition.cost > 0 }
            )
        }

        val powerDrift = if (balances.power >= 0) (if (current.power < 100) 1 else 0) else -1
        val waterDrift = if (balances.water >= 0) (if (current.water < 100) 1 else 0) else -1
        val wasteDrift = if (balances.waste > 0) 1 else if (balances.waste < 0 && current.waste > 0) -1 else 0

        if (Random.nextFloat() < 0.3f * effDelta) {
            if (powerDrift != 0) updatePower(powerDrift)
            if (waterDrift != 0) updateWater(waterDrift)
            if (wasteDrift != 0) updateWaste(wasteDrift)
        }

        if (current.activeEmergency.isNotBlank()) {
            val hasCoverage = items.any { it.definition.category == BuildingCategory.Emergency } && current.serviceCoverage > 25
            if ((!hasCoverage || roadGraph.emergencyDelay > 70) && Random.nextFloat() < 0.12f * effDelta) {
                updateHappiness(-1)
                updateMoney(-220)
            }
        }

        val targetTraffic = (roadGraph.averageCongestion + if (current.pollution > 55) 10 else 0).coerceAtMost(92)
        val trafficDrift = ((targetTraffic - current.trafficDensity) * 0.018f * effDelta).toInt()
        if (trafficDrift != 0) updateTraffic(trafficDrift)

        val pollutionDrift = when {
            balances.pollution > 20 && current.pollution < 85 -> 1
            current.waste > 60 -> 1
            current.trafficDensity > 75 -> 1
            items.any { it.definition.category == BuildingCategory.GreenSpace } && current.pollution > 15 -> -1
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
            current.jobs < current.population / 3 -> -1
            current.serviceCoverage < 25 && placedCount > 8 -> -1
            current.happiness < 55 && current.water > 80 && current.power > 80 -> 1
            current.dayTime in 7f..19f && Random.nextFloat() < 0.2f -> 1
            else -> 0
        }
        if (happyDrift != 0) updateHappiness(happyDrift)

        if (current.happiness > 75 && current.power > 70 && current.water > 70 && current.population < current.housingCapacity && Random.nextFloat() < 0.5f) {
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
}
