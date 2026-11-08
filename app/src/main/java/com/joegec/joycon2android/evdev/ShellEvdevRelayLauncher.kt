package com.joegec.joycon2android.evdev

import android.content.Context
import android.util.Log
import com.joegec.joycon2android.connection.evdev.EvdevRelayLauncher
import com.joegec.joycon2android.connection.evdev.EvdevStream
import com.joegec.joycon2android.gamepad.RelayBinary
import com.joegec.joycon2android.gamepad.privileged.PrivilegedShell
import kotlin.concurrent.thread

class ShellEvdevRelayLauncher(
    private val context: Context,
    private val acquire: ((PrivilegedShell?) -> Unit) -> Unit,
    private val readyShell: () -> PrivilegedShell?,
) : EvdevRelayLauncher {

    override fun launch(prompt: Boolean, onResult: (EvdevStream?) -> Unit) {
        if (prompt) acquire { start(it, onResult) } else start(readyShell(), onResult)
    }

    private fun start(shell: PrivilegedShell?, onResult: (EvdevStream?) -> Unit) {
        if (shell == null) return onResult(null)
        thread(name = "evdev-relay-start", isDaemon = true) {
            val process = runCatching { shell.newProcess(arrayOf(BINARY.deploy(context, shell))) }
                .onFailure { Log.e(TAG, "Failed to start evdev relay", it) }
                .getOrNull()
            onResult(process?.let(::ShellEvdevStream))
        }
    }

    private companion object {
        const val TAG = "ShellEvdevRelayLauncher"
        val BINARY = RelayBinary("evdev_relay", "/data/local/tmp/.evdev_relay")
    }
}
