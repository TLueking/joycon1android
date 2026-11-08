package com.joegec.joycon2android.evdev

import com.joegec.joycon2android.connection.evdev.EvdevStream
import com.joegec.joycon2android.gamepad.privileged.ShellProcess
import java.io.IOException
import java.io.InputStream

class ShellEvdevStream(private val process: ShellProcess) : EvdevStream {

    override val input: InputStream get() = process.inputStream

    // Closing stdin is the relay's signal to release its grabs and exit.
    override fun close() {
        try {
            process.outputStream.close()
        } catch (_: IOException) {
        }
        process.destroy()
    }
}
