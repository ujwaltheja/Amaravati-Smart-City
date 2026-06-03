package com.uc.amaravatismartcity.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uc.amaravatismartcity.db.entities.GameStateEntity
import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM placed_items")
    fun getAllPlacedItems(): Flow<List<PlacedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacedItems(items: List<PlacedItemEntity>)

    @Query("DELETE FROM placed_items")
    suspend fun clearPlacedItems()

    @Query("SELECT * FROM game_state WHERE id = 0")
    suspend fun getGameState(): GameStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGameState(state: GameStateEntity)
}
