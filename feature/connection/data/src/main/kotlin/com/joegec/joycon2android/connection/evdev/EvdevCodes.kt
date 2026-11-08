package com.joegec.joycon2android.connection.evdev

/** From linux/input-event-codes.h. */
internal object EvdevCodes {
    const val EV_KEY = 0x01
    const val EV_ABS = 0x03

    const val BTN_SOUTH = 0x130
    const val BTN_EAST = 0x131
    const val BTN_NORTH = 0x133
    const val BTN_WEST = 0x134
    const val BTN_Z = 0x135
    const val BTN_TL = 0x136
    const val BTN_TR = 0x137
    const val BTN_TL2 = 0x138
    const val BTN_TR2 = 0x139
    const val BTN_SELECT = 0x13a
    const val BTN_START = 0x13b
    const val BTN_MODE = 0x13c
    const val BTN_THUMBL = 0x13d
    const val BTN_THUMBR = 0x13e
    const val BTN_DPAD_UP = 0x220
    const val BTN_DPAD_DOWN = 0x221
    const val BTN_DPAD_LEFT = 0x222
    const val BTN_DPAD_RIGHT = 0x223

    const val ABS_X = 0x00
    const val ABS_Y = 0x01
    const val ABS_RX = 0x03
    const val ABS_RY = 0x04
    const val ABS_HAT0X = 0x10
    const val ABS_HAT0Y = 0x11
}
