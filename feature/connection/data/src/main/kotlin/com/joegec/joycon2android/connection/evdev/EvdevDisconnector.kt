package com.joegec.joycon2android.connection.evdev

/** Drops a Switch 1 controller's Bluetooth link, which puts it to sleep: docs/switch1.md#turning-off */
fun interface EvdevDisconnector {
    /**
     * Blocks on a binder call. [everyMatch] false refuses when several of [product] are connected,
     * since they can't be told apart. False when nothing was dropped.
     */
    fun disconnect(product: EvdevProduct, everyMatch: Boolean): Boolean
}
