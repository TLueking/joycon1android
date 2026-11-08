package com.joegec.joycon2android.connection.evdev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvdevStickTest {

    @Test
    fun `rest drift inside the deadzone centres the stick`() {
        // 0x0ce6, measured at rest on a Pixel 3a
        assertEquals(2048 to 2048, EvdevStick.scale(3302, 0))
    }

    @Test
    fun `full right reaches the top of the range`() {
        assertEquals(4095 to 2048, EvdevStick.scale(32767, 0))
    }

    @Test
    fun `evdev up is negative and becomes high`() {
        assertEquals(2048 to 4095, EvdevStick.scale(0, -32767))
    }

    @Test
    fun `just past the deadzone starts near centre`() {
        val (x, _) = EvdevStick.scale((32767 * (EvdevStick.DEADZONE + 0.01)).toInt(), 0)
        assertTrue(x in 2049..2100)
    }

    @Test
    fun `diagonals are clamped to the rim`() {
        val (x, y) = EvdevStick.scale(32767, 32767)
        assertTrue(x in 3400..3500)
        assertTrue(y in 600..700)
    }
}
