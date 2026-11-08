package com.joegec.joycon2android.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class UnassignedTimeoutTest {

    private var time = 0L
    private val timeout = UnassignedTimeout(timeoutMs = 1_000) { time }

    @Test
    fun `a controller unassigned past the timeout is due`() {
        timeout.due(setOf("a"))
        time = 1_000

        assertEquals(listOf("a"), timeout.due(setOf("a")))
    }

    @Test
    fun `nothing is due before the timeout`() {
        timeout.due(setOf("a"))
        time = 999

        assertEquals(emptyList<String>(), timeout.due(setOf("a")))
    }

    @Test
    fun `assigning resets the clock`() {
        timeout.due(setOf("a"))
        time = 900
        timeout.due(emptySet())
        time = 1_500

        assertEquals(emptyList<String>(), timeout.due(setOf("a")))
    }

    @Test
    fun `a due controller isn't due again until it leaves and returns`() {
        timeout.due(setOf("a"))
        time = 1_000
        timeout.due(setOf("a"))
        time = 5_000

        assertEquals(emptyList<String>(), timeout.due(setOf("a")))
        timeout.due(emptySet())
        timeout.due(setOf("a"))
        time = 6_000
        assertEquals(listOf("a"), timeout.due(setOf("a")))
    }
}
