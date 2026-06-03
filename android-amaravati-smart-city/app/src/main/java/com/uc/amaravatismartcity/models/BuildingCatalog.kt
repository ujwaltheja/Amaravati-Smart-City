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
                id = "residential-apartment",
                category = BuildingCategory.Residential,
                title = "Residential",
                assetPath = pick("house", "home", "apartment", "residential", "flat"),
                cost = 1500,
                populationImpact = 20,
                happinessImpact = 2,
                powerImpact = -5,
                waterImpact = -4,
                wasteImpact = 3
            ),
            BuildingDefinition(
                id = "commercial-it-park",
                category = BuildingCategory.Commercial,
                title = "Commercial / IT",
                assetPath = pick("office", "commercial", "mall", "business", "it"),
                cost = 4000,
                populationImpact = 10,
                happinessImpact = 1,
                powerImpact = -12,
                waterImpact = -8,
                wasteImpact = 6
            ),
            BuildingDefinition(
                id = "government-node",
                category = BuildingCategory.Government,
                title = "Government",
                assetPath = pick("gov", "government", "secretariat", "assembly", "public"),
                cost = 5000,
                happinessImpact = 3,
                sustainabilityImpact = 1,
                powerImpact = -15,
                waterImpact = -10,
                wasteImpact = 4
            ),
            BuildingDefinition(
                id = "emergency-hospital",
                category = BuildingCategory.Emergency,
                title = "City Hospital",
                assetPath = pick("ambulance", "hospital", "medical"),
                cost = 3500,
                happinessImpact = 8,
                sustainabilityImpact = 2,
                powerImpact = -20,
                waterImpact = -15,
                wasteImpact = 8
            ),
            BuildingDefinition(
                id = "education-school",
                category = BuildingCategory.Education,
                title = "Smart School",
                assetPath = pick("school", "education", "university"),
                cost = 2800,
                populationImpact = 5,
                happinessImpact = 6,
                sustainabilityImpact = 3,
                powerImpact = -8,
                waterImpact = -6,
                wasteImpact = 4
            ),
            BuildingDefinition(
                id = "infrastructure-road",
                category = BuildingCategory.Infrastructure,
                title = "Road Segment",
                assetPath = pick("road-straight", "road-square", "road-bend", "tile-low", "bridge"),
                cost = 850,
                sustainabilityImpact = 2,
                powerImpact = -1
            ),
            BuildingDefinition(
                id = "green-park",
                category = BuildingCategory.GreenSpace,
                title = "Green Space",
                assetPath = pick("tree", "park", "garden", "green"),
                cost = 700,
                happinessImpact = 5,
                sustainabilityImpact = 6,
                waterImpact = -2
            ),
            BuildingDefinition(
                id = "riverfront-plaza",
                category = BuildingCategory.Riverfront,
                title = "Riverfront",
                assetPath = pick("river", "waterfront", "ghat", "promenade", "embankment"),
                cost = 2500,
                happinessImpact = 4,
                sustainabilityImpact = 3,
                waterImpact = -5
            ),
            BuildingDefinition(
                id = "industrial-factory",
                category = BuildingCategory.Industrial,
                title = "Industrial Zone",
                assetPath = pick("factory", "industrial", "chimney", "tank"),
                cost = 6000,
                populationImpact = 5,
                happinessImpact = -2,
                sustainabilityImpact = -5,
                powerImpact = -40,
                waterImpact = -30,
                wasteImpact = 25
            ),
            BuildingDefinition(
                id = "transport-metro",
                category = BuildingCategory.Transport,
                title = "Metro Station",
                assetPath = pick("metro", "train", "tram", "subway"),
                cost = 8000,
                happinessImpact = 10,
                sustainabilityImpact = 8,
                powerImpact = -50
            ),
            BuildingDefinition(
                id = "utility-water-pump",
                category = BuildingCategory.Utilities,
                title = "Water Treatment",
                assetPath = pick("water", "pump", "tank", "buoy"),
                cost = 3200,
                happinessImpact = 2,
                sustainabilityImpact = 5,
                waterImpact = 60,
                powerImpact = -10
            ),
            BuildingDefinition(
                id = "utility-solar-farm",
                category = BuildingCategory.Utilities,
                title = "Solar Farm",
                assetPath = pick("solar", "panel", "energy", "generator"),
                cost = 4500,
                sustainabilityImpact = 10,
                powerImpact = 80
            ),
            BuildingDefinition(
                id = "utility-waste-plant",
                category = BuildingCategory.Utilities,
                title = "Waste Management",
                assetPath = pick("waste", "recycling", "garbage", "trash"),
                cost = 3800,
                sustainabilityImpact = 4,
                wasteImpact = -50,
                powerImpact = -5
            )
        )
    }
}
