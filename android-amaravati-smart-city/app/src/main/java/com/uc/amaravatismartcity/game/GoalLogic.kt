package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.PlacedItem


data class GoalResolution(
    val rank: String,
    val activeGoalText: String,
    val missionCompleted: Boolean,
    val completedMissionTitle: String? = null,
    val nextMissionIndex: Int,
    val roadGraph: RoadGraph
)

fun resolveGoalState(state: GameState, items: List<PlacedItem>): GoalResolution {
    val graph = buildRoadGraph(items)
    val rank = rankForPopulation(state.population)
    val missionIndex = state.activeMissionIndex.coerceIn(0, cityMissions.lastIndex)
    val mission = cityMissions[missionIndex]
    val completed = mission.isComplete(state, items, graph)
    val nextIndex = if (completed) (missionIndex + 1).coerceAtMost(cityMissions.lastIndex) else missionIndex
    val activeMission = cityMissions[nextIndex]

    return GoalResolution(
        rank = rank,
        activeGoalText = "${activeMission.title}: ${activeMission.description}",
        missionCompleted = completed,
        completedMissionTitle = if (completed) mission.title else null,
        nextMissionIndex = nextIndex,
        roadGraph = graph
    )
}

