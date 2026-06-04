package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.db.entities.GameStateEntity
import com.uc.amaravatismartcity.db.entities.PlacedItemEntity
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import io.github.sceneview.math.Position


data class SavedGameSnapshot(
    val state: GameState,
    val activeGoal: String,
    val currentNews: String
)

fun GameState.toEntity(activeGoal: String, currentNews: String): GameStateEntity {
    return GameStateEntity(
        money = money,
        population = population,
        happiness = happiness,
        water = water,
        power = power,
        waste = waste,
        pollution = pollution,
        sustainabilityScore = sustainabilityScore,
        powerBalance = powerBalance,
        waterBalance = waterBalance,
        wasteBalance = wasteBalance,
        jobs = jobs,
        housingCapacity = housingCapacity,
        taxIncome = taxIncome,
        serviceCoverage = serviceCoverage,
        emergencyDelay = emergencyDelay,
        cityName = cityName,
        rank = rank,
        dayTime = dayTime,
        trafficDensity = trafficDensity,
        activeMissionIndex = activeMissionIndex,
        activeEmergency = activeEmergency,
        graphicsQuality = graphicsQuality,
        totalBuildings = totalBuildings,
        activeGoal = activeGoal,
        currentNews = currentNews
    )
}

fun GameStateEntity.toSnapshot(): SavedGameSnapshot {
    return SavedGameSnapshot(
        state = GameState(
            money = money,
            population = population,
            happiness = happiness,
            water = water,
            power = power,
            waste = waste,
            pollution = pollution,
            sustainabilityScore = sustainabilityScore,
            powerBalance = powerBalance,
            waterBalance = waterBalance,
            wasteBalance = wasteBalance,
            jobs = jobs,
            housingCapacity = housingCapacity,
            taxIncome = taxIncome,
            serviceCoverage = serviceCoverage,
            emergencyDelay = emergencyDelay,
            cityName = cityName,
            rank = rank,
            dayTime = dayTime,
            trafficDensity = trafficDensity,
            activeMissionIndex = activeMissionIndex,
            activeEmergency = activeEmergency,
            graphicsQuality = graphicsQuality,
            totalBuildings = totalBuildings,
            lastIncomeTick = 0L
        ),
        activeGoal = activeGoal,
        currentNews = currentNews
    )
}

fun reconstructPlacedItems(
    entities: List<PlacedItemEntity>,
    catalog: List<BuildingDefinition>
): List<PlacedItem> {
    return entities.mapNotNull { entity ->
        val definition = catalog.firstOrNull { it.id == entity.buildingId } ?: return@mapNotNull null
        PlacedItem(
            id = entity.id,
            definition = definition,
            position = Position(entity.posX, entity.posY, entity.posZ),
            rotationY = entity.rotationY,
            scale = entity.scale
        )
    }
}

