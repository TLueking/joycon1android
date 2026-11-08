package com.joegec.joycon2android.connection.evdev

import java.io.InputStream

/** A running `evdev_relay`: its output, and a way to stop it, which releases every grab. */
interface EvdevStream {
    val input: InputStream
    fun close()
}
