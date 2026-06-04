package com.uc.amaravatismartcity.models

object BuildingCatalog {
    fun defaultCatalog(assetPaths: List<String>): List<BuildingDefinition> {
        val fallback = assetPaths.firstOrNull().orEmpty()

        fun pick(vararg tokens: String, preferFolder: String? = null, avoid: List<String> = emptyList()): String {
            return assetPaths
                .asSequence()
                .filter { assetPath ->
                    val lower = assetPath.lowercase()
                    avoid.none { lower.contains(it) } && tokens.any { token -> lower.contains(token) }
                }
                .maxByOrNull { assetPath ->
                val lower = assetPath.lowercase()
                    val folderScore = if (preferFolder != null && lower.contains(preferFolder.lowercase())) 80 else 0
                    val exactNameScore = tokens.sumOf { token -> if (lower.endsWith("$token.glb")) 30 else 0 }
                    val lowDetailPenalty = if (lower.contains("low-detail")) -8 else 0
                    folderScore + exactNameScore + lowDetailPenalty
                } ?: fallback
        }

        return listOf(
            BuildingDefinition(
                id = "residential-house",
                category = BuildingCategory.Residential,
                title = "Suburban House",
                assetPath = pick("low-detail-building-a", "low-detail-building-b", "building-a", preferFolder = "City-Commercial"),
                cost = 800,
                housingCapacity = 8,
                populationImpact = 8,
                happinessImpact = 3,
                powerImpact = -2,
                waterImpact = -2,
                wasteImpact = 1,
                taxIncome = 55
            ),
            BuildingDefinition(
                id = "residential-apartment",
                category = BuildingCategory.Residential,
                title = "Apartment Block",
                assetPath = pick("low-detail-building-wide-a", "low-detail-building-m", "building-h", preferFolder = "City-Commercial"),
                cost = 2500,
                width = 2,
                depth = 2,
                housingCapacity = 55,
                populationImpact = 45,
                happinessImpact = 1,
                powerImpact = -10,
                waterImpact = -8,
                wasteImpact = 5,
                taxIncome = 240
            ),
            BuildingDefinition(
                id = "commercial-office",
                category = BuildingCategory.Commercial,
                title = "Office Building",
                assetPath = pick("building-a", "building-b", "building-c", preferFolder = "City-Commercial"),
                cost = 3000,
                width = 2,
                depth = 2,
                jobs = 35,
                populationImpact = 5,
                happinessImpact = 2,
                powerImpact = -15,
                waterImpact = -5,
                wasteImpact = 4,
                taxIncome = 520
            ),
            BuildingDefinition(
                id = "commercial-skyscraper",
                category = BuildingCategory.Commercial,
                title = "IT Skyscraper",
                assetPath = pick("skyscraper-a", "skyscraper-b", "skyscraper-c", preferFolder = "City-Commercial"),
                cost = 12000,
                width = 3,
                depth = 3,
                jobs = 180,
                populationImpact = 15,
                happinessImpact = 5,
                powerImpact = -45,
                waterImpact = -25,
                wasteImpact = 12,
                taxIncome = 2600,
                unlockPopulation = 500
            ),
            BuildingDefinition(
                id = "government-secretariat",
                category = BuildingCategory.Government,
                title = "Secretariat",
                assetPath = pick("building-skyscraper-d", "building-skyscraper-e", preferFolder = "City-Commercial"),
                cost = 15000,
                width = 3,
                depth = 3,
                jobs = 120,
                happinessImpact = 10,
                sustainabilityImpact = 5,
                powerImpact = -50,
                waterImpact = -30,
                wasteImpact = 10,
                taxIncome = 1800,
                serviceCoverage = 8,
                unlockPopulation = 1200
            ),
            BuildingDefinition(
                id = "emergency-police",
                category = BuildingCategory.Emergency,
                title = "Police Hub",
                assetPath = pick("police", "building-f", preferFolder = "Cars"),
                cost = 4500,
                width = 2,
                depth = 2,
                jobs = 28,
                happinessImpact = 12,
                powerImpact = -10,
                waterImpact = -5,
                wasteImpact = 2,
                serviceCoverage = 7,
                unlockPopulation = 150
            ),
            BuildingDefinition(
                id = "emergency-hospital",
                category = BuildingCategory.Emergency,
                title = "City Hospital",
                assetPath = pick("ambulance", "building-g", preferFolder = "Cars"),
                cost = 7500,
                width = 3,
                depth = 2,
                jobs = 85,
                happinessImpact = 20,
                powerImpact = -30,
                waterImpact = -25,
                wasteImpact = 10,
                serviceCoverage = 9,
                unlockPopulation = 300
            ),
            BuildingDefinition(
                id = "industrial-warehouse",
                category = BuildingCategory.Industrial,
                title = "Logistics Hub",
                assetPath = pick("building-o", "building-p", preferFolder = "City-Industries"),
                cost = 3500,
                width = 2,
                depth = 2,
                jobs = 55,
                powerImpact = -10,
                waterImpact = -5,
                wasteImpact = 8,
                pollutionImpact = 8,
                taxIncome = 780
            ),
            BuildingDefinition(
                id = "industrial-factory-heavy",
                category = BuildingCategory.Industrial,
                title = "Heavy Industry",
                assetPath = pick("chimney-large", "building-r", "building-s", preferFolder = "City-Industries"),
                cost = 9000,
                width = 3,
                depth = 3,
                jobs = 140,
                populationImpact = 5,
                happinessImpact = -8,
                sustainabilityImpact = -10,
                powerImpact = -80,
                waterImpact = -60,
                wasteImpact = 40,
                pollutionImpact = 35,
                taxIncome = 2200,
                unlockPopulation = 400
            ),
            BuildingDefinition(
                id = "utility-solar-farm",
                category = BuildingCategory.Utilities,
                title = "Solar Grid",
                assetPath = pick("light-square", "tile-high", "detail-tank", preferFolder = "Roads and Bridges"),
                cost = 5500,
                width = 3,
                depth = 2,
                sustainabilityImpact = 12,
                powerImpact = 120,
                serviceCoverage = 9
            ),
            BuildingDefinition(
                id = "utility-water-tower",
                category = BuildingCategory.Utilities,
                title = "Water Tower",
                assetPath = pick("detail-tank", "tank", "tower", preferFolder = "City-Industries"),
                cost = 4000,
                width = 2,
                depth = 2,
                waterImpact = 90,
                powerImpact = -5,
                serviceCoverage = 8
            ),
            BuildingDefinition(
                id = "utility-recycling",
                category = BuildingCategory.Utilities,
                title = "Recycling Plant",
                assetPath = pick("garbage-truck", "building-n", "building-m", preferFolder = "Cars"),
                cost = 6000,
                width = 2,
                depth = 2,
                jobs = 45,
                sustainabilityImpact = 8,
                wasteImpact = -100,
                powerImpact = -20,
                serviceCoverage = 8,
                unlockPopulation = 200
            ),
            BuildingDefinition(
                id = "road-basic",
                category = BuildingCategory.Infrastructure,
                title = "Basic Road",
                assetPath = pick("road-straight", "road-bend", preferFolder = "Roads and Bridges"),
                cost = 500,
                roadUpgrade = RoadUpgrade.Basic,
                taxIncome = 20
            ),
            BuildingDefinition(
                id = "road-smart",
                category = BuildingCategory.Infrastructure,
                title = "Smart Road",
                assetPath = pick("road-intersection-line", "road-straight", preferFolder = "Roads and Bridges"),
                cost = 900,
                roadUpgrade = RoadUpgrade.Smart,
                sustainabilityImpact = 2,
                powerImpact = -1,
                unlockPopulation = 150
            ),
            BuildingDefinition(
                id = "road-bus-lane",
                category = BuildingCategory.Infrastructure,
                title = "Bus Lane",
                assetPath = pick("road-straight-barrier", "road-side", preferFolder = "Roads and Bridges"),
                cost = 1600,
                roadUpgrade = RoadUpgrade.BusLane,
                sustainabilityImpact = 4,
                powerImpact = -1,
                unlockPopulation = 800
            ),
            BuildingDefinition(
                id = "road-flyover",
                category = BuildingCategory.Infrastructure,
                title = "Flyover",
                assetPath = pick("road-bridge", "bridge", preferFolder = "Roads and Bridges"),
                cost = 3200,
                roadUpgrade = RoadUpgrade.Flyover,
                width = 2,
                depth = 1,
                unlockPopulation = 1200
            ),
            BuildingDefinition(
                id = "green-central-park",
                category = BuildingCategory.GreenSpace,
                title = "Central Park",
                assetPath = pick("tile-low", "tile-high", "detail-parasol", preferFolder = "Roads and Bridges"),
                cost = 1200,
                width = 2,
                depth = 2,
                happinessImpact = 12,
                sustainabilityImpact = 15,
                waterImpact = -5,
                pollutionImpact = -10,
                serviceCoverage = 5
            ),
            BuildingDefinition(
                id = "riverfront-district",
                category = BuildingCategory.Riverfront,
                title = "Riverfront District",
                assetPath = pick("boat-house", "ramp-wide", "gate", preferFolder = "Water"),
                cost = 8500,
                width = 3,
                depth = 2,
                jobs = 75,
                happinessImpact = 14,
                sustainabilityImpact = 8,
                powerImpact = -20,
                waterImpact = -12,
                taxIncome = 1450,
                unlockPopulation = 600
            ),
            BuildingDefinition(
                id = "transport-bus-terminal",
                category = BuildingCategory.Transport,
                title = "Bus Terminal",
                assetPath = pick("van", "road-roundabout", "taxi", preferFolder = "Cars"),
                cost = 9000,
                width = 3,
                depth = 2,
                jobs = 60,
                happinessImpact = 8,
                powerImpact = -18,
                pollutionImpact = -8,
                serviceCoverage = 8,
                unlockPopulation = 800
            ),
            BuildingDefinition(
                id = "transport-metro",
                category = BuildingCategory.Transport,
                title = "Metro Station",
                assetPath = pick("train-electric-subway", "train-tram-modern", "track", preferFolder = "Train"),
                cost = 22000,
                width = 3,
                depth = 3,
                jobs = 120,
                happinessImpact = 15,
                powerImpact = -55,
                pollutionImpact = -18,
                serviceCoverage = 10,
                unlockPopulation = 2000
            ),
            BuildingDefinition(
                id = "smart-capital-command",
                category = BuildingCategory.Government,
                title = "Smart Capital Core",
                assetPath = pick("building-skyscraper-e", "building-skyscraper-d", preferFolder = "City-Commercial"),
                cost = 42000,
                width = 4,
                depth = 4,
                jobs = 300,
                happinessImpact = 20,
                sustainabilityImpact = 20,
                powerImpact = -100,
                waterImpact = -60,
                taxIncome = 6000,
                serviceCoverage = 12,
                unlockPopulation = 4000
            ),
            BuildingDefinition(
                id = "education-knowledge-campus",
                category = BuildingCategory.Education,
                title = "Knowledge Campus",
                assetPath = pick("building-l", "building-m", "low-detail-building-wide-b", preferFolder = "City-Commercial"),
                cost = 11000,
                width = 3,
                depth = 2,
                jobs = 90,
                happinessImpact = 10,
                sustainabilityImpact = 6,
                powerImpact = -28,
                waterImpact = -18,
                wasteImpact = 8,
                taxIncome = 900,
                serviceCoverage = 7,
                unlockPopulation = 500
            ),
            BuildingDefinition(
                id = "emergency-fire-station",
                category = BuildingCategory.Emergency,
                title = "Fire Station",
                assetPath = pick("firetruck", "building-e", preferFolder = "Cars"),
                cost = 6200,
                width = 2,
                depth = 2,
                jobs = 42,
                happinessImpact = 10,
                powerImpact = -12,
                waterImpact = -8,
                serviceCoverage = 8,
                unlockPopulation = 150
            )
        )
    }
}
