 package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import com.uc.amaravatismartcity.models.RoadUpgrade
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitySimulationTest {
    private val park = BuildingDefinition(
        id = "park",
        category = BuildingCategory.GreenSpace,
        title = "Park",
        assetPath = "models/Roads and Bridges/tile-low.glb",
        cost = 1200,
        happinessImpact = 12,
        sustainabilityImpact = 15,
        pollutionImpact = -10,
        serviceCoverage = 5
    )

    private val road = BuildingDefinition(
        id = "road-basic",
        category = BuildingCategory.Infrastructure,
        title = "Basic Road",
        assetPath = "models/Roads and Bridges/road-straight.glb",
        cost = 500,
        roadUpgrade = RoadUpgrade.Basic
    )

    private val hospital = BuildingDefinition(
        id = "hospital",
        category = BuildingCategory.Emergency,
        title = "City Hospital",
        assetPath = "models/Cars/ambulance.glb",
        cost = 7500,
        width = 3,
        depth = 2,
        jobs = 85,
        happinessImpact = 20,
        powerImpact = -30,
        waterImpact = -25,
        wasteImpact = 10,
        serviceCoverage = 9,
        unlockPopulation = 300
    )

    @Test
    fun buildSimulationSnapshot_aggregates_expected_drifts() {
        val state = GameState(
            population = 600,
            happiness = 82,
            water = 88,
            power = 91,
            waste = 20,
            pollution = 18,
            trafficDensity = 30,
            jobs = 200,
            housingCapacity = 650,
            dayTime = 12f
        )
        val items = listOf(
            PlacedItem(1L, park, Position(0f, 0.02f, 0f)),
            PlacedItem(2L, road, Position(2f, 0.02f, 0f)),
            PlacedItem(3L, hospital, Position(4f, 0.02f, 0f))
        )

        val snapshot = buildSimulationSnapshot(state, items)

        assertEquals(-1, snapshot.powerDrift)
        assertEquals(-1, snapshot.waterDrift)
        assertEquals(1, snapshot.wasteDrift)
        assertEquals(-1, snapshot.pollutionDrift)
        assertEquals(1, snapshot.happinessDrift)
        assertTrue(snapshot.emergencyCoverage)
        assertEquals(3, snapshot.totalBuildings)
        assertTrue(snapshot.incomePerSecond > 0)
        assertTrue(snapshot.targetTraffic >= 0)
    }

    @Test
    fun hasIncomeTickElapsed_uses_spacing_threshold() {
        assertFalse(hasIncomeTickElapsed(lastIncomeTick = 1000L, nowMillis = 1649L))
        assertTrue(hasIncomeTickElapsed(lastIncomeTick = 1000L, nowMillis = 1651L))
        assertTrue(hasIncomeTickElapsed(lastIncomeTick = 1000L, nowMillis = 2000L, minimumSpacingMillis = 500L))
    }

    @Test
    fun applySimulationSnapshot_updates_state_projection_fields() {
        val state = GameState()
        val snapshot = buildSimulationSnapshot(
            state = state.copy(population = 600, happiness = 82, water = 88, power = 91),
            items = listOf(
                PlacedItem(1L, park, Position(0f, 0.02f, 0f)),
                PlacedItem(2L, road, Position(2f, 0.02f, 0f)),
                PlacedItem(3L, hospital, Position(4f, 0.02f, 0f))
            )
        )

        val updated = applySimulationSnapshot(state, snapshot)

        assertEquals(snapshot.balances.power, updated.powerBalance)
        assertEquals(snapshot.balances.water, updated.waterBalance)
        assertEquals(snapshot.balances.waste, updated.wasteBalance)
        assertEquals(snapshot.balances.jobs, updated.jobs)
        assertEquals(snapshot.balances.housing, updated.housingCapacity)
        assertEquals(snapshot.balances.taxIncome, updated.taxIncome)
        assertEquals(snapshot.serviceCoverage, updated.serviceCoverage)
        assertEquals(snapshot.roadGraph.emergencyDelay, updated.emergencyDelay)
        assertEquals(snapshot.totalBuildings, updated.totalBuildings)
    }

    @Test
    fun resolveSimulationActions_applies_roll_gates_and_population_paths() {
        val current = GameState(activeEmergency = "Fire outbreak", trafficDensity = 40)
        val snapshot = buildSimulationSnapshot(
            state = current.copy(population = 600, happiness = 82, water = 88, power = 91),
            items = listOf(
                PlacedItem(1L, park, Position(0f, 0.02f, 0f)),
                PlacedItem(2L, road, Position(2f, 0.02f, 0f)),
                PlacedItem(3L, hospital, Position(4f, 0.02f, 0f))
            )
        )

        val actions = resolveSimulationActions(
            current = current,
            snapshot = snapshot.copy(emergencyCoverage = false),
            effDelta = 1f,
            rolls = SimulationRolls(
                resourceRoll = 0.1f,
                emergencyPenaltyRoll = 0.05f,
                populationGrowthRoll = 0.1f,
                populationDeclineRoll = 0.9f,
                eventRoll = 0.001f
            )
        )

        assertEquals(snapshot.powerDrift, actions.powerDelta)
        assertEquals(snapshot.waterDrift, actions.waterDelta)
        assertEquals(snapshot.wasteDrift, actions.wasteDelta)
        assertEquals(((snapshot.targetTraffic - current.trafficDensity) * 0.018f).toInt(), actions.trafficDelta)
        assertEquals(snapshot.pollutionDrift, actions.pollutionDelta)
        assertEquals(snapshot.happinessDrift, actions.happinessDelta)
        assertEquals(snapshot.populationGrowth, actions.populationDelta)
        assertEquals(true, actions.emergencyPenalty)
        assertEquals(true, actions.triggerEvent)

        val declineActions = resolveSimulationActions(
            current = current,
            snapshot = snapshot.copy(populationGrowth = -1),
            effDelta = 1f,
            rolls = SimulationRolls(
                resourceRoll = 0.9f,
                emergencyPenaltyRoll = 0.9f,
                populationGrowthRoll = 0.9f,
                populationDeclineRoll = 0.2f,
                eventRoll = 0.9f
            )
        )
        assertEquals(0, declineActions.powerDelta)
        assertEquals(false, declineActions.emergencyPenalty)
        assertEquals(-1, declineActions.populationDelta)
        assertEquals(false, declineActions.triggerEvent)
    }

    @Test
    fun resolveIncomeTick_respects_spacing_and_minimum_income() {
        val snapshot = buildSimulationSnapshot(
            state = GameState(population = 200, happiness = 70),
            items = listOf(PlacedItem(1L, road, Position(0f, 0.02f, 0f)))
        )

        val blocked = resolveIncomeTick(
            snapshot = snapshot,
            effDelta = 1f,
            nowMillis = 1200L,
            lastIncomeTick = 1000L
        )
        assertEquals(false, blocked.grantIncome)
        assertEquals(false, blocked.updateLastIncomeTick)

        val granted = resolveIncomeTick(
            snapshot = snapshot,
            effDelta = 0.001f,
            nowMillis = 2000L,
            lastIncomeTick = 1000L
        )
        assertEquals(true, granted.grantIncome)
        assertEquals(true, granted.updateLastIncomeTick)
        assertEquals(true, granted.incomeAmount >= 1L)

        val paused = resolveIncomeTick(
            snapshot = snapshot,
            effDelta = 0f,
            nowMillis = 3000L,
            lastIncomeTick = 1000L
        )
        assertEquals(false, paused.grantIncome)
        assertEquals(0L, paused.incomeAmount)
        assertEquals(false, paused.updateLastIncomeTick)
    }
}



