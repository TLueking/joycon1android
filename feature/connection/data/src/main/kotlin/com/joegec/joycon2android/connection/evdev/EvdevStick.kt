package com.joegec.joycon2android.connection.evdev

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * hid-nintendo's calibrated ±32767 axes, up negative, to the app's 0–4095 with up high.
 * A radial deadzone absorbs rest drift: docs/switch1.md#sticks
 */
internal object EvdevStick {

    private const val AXIS_MAX = 32767.0
    private const val CENTER = 2048
    private const val HALF_RANGE = 2047
    const val DEADZONE = 0.12

    fun scale(x: Int, y: Int): Pair<Int, Int> {
        val nx = (x / AXIS_MAX).coerceIn(-1.0, 1.0)
        val ny = (-y / AXIS_MAX).coerceIn(-1.0, 1.0)
        val magnitude = hypot(nx, ny)
        if (magnitude <= DEADZONE) return CENTER to CENTER
        val rescaled = ((magnitude - DEADZONE) / (1 - DEADZONE)).coerceAtMost(1.0) / magnitude
        return toRange(nx * rescaled) to toRange(ny * rescaled)
    }

    private fun toRange(unit: Double): Int = CENTER + (unit * HALF_RANGE).roundToInt()
}
