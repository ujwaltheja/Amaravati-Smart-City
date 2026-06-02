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
                happinessImpact = 2
            ),
            BuildingDefinition(
                id = "commercial-it-park",
                category = BuildingCategory.Commercial,
                title = "Commercial / IT",
                assetPath = pick("office", "commercial", "mall", "business", "it"),
                cost = 4000,
                populationImpact = 10,
                happinessImpact = 1
            ),
            BuildingDefinition(
                id = "government-node",
                category = BuildingCategory.Government,
                title = "Government",
                assetPath = pick("gov", "government", "secretariat", "assembly", "public"),
                cost = 5000,
                happinessImpact = 3,
                sustainabilityImpact = 1
            ),
            BuildingDefinition(
                id = "infrastructure-road",
                category = BuildingCategory.Infrastructure,
                title = "Infrastructure",
                assetPath = pick("road", "bridge", "metro", "infrastructure", "flyover"),
                cost = 900,
                sustainabilityImpact = 1
            ),
            BuildingDefinition(
                id = "green-park",
                category = BuildingCategory.GreenSpace,
                title = "Green Space",
                assetPath = pick("tree", "park", "garden", "green"),
                cost = 700,
                happinessImpact = 5,
                sustainabilityImpact = 6
            ),
            BuildingDefinition(
                id = "riverfront-plaza",
                category = BuildingCategory.Riverfront,
                title = "Riverfront",
                assetPath = pick("river", "waterfront", "ghat", "promenade", "embankment"),
                cost = 2500,
                happinessImpact = 4,
                sustainabilityImpact = 3
            )
        )
    }
}
