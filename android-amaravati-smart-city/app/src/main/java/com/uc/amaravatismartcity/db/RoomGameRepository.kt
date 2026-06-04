package com.uc.amaravatismartcity.db

import android.content.Context
import com.uc.amaravatismartcity.db.entities.GameStateEntity
import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import com.uc.amaravatismartcity.game.GameRepository
import com.uc.amaravatismartcity.game.GameState
import com.uc.amaravatismartcity.game.SavedGameSnapshot
import com.uc.amaravatismartcity.game.reconstructPlacedItems as reconstructPlacedItemsHelper
import com.uc.amaravatismartcity.game.toEntity
import com.uc.amaravatismartcity.game.toSnapshot
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import kotlinx.coroutines.flow.Flow

class RoomGameRepository(context: Context) : GameRepository {
    private val dao = GameDatabase.getDatabase(context).gameDao()

    override suspend fun saveSnapshot(
        state: GameState,
        items: List<PlacedItem>,
        activeGoal: String,
        currentNews: String
    ) {
        dao.saveGameState(state.toEntity(activeGoal, currentNews))
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
    }

    override suspend fun loadSnapshot(): SavedGameSnapshot? = dao.getGameState()?.toSnapshot()

    override fun observePlacedItems(): Flow<List<PlacedItemEntity>> = dao.getAllPlacedItems()

    override suspend fun replacePlacedItems(items: List<PlacedItemEntity>) {
        dao.clearPlacedItems()
        dao.insertPlacedItems(items)
    }

    override suspend fun clearPlacedItems() {
        dao.clearPlacedItems()
    }

    override fun reconstructPlacedItems(
        entities: List<PlacedItemEntity>,
        catalog: List<BuildingDefinition>
    ): List<PlacedItem> = reconstructPlacedItemsHelper(entities, catalog)

}

