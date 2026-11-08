package com.joegec.joycon2android.connection.evdev

fun interface EvdevRelayLauncher {
    /** [prompt] false starts only if privileged access is already granted. Null when it can't start. */
    fun launch(prompt: Boolean, onResult: (EvdevStream?) -> Unit)
}
