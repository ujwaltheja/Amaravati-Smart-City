package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSaveModelsTest {
    private val house = BuildingDefinition(
        id = "house",
        category = BuildingCategory.Residential,
        title = "House",
        assetPath = "models/City-Commercial/building-a.glb",
        cost = 800
    )

    @Test
    fun gameState_toEntity_and_back_preserves_core_fields() {
        val original = GameState(
            money = 12345,
            population = 987,
            happiness = 76,
            water = 88,
            power = 91,
            waste = 14,
            pollution = 22,
            sustainabilityScore = 67,
            powerBalance = 4,
            waterBalance = 5,
            wasteBalance = -3,
            jobs = 321,
            housingCapacity = 654,
            taxIncome = 4321,
            serviceCoverage = 29,
            emergencyDelay = 17,
            cityName = "Amaravati",
            rank = "Transit Township",
            dayTime = 14.5f,
            trafficDensity = 61,
            activeMissionIndex = 3,
            activeEmergency = "Fire outbreak",
            graphicsQuality = 2,
            lastIncomeTick = 999L,
            totalBuildings = 42
        )

        val entity = original.toEntity(activeGoal = "Build parks", currentNews = "Good weather")
        val snapshot = entity.toSnapshot()

        assertEquals(original.money, snapshot.state.money)
        assertEquals(original.population, snapshot.state.population)
        assertEquals(original.happiness, snapshot.state.happiness)
        assertEquals(original.water, snapshot.state.water)
        assertEquals(original.power, snapshot.state.power)
        assertEquals(original.waste, snapshot.state.waste)
        assertEquals(original.pollution, snapshot.state.pollution)
        assertEquals(original.sustainabilityScore, snapshot.state.sustainabilityScore)
        assertEquals(original.powerBalance, snapshot.state.powerBalance)
        assertEquals(original.waterBalance, snapshot.state.waterBalance)
        assertEquals(original.wasteBalance, snapshot.state.wasteBalance)
        assertEquals(original.jobs, snapshot.state.jobs)
        assertEquals(original.housingCapacity, snapshot.state.housingCapacity)
        assertEquals(original.taxIncome, snapshot.state.taxIncome)
        assertEquals(original.serviceCoverage, snapshot.state.serviceCoverage)
        assertEquals(original.emergencyDelay, snapshot.state.emergencyDelay)
        assertEquals(original.cityName, snapshot.state.cityName)
        assertEquals(original.rank, snapshot.state.rank)
        assertEquals(original.dayTime, snapshot.state.dayTime)
        assertEquals(original.trafficDensity, snapshot.state.trafficDensity)
        assertEquals(original.activeMissionIndex, snapshot.state.activeMissionIndex)
        assertEquals(original.activeEmergency, snapshot.state.activeEmergency)
        assertEquals(original.graphicsQuality, snapshot.state.graphicsQuality)
        assertEquals(original.totalBuildings, snapshot.state.totalBuildings)
        assertEquals(original.lastIncomeTick, snapshot.state.lastIncomeTick)
        assertEquals("Build parks", snapshot.activeGoal)
        assertEquals("Good weather", snapshot.currentNews)
    }

    @Test
    fun reconstructPlacedItems_maps_known_items_and_skips_unknown_ones() {
        val reconstructed = reconstructPlacedItems(
            entities = listOf(
                PlacedItemEntity(1L, "house", 4f, 0.02f, 8f, 90f, 1f),
                PlacedItemEntity(2L, "missing", 2f, 0.02f, 2f, 0f, 1f)
            ),
            catalog = listOf(house)
        )

        assertEquals(1, reconstructed.size)
        assertEquals(1L, reconstructed.first().id)
        assertEquals(house, reconstructed.first().definition)
        assertEquals(Position(4f, 0.02f, 8f), reconstructed.first().position)
        assertEquals(90f, reconstructed.first().rotationY, 0.0001f)
        assertEquals(1f, reconstructed.first().scale, 0.0001f)
    }
}

