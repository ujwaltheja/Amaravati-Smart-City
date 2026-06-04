package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacedItemIdGeneratorTest {
    private val definition = BuildingDefinition(
        id = "house",
        category = BuildingCategory.Residential,
        title = "House",
        assetPath = "models/City-Commercial/building-a.glb",
        cost = 800
    )

    @Test
    fun next_increments_and_seedFrom_continues_after_loaded_items() {
        val generator = PlacedItemIdGenerator()
        assertEquals(1L, generator.next())
        assertEquals(2L, generator.next())

        generator.seedFrom(
            listOf(
                PlacedItem(10L, definition, Position(0f, 0.02f, 0f)),
                PlacedItem(14L, definition, Position(2f, 0.02f, 0f))
            )
        )

        assertEquals(15L, generator.next())
        assertEquals(16L, generator.next())
    }
}

