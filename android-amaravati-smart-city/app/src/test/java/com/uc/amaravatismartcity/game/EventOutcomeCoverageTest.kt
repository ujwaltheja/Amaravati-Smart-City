package com.uc.amaravatismartcity.game

import org.junit.Assert.assertEquals
import org.junit.Test

class EventOutcomeCoverageTest {
    @Test
    fun resolveEventOutcome_handles_default_and_green_education_cases() {
        val neutral = resolveEventOutcome("Unknown bulletin", EmergencyType.Fire)
        assertEquals(0, neutral.happinessDelta)
        assertEquals(0L, neutral.moneyDelta)
        assertEquals(null, neutral.activeEmergency)

        val green = resolveEventOutcome("Citizens praise new green corridor.", EmergencyType.Fire)
        assertEquals(6, green.happinessDelta)
        assertEquals(4, green.sustainabilityDelta)

        val education = resolveEventOutcome("School exam results high - education impact +3.", EmergencyType.Fire)
        assertEquals(3, education.happinessDelta)
        assertEquals(2, education.sustainabilityDelta)
    }
}

