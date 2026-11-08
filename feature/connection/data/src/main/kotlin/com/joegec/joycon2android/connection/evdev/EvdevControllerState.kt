package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_HAT0X
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_HAT0Y
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_RX
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_RY
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_X
import com.joegec.joycon2android.connection.evdev.EvdevCodes.ABS_Y
import com.joegec.joycon2android.connection.evdev.EvdevCodes.EV_ABS
import com.joegec.joycon2android.connection.evdev.EvdevCodes.EV_KEY
import com.joegec.joycon2android.model.JoyconButton
import com.joegec.joycon2android.model.JoyconInput
import com.joegec.joycon2android.model.MotionSupport

/** Accumulates one controller's events between SYN_REPORTs. */
class EvdevControllerState(private val product: EvdevProduct) {

    private val keys = mutableSetOf<JoyconButton>()
    private val axes = IntArray(ABS_HAT0Y + 1)
    private var packetId = 0

    fun apply(type: Int, code: Int, value: Int) {
        when (type) {
            EV_KEY -> EvdevButtons.of(code)?.let { if (value != 0) keys += it else keys -= it }
            EV_ABS -> if (code in axes.indices) axes[code] = value
        }
    }

    fun snapshot(): JoyconInput {
        val (leftX, leftY) = EvdevStick.scale(axes[ABS_X], axes[ABS_Y])
        val (rightX, rightY) = EvdevStick.scale(axes[ABS_RX], axes[ABS_RY])
        val ownStick = if (product == EvdevProduct.JOYCON_RIGHT) rightX to rightY else leftX to leftY
        return JoyconInput(
            packetId = ++packetId,
            pressed = (keys + hatButtons()).mapTo(mutableSetOf()) { it.id },
            stickX = ownStick.first,
            stickY = ownStick.second,
            rightStickX = rightX,
            rightStickY = rightY,
            motionSupport = MotionSupport.None,
        )
    }

    private fun hatButtons(): Set<JoyconButton> = buildSet {
        when {
            axes[ABS_HAT0X] < 0 -> add(JoyconButton.Left)
            axes[ABS_HAT0X] > 0 -> add(JoyconButton.Right)
        }
        when {
            axes[ABS_HAT0Y] < 0 -> add(JoyconButton.Up)
            axes[ABS_HAT0Y] > 0 -> add(JoyconButton.Down)
        }
    }
}
