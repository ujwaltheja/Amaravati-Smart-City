package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.PlacedItem


data class CitySimulationSnapshot(
    val balances: CityBalances,
    val roadGraph: RoadGraph,
    val serviceCoverage: Int,
    val totalBuildings: Int,
    val incomePerSecond: Long,
    val targetTraffic: Int,
    val powerDrift: Int,
    val waterDrift: Int,
    val wasteDrift: Int,
    val pollutionDrift: Int,
    val happinessDrift: Int,
    val populationGrowth: Int,
    val emergencyCoverage: Boolean
)

data class SimulationRolls(
    val resourceRoll: Float,
    val emergencyPenaltyRoll: Float,
    val populationGrowthRoll: Float,
    val populationDeclineRoll: Float,
    val eventRoll: Float
)

data class SimulationActions(
    val powerDelta: Int = 0,
    val waterDelta: Int = 0,
    val wasteDelta: Int = 0,
    val emergencyPenalty: Boolean = false,
    val trafficDelta: Int = 0,
    val pollutionDelta: Int = 0,
    val happinessDelta: Int = 0,
    val populationDelta: Int = 0,
    val triggerEvent: Boolean = false
)

data class IncomeTickDecision(
    val grantIncome: Boolean,
    val incomeAmount: Long,
    val updateLastIncomeTick: Boolean
)

fun hasIncomeTickElapsed(lastIncomeTick: Long, nowMillis: Long, minimumSpacingMillis: Long = 650L): Boolean {
    return nowMillis - lastIncomeTick > minimumSpacingMillis
}

fun resolveIncomeTick(
    snapshot: CitySimulationSnapshot,
    effDelta: Float,
    nowMillis: Long,
    lastIncomeTick: Long
): IncomeTickDecision {
    if (effDelta <= 0f) return IncomeTickDecision(grantIncome = false, incomeAmount = 0L, updateLastIncomeTick = false)
    val incomeAmount = (snapshot.incomePerSecond * effDelta).toLong().coerceAtLeast(1)
    val canGrant = hasIncomeTickElapsed(lastIncomeTick = lastIncomeTick, nowMillis = nowMillis)
    return IncomeTickDecision(
        grantIncome = canGrant,
        incomeAmount = incomeAmount,
        updateLastIncomeTick = canGrant
    )
}

fun applySimulationSnapshot(state: GameState, snapshot: CitySimulationSnapshot): GameState {
    return state.copy(
        powerBalance = snapshot.balances.power,
        waterBalance = snapshot.balances.water,
        wasteBalance = snapshot.balances.waste,
        jobs = snapshot.balances.jobs,
        housingCapacity = snapshot.balances.housing,
        taxIncome = snapshot.balances.taxIncome,
        serviceCoverage = snapshot.serviceCoverage,
        emergencyDelay = snapshot.roadGraph.emergencyDelay,
        trafficDensity = snapshot.roadGraph.averageCongestion.coerceIn(5, 95),
        totalBuildings = snapshot.totalBuildings
    )
}

fun resolveSimulationActions(
    current: GameState,
    snapshot: CitySimulationSnapshot,
    effDelta: Float,
    rolls: SimulationRolls
): SimulationActions {
    val resourceEnabled = rolls.resourceRoll < 0.3f * effDelta
    val powerDelta = if (resourceEnabled) snapshot.powerDrift else 0
    val waterDelta = if (resourceEnabled) snapshot.waterDrift else 0
    val wasteDelta = if (resourceEnabled) snapshot.wasteDrift else 0

    val emergencyPenalty = current.activeEmergency.isNotBlank() &&
        (!snapshot.emergencyCoverage || snapshot.roadGraph.emergencyDelay > 70) &&
        rolls.emergencyPenaltyRoll < 0.12f * effDelta

    val trafficDelta = ((snapshot.targetTraffic - current.trafficDensity) * 0.018f * effDelta).toInt()

    val populationDelta = when {
        snapshot.populationGrowth > 0 && rolls.populationGrowthRoll < 0.5f -> snapshot.populationGrowth
        snapshot.populationGrowth < 0 && rolls.populationDeclineRoll < 0.3f -> snapshot.populationGrowth
        else -> 0
    }

    return SimulationActions(
        powerDelta = powerDelta,
        waterDelta = waterDelta,
        wasteDelta = wasteDelta,
        emergencyPenalty = emergencyPenalty,
        trafficDelta = trafficDelta,
        pollutionDelta = snapshot.pollutionDrift,
        happinessDelta = snapshot.happinessDrift,
        populationDelta = populationDelta,
        triggerEvent = rolls.eventRoll < 0.012f * effDelta
    )
}

fun buildSimulationSnapshot(state: GameState, items: List<PlacedItem>): CitySimulationSnapshot {
    val balances = calculateBalances(items)
    val roadGraph = buildRoadGraph(items)
    val serviceCoverage = serviceCoveragePercent(items)
    val totalBuildings = items.count { it.definition.cost > 0 }
    val incomePerSecond = (balances.taxIncome / 12f + state.population * 0.28f + state.happiness * 1.2f).toLong()
    val targetTraffic = (roadGraph.averageCongestion + if (state.pollution > 55) 10 else 0).coerceAtMost(92)
    val powerDrift = if (balances.power >= 0) (if (state.power < 100) 1 else 0) else -1
    val waterDrift = if (balances.water >= 0) (if (state.water < 100) 1 else 0) else -1
    val wasteDrift = if (balances.waste > 0) 1 else if (balances.waste < 0 && state.waste > 0) -1 else 0
    val pollutionDrift = when {
        balances.pollution > 20 && state.pollution < 85 -> 1
        state.waste > 60 -> 1
        state.trafficDensity > 75 -> 1
        items.any { it.definition.category == BuildingCategory.GreenSpace } && state.pollution > 15 -> -1
        state.pollution > 20 -> -1
        else -> 0
    }
    val happinessDrift = when {
        state.power < 40 -> -2
        state.water < 40 -> -2
        state.waste > 70 -> -1
        state.pollution > 65 -> -1
        state.trafficDensity > 78 -> -1
        state.jobs < state.population / 3 -> -1
        state.serviceCoverage < 25 && totalBuildings > 8 -> -1
        state.happiness < 55 && state.water > 80 && state.power > 80 -> 1
        state.dayTime in 7f..19f -> 1
        else -> 0
    }
    val populationGrowth = if (
        state.happiness > 75 && state.power > 70 && state.water > 70 &&
        state.population < state.housingCapacity
    ) {
        1 + (totalBuildings / 8)
    } else if (state.happiness < 35 || state.power < 20 || state.water < 20) {
        -1
    } else {
        0
    }
    val emergencyCoverage = items.any { it.definition.category == BuildingCategory.Emergency } && serviceCoverage > 25

    return CitySimulationSnapshot(
        balances = balances,
        roadGraph = roadGraph,
        serviceCoverage = serviceCoverage,
        totalBuildings = totalBuildings,
        incomePerSecond = incomePerSecond,
        targetTraffic = targetTraffic,
        powerDrift = powerDrift,
        waterDrift = waterDrift,
        wasteDrift = wasteDrift,
        pollutionDrift = pollutionDrift,
        happinessDrift = happinessDrift,
        populationGrowth = populationGrowth,
        emergencyCoverage = emergencyCoverage
    )
}

