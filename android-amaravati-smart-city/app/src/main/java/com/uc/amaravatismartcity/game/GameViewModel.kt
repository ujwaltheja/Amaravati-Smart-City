package com.uc.amaravatismartcity.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uc.amaravatismartcity.db.RoomGameRepository
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
    private val repository: GameRepository = RoomGameRepository(application)
    private val placedItemIdGenerator = PlacedItemIdGenerator()

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

            repository.saveSnapshot(current, items, _activeGoal.value, _currentNews.value)
            _currentNews.value = "City data synchronized with secure archives."
        }
    }

    fun loadGame() {
        viewModelScope.launch {
            val savedSnapshot = repository.loadSnapshot() ?: return@launch
            _gameState.value = savedSnapshot.state
            if (savedSnapshot.activeGoal.isNotBlank()) _activeGoal.value = savedSnapshot.activeGoal
            if (savedSnapshot.currentNews.isNotBlank()) _currentNews.value = savedSnapshot.currentNews
        }
    }

    fun syncLoadedItems(entities: List<PlacedItemEntity>, catalog: List<BuildingDefinition>) {
        val reconstructed = repository.reconstructPlacedItems(entities, catalog)
        _placedItems.value = reconstructed
        placedItemIdGenerator.seedFrom(reconstructed)
        updateTotalBuildings(reconstructed.size)
    }

    fun getSavedItemsFlow(): Flow<List<PlacedItemEntity>> = repository.observePlacedItems()

    fun updateNews(news: String) {
        _currentNews.value = news
    }

    fun triggerRandomEvent() {
        val (emergency, event) = selectEvent(
            emergencyRoll = Random.nextFloat(),
            emergencyOptions = EmergencyType.entries,
            emergencyIndex = Random.nextInt(EmergencyType.entries.size),
            eventPool = eventPool,
            poolIndex = if (eventPool.isNotEmpty()) Random.nextInt(eventPool.size) else 0
        )
        _currentNews.value = event
        _events.update { (it + event).takeLast(4) }

        val outcome = resolveEventOutcome(event, emergency)
        if (outcome.happinessDelta != 0) updateHappiness(outcome.happinessDelta)
        if (outcome.trafficDelta != 0) updateTraffic(outcome.trafficDelta)
        if (outcome.moneyDelta != 0L) updateMoney(outcome.moneyDelta)
        if (outcome.powerDelta != 0) updatePower(outcome.powerDelta)
        if (outcome.pollutionDelta != 0) updatePollution(outcome.pollutionDelta)
        if (outcome.sustainabilityDelta != 0) updateSustainability(outcome.sustainabilityDelta)
        outcome.activeEmergency?.let { active -> _gameState.update { it.copy(activeEmergency = active) } }
        recalculateSmartScore()
    }

    fun checkGoals() {
        val state = _gameState.value
        val items = _placedItems.value
        val resolution = resolveGoalState(state, items)
        if (resolution.rank != state.rank) {
            _gameState.update { it.copy(rank = resolution.rank) }
            _currentNews.value = "CITY UPGRADE: Amaravati is now a ${resolution.rank}!"
        }

        _activeGoal.value = resolution.activeGoalText
        if (resolution.missionCompleted) {
            _gameState.update { it.copy(activeMissionIndex = resolution.nextMissionIndex) }
            resolution.completedMissionTitle?.let { title ->
                _currentNews.value = "MISSION COMPLETE: $title."
            }
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

        val id = placedItemIdGenerator.next()
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
        val snapshot = buildSimulationSnapshot(_gameState.value, items)
        _gameState.update { applySimulationSnapshot(it, snapshot) }
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
        val snapshot = buildSimulationSnapshot(current, items)

        updateDayTime(effDelta * 0.1f)

        val now = System.currentTimeMillis()
        val incomeDecision = resolveIncomeTick(
            snapshot = snapshot,
            effDelta = effDelta,
            nowMillis = now,
            lastIncomeTick = current.lastIncomeTick
        )
        if (incomeDecision.grantIncome) {
            addIncome(incomeDecision.incomeAmount, if (placedCount > 6) "Economy thriving" else "Daily commerce")
        }
        if (incomeDecision.updateLastIncomeTick) {
            _gameState.update { it.copy(lastIncomeTick = now) }
        }

        _gameState.update { applySimulationSnapshot(it, snapshot) }

        val actions = resolveSimulationActions(
            current = current,
            snapshot = snapshot,
            effDelta = effDelta,
            rolls = SimulationRolls(
                resourceRoll = Random.nextFloat(),
                emergencyPenaltyRoll = Random.nextFloat(),
                populationGrowthRoll = Random.nextFloat(),
                populationDeclineRoll = Random.nextFloat(),
                eventRoll = Random.nextFloat()
            )
        )

        if (actions.powerDelta != 0) updatePower(actions.powerDelta)
        if (actions.waterDelta != 0) updateWater(actions.waterDelta)
        if (actions.wasteDelta != 0) updateWaste(actions.wasteDelta)
        if (actions.emergencyPenalty) {
            updateHappiness(-1)
            updateMoney(-220)
        }
        if (actions.trafficDelta != 0) updateTraffic(actions.trafficDelta)
        if (actions.pollutionDelta != 0) updatePollution(actions.pollutionDelta)
        if (actions.happinessDelta != 0) updateHappiness(actions.happinessDelta)
        if (actions.populationDelta != 0) updatePopulation(actions.populationDelta)

        recalculateSmartScore()
        checkGoals()

        if (actions.triggerEvent) {
            triggerRandomEvent()
        }
    }
}
