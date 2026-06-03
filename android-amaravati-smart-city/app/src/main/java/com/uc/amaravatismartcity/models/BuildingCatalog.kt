package com.uc.amaravatismartcity.models

object BuildingCatalog {
    fun defaultCatalog(assetPaths: List<String>): List<BuildingDefinition> {
        val fallback = assetPaths.firstOrNull().orEmpty()

        fun pick(vararg tokens: String): String {
            return assetPaths.firstOrNull { assetPath ->
                val lower = assetPath.lowercase()
                tokens.any { token -> lower.contains(token) }
            } ?: fallback
        }

        return listOf(
            BuildingDefinition(
                id = "residential-house",
                category = BuildingCategory.Residential,
                title = "Suburban House",
                assetPath = pick("house", "home"),
                cost = 800,
                populationImpact = 8,
                happinessImpact = 3,
                powerImpact = -2,
                waterImpact = -2,
                wasteImpact = 1
            ),
            BuildingDefinition(
                id = "residential-apartment",
                category = BuildingCategory.Residential,
                title = "Apartment Block",
                assetPath = pick("apartment", "residential", "flat"),
                cost = 2500,
                populationImpact = 45,
                happinessImpact = 1,
                powerImpact = -10,
                waterImpact = -8,
                wasteImpact = 5
            ),
            BuildingDefinition(
                id = "commercial-office",
                category = BuildingCategory.Commercial,
                title = "Office Building",
                assetPath = pick("building-a", "building-b", "building-c"),
                cost = 3000,
                populationImpact = 5,
                happinessImpact = 2,
                powerImpact = -15,
                waterImpact = -5,
                wasteImpact = 4
            ),
            BuildingDefinition(
                id = "commercial-skyscraper",
                category = BuildingCategory.Commercial,
                title = "IT Skyscraper",
                assetPath = pick("skyscraper-a", "skyscraper-b", "skyscraper-c"),
                cost = 12000,
                populationImpact = 15,
                happinessImpact = 5,
                powerImpact = -45,
                waterImpact = -25,
                wasteImpact = 12
            ),
            BuildingDefinition(
                id = "government-secretariat",
                category = BuildingCategory.Government,
                title = "Secretariat",
                assetPath = pick("building-skyscraper-d", "building-skyscraper-e"),
                cost = 15000,
                happinessImpact = 10,
                sustainabilityImpact = 5,
                powerImpact = -50,
                waterImpact = -30,
                wasteImpact = 10
            ),
            BuildingDefinition(
                id = "emergency-police",
                category = BuildingCategory.Emergency,
                title = "Police Hub",
                assetPath = pick("police", "building-f"),
                cost = 4500,
                happinessImpact = 12,
                powerImpact = -10,
                waterImpact = -5,
                wasteImpact = 2
            ),
            BuildingDefinition(
                id = "emergency-hospital",
                category = BuildingCategory.Emergency,
                title = "City Hospital",
                assetPath = pick("ambulance", "medical", "building-g"),
                cost = 7500,
                happinessImpact = 20,
                powerImpact = -30,
                waterImpact = -25,
                wasteImpact = 10
            ),
            BuildingDefinition(
                id = "industrial-warehouse",
                category = BuildingCategory.Industrial,
                title = "Logistics Hub",
                assetPath = pick("building-o", "building-p"),
                cost = 3500,
                powerImpact = -10,
                waterImpact = -5,
                wasteImpact = 8
            ),
            BuildingDefinition(
                id = "industrial-factory-heavy",
                category = BuildingCategory.Industrial,
                title = "Heavy Industry",
                assetPath = pick("chimney-large", "building-r", "building-s"),
                cost = 9000,
                populationImpact = 5,
                happinessImpact = -8,
                sustainabilityImpact = -10,
                powerImpact = -80,
                waterImpact = -60,
                wasteImpact = 40
            ),
            BuildingDefinition(
                id = "utility-solar-farm",
                category = BuildingCategory.Utilities,
                title = "Solar Grid",
                assetPath = pick("solar", "panel"),
                cost = 5500,
                sustainabilityImpact = 12,
                powerImpact = 120
            ),
            BuildingDefinition(
                id = "utility-water-tower",
                category = BuildingCategory.Utilities,
                title = "Water Tower",
                assetPath = pick("tank", "tower"),
                cost = 4000,
                waterImpact = 90,
                powerImpact = -5
            ),
            BuildingDefinition(
                id = "utility-recycling",
                category = BuildingCategory.Utilities,
                title = "Recycling Plant",
                assetPath = pick("waste", "recycling"),
                cost = 6000,
                sustainabilityImpact = 8,
                wasteImpact = -100,
                powerImpact = -20
            ),
            BuildingDefinition(
                id = "infrastructure-road",
                category = BuildingCategory.Infrastructure,
                title = "Smart Road",
                assetPath = pick("road-straight", "road-bend"),
                cost = 500,
                sustainabilityImpact = 1,
                powerImpact = -1
            ),
            BuildingDefinition(
                id = "green-central-park",
                category = BuildingCategory.GreenSpace,
                title = "Central Park",
                assetPath = pick("tree", "park"),
                cost = 1200,
                happinessImpact = 12,
                sustainabilityImpact = 15,
                waterImpact = -5
            )
        )
    }
}
