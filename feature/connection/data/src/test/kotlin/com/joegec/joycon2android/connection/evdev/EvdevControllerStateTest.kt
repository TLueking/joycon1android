package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_HAT0Y
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_RX
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_X
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_EAST
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_NORTH
import com.joegec.joycon2android.connection.evdev.EvdevCodes.BTN_WEST
import com.joegec.joycon2android.connection.evdev.EvdevCodes.EV_ABS
import com.joegec.joycon2android.connection.evdev.EvdevCodes.EV_KEY
import com.joegec.joycon2android.model.JoyconButton
import org.junit.Assert.assertEquals
import org.junit.Test

class EvdevControllerStateTest {

    @Test
    fun `face buttons follow the measured codes`() {
        val state = EvdevControllerState(EvdevProduct.JOYCON_RIGHT)
        state.apply(EV_KEY, BTN_EAST, 1)
        state.apply(EV_KEY, BTN_NORTH, 1)
        state.apply(EV_KEY, BTN_WEST, 1)

        assertEquals(setOf(JoyconButton.A.id, JoyconButton.X.id, JoyconButton.Y.id), state.snapshot().pressed)
    }

    @Test
    fun `a release clears the button`() {
        val state = EvdevControllerState(EvdevProduct.JOYCON_RIGHT)
        state.apply(EV_KEY, BTN_EAST, 1)
        state.apply(EV_KEY, BTN_EAST, 0)

        assertEquals(emptySet<String>(), state.snapshot().pressed)
    }

    @Test
    fun `a right Joy-Con's stick is its own stick`() {
        val state = EvdevControllerState(EvdevProduct.JOYCON_RIGHT)
        state.apply(EV_ABS, ABS_RX, 32767)

        assertEquals(4095, state.snapshot().stickX)
    }

    @Test
    fun `a Pro Controller keeps both sticks`() {
        val state = EvdevControllerState(EvdevProduct.PRO_CONTROLLER)
        state.apply(EV_ABS, ABS_X, -32767)
        state.apply(EV_ABS, ABS_RX, 32767)

        val input = state.snapshot()
        assertEquals(1, input.stickX)
        assertEquals(4095, input.rightStickX)
    }

    @Test
    fun `a hat reports as d-pad buttons`() {
        val state = EvdevControllerState(EvdevProduct.PRO_CONTROLLER)
        state.apply(EV_ABS, ABS_HAT0Y, -1)

        assertEquals(setOf(JoyconButton.Up.id), state.snapshot().pressed)
    }
}
