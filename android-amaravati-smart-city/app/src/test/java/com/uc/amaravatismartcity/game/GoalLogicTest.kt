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

class GoalLogicTest {
    private val road = BuildingDefinition(
        id = "road-basic",
        category = BuildingCategory.Infrastructure,
        title = "Basic Road",
        assetPath = "models/Roads and Bridges/road-straight.glb",
        cost = 500,
        roadUpgrade = RoadUpgrade.Basic
    )

    @Test
    fun resolveGoalState_reports_rank_and_advances_completed_mission() {
        val items = listOf(
            PlacedItem(1L, road, Position(0f, 0.02f, 0f)),
            PlacedItem(2L, road, Position(2f, 0.02f, 0f)),
            PlacedItem(3L, road, Position(4f, 0.02f, 0f))
        )
        val state = GameState(population = 220, activeMissionIndex = 0)

        val resolution = resolveGoalState(state, items)

        assertEquals("Protected Settlement", resolution.rank)
        assertTrue(resolution.missionCompleted)
        assertEquals(1, resolution.nextMissionIndex)
        assertEquals("Reach 500 citizens: Grow Amaravati to 500 citizens.", resolution.activeGoalText)
        assertEquals("First road network", resolution.completedMissionTitle)
        assertTrue(resolution.roadGraph.nodes.size >= 3)
    }

    @Test
    fun resolveGoalState_keeps_current_mission_when_not_complete() {
        val state = GameState(population = 120, activeMissionIndex = 1, happiness = 60)
        val resolution = resolveGoalState(state, emptyList())

        assertEquals("Rising Settlement", resolution.rank)
        assertFalse(resolution.missionCompleted)
        assertEquals(1, resolution.nextMissionIndex)
        assertEquals("Reach 500 citizens: Grow Amaravati to 500 citizens.", resolution.activeGoalText)
        assertEquals(null, resolution.completedMissionTitle)
    }
}

