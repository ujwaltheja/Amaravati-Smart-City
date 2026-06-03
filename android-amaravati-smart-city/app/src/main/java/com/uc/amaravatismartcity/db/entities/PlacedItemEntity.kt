package com.uc.amaravatismartcity.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "placed_items")
data class PlacedItemEntity(
    @PrimaryKey val id: Long,
    val buildingId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val rotationY: Float,
    val scale: Float
)
