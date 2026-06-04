package com.uc.amaravatismartcity.game


data class EventOutcome(
    val happinessDelta: Int = 0,
    val trafficDelta: Int = 0,
    val moneyDelta: Long = 0,
    val powerDelta: Int = 0,
    val pollutionDelta: Int = 0,
    val sustainabilityDelta: Int = 0,
    val activeEmergency: String? = null
)

fun selectEvent(
    emergencyRoll: Float,
    emergencyOptions: List<EmergencyType>,
    emergencyIndex: Int,
    eventPool: List<String>,
    poolIndex: Int
): Pair<EmergencyType, String> {
    require(emergencyOptions.isNotEmpty()) { "Emergency options cannot be empty." }
    val emergency = emergencyOptions[emergencyIndex.mod(emergencyOptions.size)]
    val event = if (emergencyRoll < 0.45f || eventPool.isEmpty()) {
        emergency.displayName
    } else {
        eventPool[poolIndex.mod(eventPool.size)]
    }
    return emergency to event
}

fun resolveEventOutcome(event: String, emergency: EmergencyType): EventOutcome {
    return when {
        event == emergency.displayName -> EventOutcome(
            activeEmergency = emergency.displayName,
            happinessDelta = -2,
            pollutionDelta = if (emergency == EmergencyType.Spill) 8 else 0,
            powerDelta = if (emergency == EmergencyType.Outage) -10 else 0
        )
        event.contains("monsoon") || event.contains("Traffic") -> EventOutcome(happinessDelta = -4, trafficDelta = 12)
        event.contains("investor") || event.contains("festival") -> EventOutcome(moneyDelta = 1800, happinessDelta = 5)
        event.contains("Power") -> EventOutcome(powerDelta = -8)
        event.contains("spill") || event.contains("Pollution") -> EventOutcome(pollutionDelta = 7, happinessDelta = -2)
        event.contains("green") -> EventOutcome(happinessDelta = 6, sustainabilityDelta = 4)
        event.contains("education") -> EventOutcome(happinessDelta = 3, sustainabilityDelta = 2)
        else -> EventOutcome()
    }
}

