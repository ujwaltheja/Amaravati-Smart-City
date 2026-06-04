package com.uc.amaravatismartcity.game

import org.junit.Assert.assertEquals
import org.junit.Test

class EventLogicTest {
    @Test
    fun selectEvent_prefers_emergency_under_threshold_and_pool_when_above() {
        val (emergencyA, eventA) = selectEvent(
            emergencyRoll = 0.2f,
            emergencyOptions = EmergencyType.entries,
            emergencyIndex = 2,
            eventPool = listOf("event-1", "event-2"),
            poolIndex = 1
        )
        assertEquals(EmergencyType.Accident, emergencyA)
        assertEquals(EmergencyType.Accident.displayName, eventA)

        val (emergencyB, eventB) = selectEvent(
            emergencyRoll = 0.8f,
            emergencyOptions = EmergencyType.entries,
            emergencyIndex = 3,
            eventPool = listOf("event-1", "event-2"),
            poolIndex = 1
        )
        assertEquals(EmergencyType.Flood, emergencyB)
        assertEquals("event-2", eventB)
    }

    @Test
    fun resolveEventOutcome_maps_investor_festival_and_emergency_events() {
        val investor = resolveEventOutcome("Tech investor visit: Commercial demand up.", EmergencyType.Fire)
        assertEquals(1800, investor.moneyDelta)
        assertEquals(5, investor.happinessDelta)

        val monsoon = resolveEventOutcome("Heavy monsoon rain: Traffic slowed, happiness -4.", EmergencyType.Fire)
        assertEquals(-4, monsoon.happinessDelta)
        assertEquals(12, monsoon.trafficDelta)

        val outage = resolveEventOutcome(EmergencyType.Outage.displayName, EmergencyType.Outage)
        assertEquals("Power outage", outage.activeEmergency)
        assertEquals(-2, outage.happinessDelta)
        assertEquals(-10, outage.powerDelta)
        assertEquals(0, outage.pollutionDelta)

        val spill = resolveEventOutcome(EmergencyType.Spill.displayName, EmergencyType.Spill)
        assertEquals("Industrial spill", spill.activeEmergency)
        assertEquals(8, spill.pollutionDelta)
    }
}

