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

class CitySystemsTest {
    private val road = BuildingDefinition(
        id = "road-basic",
        category = BuildingCategory.Infrastructure,
        title = "Basic Road",
        assetPath = "models/Roads and Bridges/road-straight.glb",
        cost = 500,
        roadUpgrade = RoadUpgrade.Basic
    )

    private val house = BuildingDefinition(
        id = "house",
        category = BuildingCategory.Residential,
        title = "House",
        assetPath = "models/City-Commercial/building-a.glb",
        cost = 800,
        width = 1,
        depth = 1,
        housingCapacity = 8,
        populationImpact = 8,
        happinessImpact = 3,
        powerImpact = -2,
        waterImpact = -2,
        wasteImpact = 1,
        taxIncome = 55
    )

    @Test
    fun snapToGrid_and_gridCell_align_positions() {
        val snapped = snapToGrid(Position(3.1f, 0.2f, 5.7f))
        assertEquals(4f, snapped.x, 0.0001f)
        assertEquals(6f, snapped.z, 0.0001f)
        assertEquals(GridCell(2, 3), snapped.gridCell())
        assertEquals(Position(4f, 0.02f, 6f), GridCell(2, 3).position())
    }

    @Test
    fun canPlaceOnGrid_blocks_overlap_and_requires_road_alignment() {
        val existing = listOf(
            PlacedItem(1L, house, Position(0f, 0.02f, 0f)),
            PlacedItem(2L, road, Position(2f, 0.02f, 0f))
        )

        assertFalse(canPlaceOnGrid(house, Position(0f, 0.02f, 0f), existing))
        assertTrue(canPlaceOnGrid(house, Position(4f, 0.02f, 0f), existing))
        assertFalse(canPlaceOnGrid(road, Position(1f, 0.02f, 0f), existing))
        assertTrue(canPlaceOnGrid(road, Position(4f, 0.02f, 0f), existing))
    }

    @Test
    fun calculateBalances_and_rank_for_population_return_expected_values() {
        val items = listOf(
            PlacedItem(1L, house, Position(0f, 0.02f, 0f)),
            PlacedItem(2L, road, Position(2f, 0.02f, 0f))
        )

        val balances = calculateBalances(items)
        assertEquals(-2, balances.power)
        assertEquals(-2, balances.water)
        assertEquals(1, balances.waste)
        assertEquals(0, balances.pollution)
        assertEquals(8, balances.housing)
        assertEquals(55L, balances.taxIncome)
        assertEquals("Rising Settlement", rankForPopulation(120))
        assertEquals("IT Growth City", rankForPopulation(500))
        assertEquals("Smart Capital", rankForPopulation(4000))
    }

    @Test
    fun buildRoadGraph_and_cityMission_progression_work_for_connected_roads() {
        val items = listOf(
            PlacedItem(1L, road, Position(0f, 0.02f, 0f)),
            PlacedItem(2L, road, Position(2f, 0.02f, 0f)),
            PlacedItem(3L, road, Position(4f, 0.02f, 0f)),
            PlacedItem(4L, house, Position(8f, 0.02f, 0f))
        )

        val graph = buildRoadGraph(items)
        assertEquals(3, graph.nodes.size)
        assertTrue(graph.nodes.values.any { it.neighbors.isNotEmpty() })
        assertTrue(cityMissions.first().isComplete(GameState(), items, graph))
    }
}


