package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.model.ControllerModel

/** hid-nintendo's Switch 1 product IDs, Nintendo vendor 0x057e, and the name each advertises. */
enum class EvdevProduct(val productId: Int, val model: ControllerModel, val bluetoothName: String) {
    JOYCON_LEFT(0x2006, ControllerModel.JOYCON_LEFT, "Joy-Con (L)"),
    JOYCON_RIGHT(0x2007, ControllerModel.JOYCON_RIGHT, "Joy-Con (R)"),
    PRO_CONTROLLER(0x2009, ControllerModel.PRO_CONTROLLER, "Pro Controller"),
    ;

    companion object {
        fun of(productId: Int): EvdevProduct? = entries.firstOrNull { it.productId == productId }
    }
}
