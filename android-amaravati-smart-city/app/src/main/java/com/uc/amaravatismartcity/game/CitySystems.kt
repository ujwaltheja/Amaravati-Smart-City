package com.uc.amaravatismartcity.game

import com.uc.amaravatismartcity.models.BuildingCategory
import com.uc.amaravatismartcity.models.BuildingDefinition
import com.uc.amaravatismartcity.models.PlacedItem
import com.uc.amaravatismartcity.models.RoadUpgrade
import io.github.sceneview.math.Position
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

const val CITY_GRID_SIZE = 2f

data class GridCell(val x: Int, val z: Int)

data class CityBalances(
    val power: Int,
    val water: Int,
    val waste: Int,
    val pollution: Int,
    val jobs: Int,
    val housing: Int,
    val taxIncome: Long,
    val serviceCoverage: Int
)

data class RoadNode(
    val itemId: Long,
    val cell: GridCell,
    val upgrade: RoadUpgrade,
    val neighbors: Set<GridCell>
)

data class RoadGraph(
    val nodes: Map<GridCell, RoadNode>,
    val usageByCell: Map<GridCell, Int>,
    val routeCount: Int,
    val averageCongestion: Int,
    val emergencyDelay: Int
)

data class CityMission(
    val title: String,
    val description: String,
    val isComplete: (GameState, List<PlacedItem>, RoadGraph) -> Boolean
)

enum class HeatmapMode(val displayName: String) {
    Traffic("Traffic"),
    Pollution("Pollution"),
    Happiness("Happiness"),
    Power("Power"),
    Water("Water"),
    Emergency("Emergency")
}

enum class EmergencyType(val displayName: String, val requiredService: BuildingCategory, val expensive: Boolean = false) {
    Fire("Fire outbreak", BuildingCategory.Emergency, true),
    Medical("Medical emergency", BuildingCategory.Emergency),
    Accident("Traffic accident", BuildingCategory.Emergency),
    Flood("Monsoon flooding", BuildingCategory.Utilities, true),
    Outage("Power outage", BuildingCategory.Utilities),
    Spill("Industrial spill", BuildingCategory.Emergency, true)
}

fun snapToGrid(pos: Position): Position {
    return Position(
        x = kotlin.math.round(pos.x / CITY_GRID_SIZE) * CITY_GRID_SIZE,
        y = pos.y,
        z = kotlin.math.round(pos.z / CITY_GRID_SIZE) * CITY_GRID_SIZE
    )
}

fun Position.gridCell(): GridCell {
    val snapped = snapToGrid(this)
    return GridCell((snapped.x / CITY_GRID_SIZE).toInt(), (snapped.z / CITY_GRID_SIZE).toInt())
}

fun GridCell.position(y: Float = 0.02f): Position = Position(x * CITY_GRID_SIZE, y, z * CITY_GRID_SIZE)

fun footprintCells(definition: BuildingDefinition, origin: GridCell): Set<GridCell> {
    val w = max(1, definition.width)
    val d = max(1, definition.depth)
    return buildSet {
        for (dx in 0 until w) {
            for (dz in 0 until d) add(GridCell(origin.x + dx, origin.z + dz))
        }
    }
}

fun isRoad(definition: BuildingDefinition): Boolean {
    return definition.category == BuildingCategory.Infrastructure && definition.roadUpgrade != RoadUpgrade.None
}

fun canPlaceOnGrid(definition: BuildingDefinition, position: Position, items: List<PlacedItem>): Boolean {
    val target = footprintCells(definition, position.gridCell())
    val occupied = items
        .filter { it.definition.cost > 0 }
        .flatMap { footprintCells(it.definition, it.position.gridCell()) }
        .toSet()
    if (target.any { it in occupied }) return false
    return !isRoad(definition) || position.x % CITY_GRID_SIZE == 0f && position.z % CITY_GRID_SIZE == 0f
}

fun calculateUpkeep(items: List<PlacedItem>): Long {
    return items.sumOf { item ->
        val cost = item.definition.cost
        when (item.definition.category) {
            BuildingCategory.Infrastructure -> (cost * 0.04f).toLong().coerceAtLeast(2)
            BuildingCategory.Utilities -> (cost * 0.08f).toLong().coerceAtLeast(20)
            BuildingCategory.Emergency -> (cost * 0.10f).toLong().coerceAtLeast(50)
            BuildingCategory.Government -> (cost * 0.08f).toLong().coerceAtLeast(100)
            BuildingCategory.Transport -> (cost * 0.06f).toLong().coerceAtLeast(60)
            BuildingCategory.Education -> (cost * 0.07f).toLong().coerceAtLeast(40)
            BuildingCategory.GreenSpace -> (cost * 0.05f).toLong().coerceAtLeast(10)
            else -> 0L
        }
    }
}

fun calculateBalances(items: List<PlacedItem>): CityBalances {
    var netTax = 0L
    var powerGen = 0
    var waterGen = 0
    var wasteGen = 0
    var pollutionGen = 0
    var jobsTotal = 0
    var housingTotal = 0
    
    val roadCells = items.filter { isRoad(it.definition) }.map { it.position.gridCell() }.toSet()
    val solarGrids = items.filter { it.definition.id == "utility-solar-farm" }
    val waterTowers = items.filter { it.definition.id == "utility-water-tower" }
    
    items.forEach { item ->
        val def = item.definition
        if (def.category == BuildingCategory.Infrastructure) {
            netTax += def.taxIncome
            powerGen += def.powerImpact
            waterGen += def.waterImpact
            wasteGen += def.wasteImpact
            pollutionGen += def.pollutionImpact
            return@forEach
        }
        
        val cell = item.position.gridCell()
        val hasRoad = roadCells.isEmpty() || listOf(
            GridCell(cell.x + 1, cell.z),
            GridCell(cell.x - 1, cell.z),
            GridCell(cell.x, cell.z + 1),
            GridCell(cell.x, cell.z - 1)
        ).any { it in roadCells }
        
        val hasPower = def.powerImpact >= 0 || solarGrids.isEmpty() || solarGrids.any { solar ->
            hypot(item.position.x - solar.position.x, item.position.z - solar.position.z) <= solar.definition.serviceCoverage * CITY_GRID_SIZE
        }
        
        val hasWater = def.waterImpact >= 0 || waterTowers.isEmpty() || waterTowers.any { tower ->
            hypot(item.position.x - tower.position.x, item.position.z - tower.position.z) <= tower.definition.serviceCoverage * CITY_GRID_SIZE
        }
        
        val fullyConnected = hasRoad && hasPower && hasWater
        
        if (fullyConnected) {
            netTax += def.taxIncome
            jobsTotal += def.jobs
            housingTotal += def.housingCapacity
        } else {
            netTax += (def.taxIncome * 0.1f).toLong()
            jobsTotal += (def.jobs * 0.15f).toInt()
            housingTotal += (def.housingCapacity * 0.15f).toInt()
        }
        
        powerGen += def.powerImpact
        waterGen += def.waterImpact
        wasteGen += def.wasteImpact
        pollutionGen += def.pollutionImpact
    }
    
    return CityBalances(
        power = powerGen,
        water = waterGen,
        waste = wasteGen,
        pollution = pollutionGen,
        jobs = jobsTotal,
        housing = housingTotal,
        taxIncome = netTax,
        serviceCoverage = items.filter { it.definition.serviceCoverage > 0 }.sumOf { it.definition.serviceCoverage }
    )
}

fun buildRoadGraph(items: List<PlacedItem>): RoadGraph {
    val roads = items.filter { isRoad(it.definition) }
    val nodeCells = roads.associateBy { it.position.gridCell() }
    val nodes = roads.associate { road ->
        val cell = road.position.gridCell()
        val neighbors = listOf(
            GridCell(cell.x + 1, cell.z),
            GridCell(cell.x - 1, cell.z),
            GridCell(cell.x, cell.z + 1),
            GridCell(cell.x, cell.z - 1)
        ).filter { it in nodeCells }.toSet()
        cell to RoadNode(road.id, cell, road.definition.roadUpgrade, neighbors)
    }

    val zones = items.filter { it.definition.category != BuildingCategory.Infrastructure && it.definition.cost > 0 }
    val residential = zones.filter { it.definition.category == BuildingCategory.Residential }
    val destinations = zones.filter {
        it.definition.category in setOf(
            BuildingCategory.Commercial,
            BuildingCategory.Industrial,
            BuildingCategory.Emergency,
            BuildingCategory.Utilities,
            BuildingCategory.Transport,
            BuildingCategory.Government
        )
    }

    val usage = mutableMapOf<GridCell, Int>()
    var routeCount = 0
    residential.forEach { home ->
        destinations.take(8).forEach { dest ->
            val start = nearestRoadCell(home.position, nodes.keys)
            val end = nearestRoadCell(dest.position, nodes.keys)
            val route = if (start != null && end != null) findRoute(start, end, nodes) else emptyList()
            if (route.isNotEmpty()) {
                routeCount++
                val weight = max(1, home.definition.housingCapacity / 12) + max(1, dest.definition.jobs / 45)
                route.forEach { usage[it] = (usage[it] ?: 0) + weight }
            }
        }
    }

    val averageCongestion = if (usage.isEmpty()) 0 else {
        usage.map { (cell, used) ->
            val capacity = nodes[cell]?.upgrade?.capacity ?: 45
            (used * 100 / capacity).coerceIn(0, 160)
        }.average().toInt().coerceIn(0, 100)
    }
    val emergencyDelay = (averageCongestion * 0.7f + max(0, zones.size - nodes.size) * 2).toInt().coerceIn(0, 100)
    return RoadGraph(nodes, usage, routeCount, averageCongestion, emergencyDelay)
}

fun nearestRoadCell(position: Position, roads: Set<GridCell>): GridCell? {
    val source = position.gridCell()
    return roads.minByOrNull { abs(it.x - source.x) + abs(it.z - source.z) }
        ?.takeIf { abs(it.x - source.x) + abs(it.z - source.z) <= 5 }
}

fun findRoute(start: GridCell, end: GridCell, nodes: Map<GridCell, RoadNode>): List<GridCell> {
    if (start == end) return listOf(start)
    val frontier = ArrayDeque<GridCell>()
    val cameFrom = mutableMapOf<GridCell, GridCell?>()
    frontier.add(start)
    cameFrom[start] = null
    while (frontier.isNotEmpty()) {
        val current = frontier.removeFirst()
        if (current == end) break
        nodes[current]?.neighbors.orEmpty().forEach { next ->
            if (next !in cameFrom) {
                cameFrom[next] = current
                frontier.add(next)
            }
        }
    }
    if (end !in cameFrom) return emptyList()
    val route = mutableListOf<GridCell>()
    var cursor: GridCell? = end
    while (cursor != null) {
        route += cursor
        cursor = cameFrom[cursor]
    }
    return route.asReversed()
}

fun serviceCoveragePercent(items: List<PlacedItem>): Int {
    val serviceItems = items.filter { it.definition.serviceCoverage > 0 }
    val demand = items.count { it.definition.cost > 0 && it.definition.category != BuildingCategory.Infrastructure }
    if (demand == 0) return 0
    val covered = items.count { target ->
        target.definition.cost > 0 && serviceItems.any { svc ->
            hypot(target.position.x - svc.position.x, target.position.z - svc.position.z) <= svc.definition.serviceCoverage * CITY_GRID_SIZE
        }
    }
    return (covered * 100 / demand).coerceIn(0, 100)
}

fun heatScore(mode: HeatmapMode, item: PlacedItem, graph: RoadGraph, state: GameState): Float {
    val cell = item.position.gridCell()
    return when (mode) {
        HeatmapMode.Traffic -> ((graph.usageByCell[cell] ?: 0) / 80f).coerceIn(0f, 1f)
        HeatmapMode.Pollution -> ((item.definition.pollutionImpact + state.pollution / 3f) / 55f).coerceIn(0f, 1f)
        HeatmapMode.Happiness -> ((item.definition.happinessImpact + state.happiness) / 115f).coerceIn(0f, 1f)
        HeatmapMode.Power -> if (item.definition.powerImpact >= 0) 0.15f else (abs(item.definition.powerImpact) / 80f).coerceIn(0f, 1f)
        HeatmapMode.Water -> if (item.definition.waterImpact >= 0) 0.15f else (abs(item.definition.waterImpact) / 65f).coerceIn(0f, 1f)
        HeatmapMode.Emergency -> (graph.emergencyDelay / 100f).coerceIn(0f, 1f)
    }
}

val cityMissions = listOf(
    CityMission("First road network", "Build at least three connected road tiles.") { _, _, graph -> graph.nodes.size >= 3 && graph.nodes.values.any { it.neighbors.isNotEmpty() } },
    CityMission("Reach 500 citizens", "Grow Amaravati to 500 citizens.") { state, _, _ -> state.population >= 500 },
    CityMission("80 percent happiness", "Maintain citizen happiness at 80 percent.") { state, _, _ -> state.happiness >= 80 },
    CityMission("Hospital coverage", "Build hospital or emergency coverage.") { _, items, _ -> items.any { it.definition.id.contains("hospital") } && serviceCoveragePercent(items) >= 35 },
    CityMission("Clean air mandate", "Reduce pollution below 25 percent.") { state, _, _ -> state.pollution < 25 },
    CityMission("Riverfront district", "Build the riverfront district.") { _, items, _ -> items.any { it.definition.category == BuildingCategory.Riverfront } },
    CityMission("Smart Capital rank", "Reach Smart Capital status with 4000 citizens and strong systems.") { state, _, graph -> state.population >= 4000 && state.happiness >= 80 && graph.averageCongestion < 70 }
)

fun rankForPopulation(population: Int): String = when {
    population >= 4000 -> "Smart Capital"
    population >= 2000 -> "Capital City"
    population >= 1200 -> "Government District"
    population >= 800 -> "Transit Township"
    population >= 500 -> "IT Growth City"
    population >= 300 -> "Healthy Township"
    population >= 150 -> "Protected Settlement"
    else -> "Rising Settlement"
}
