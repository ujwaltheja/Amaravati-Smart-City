package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.PlacedItem

class PlacedItemIdGenerator(startingId: Long = 1L) {
    private var nextId = startingId.coerceAtLeast(1L)

    fun next(): Long = nextId++

    fun seedFrom(items: List<PlacedItem>) {
        nextId = (items.maxOfOrNull { it.id }?.plus(1L) ?: 1L).coerceAtLeast(1L)
    }
}

