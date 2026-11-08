package com.joegec.joycon2android.connection.evdev

data class EvdevController(
    val slot: Int,
    val address: String,
    val product: EvdevProduct,
    val name: String,
    val state: EvdevControllerState = EvdevControllerState(product),
)
