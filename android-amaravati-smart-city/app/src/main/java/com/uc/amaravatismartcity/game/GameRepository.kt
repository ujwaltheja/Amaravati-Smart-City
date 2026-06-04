package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    suspend fun saveSnapshot(state: GameState, items: List<PlacedItem>, activeGoal: String, currentNews: String)
    suspend fun loadSnapshot(): SavedGameSnapshot?
    fun observePlacedItems(): Flow<List<PlacedItemEntity>>
    suspend fun replacePlacedItems(items: List<PlacedItemEntity>)
    suspend fun clearPlacedItems()
    fun reconstructPlacedItems(entities: List<PlacedItemEntity>, catalog: List<BuildingDefinition>): List<PlacedItem>
}

