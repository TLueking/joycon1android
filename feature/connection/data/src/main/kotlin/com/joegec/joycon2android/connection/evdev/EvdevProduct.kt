package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.model.ControllerModel

/** hid-nintendo's Switch 1 product IDs, Nintendo vendor 0x057e. */
enum class EvdevProduct(val productId: Int, val model: ControllerModel) {
    JOYCON_LEFT(0x2006, ControllerModel.JOYCON_LEFT),
    JOYCON_RIGHT(0x2007, ControllerModel.JOYCON_RIGHT),
    PRO_CONTROLLER(0x2009, ControllerModel.PRO_CONTROLLER),
    ;

    companion object {
        fun of(productId: Int): EvdevProduct? = entries.firstOrNull { it.productId == productId }
    }
}
