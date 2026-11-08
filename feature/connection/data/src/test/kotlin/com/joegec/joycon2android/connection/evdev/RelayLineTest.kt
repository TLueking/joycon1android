package com.joegec.joycon2android.connection.evdev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayLineTest {

    @Test
    fun `parses an added controller with its address and spaced name`() {
        assertEquals(
            RelayLine.Added(7, 0x2006, "98:b6:e9:00:00:01", "Nintendo Switch Left Joy-Con"),
            RelayLine.parse("A 7 2006 98:b6:e9:00:00:01 Nintendo_Switch_Left_Joy-Con"),
        )
    }

    @Test
    fun `a dash means the relay found no address`() {
        assertNull((RelayLine.parse("A 7 2006 - Name") as RelayLine.Added).address)
    }

    @Test
    fun `parses negative axis values`() {
        assertEquals(RelayLine.Event(6, 3, 4, -938), RelayLine.parse("E 6 3 4 -938"))
    }

    @Test
    fun `parses sync, removal and ready`() {
        assertEquals(RelayLine.Sync(6), RelayLine.parse("S 6"))
        assertEquals(RelayLine.Removed(6), RelayLine.parse("R 6"))
        assertEquals(RelayLine.Ready, RelayLine.parse("OK"))
    }

    @Test
    fun `ignores malformed lines`() {
        assertNull(RelayLine.parse("E 6 3 x 1"))
        assertNull(RelayLine.parse("A 7"))
        assertNull(RelayLine.parse("garbage"))
    }
}
