package com.joegec.joycon2android.connection.evdev

/** One line of `evdev_relay` output: docs/switch1.md#evdev-relay */
sealed interface RelayLine {
    data object Ready : RelayLine
    data class Added(val slot: Int, val productId: Int, val address: String?, val name: String) : RelayLine
    data class Event(val slot: Int, val type: Int, val code: Int, val value: Int) : RelayLine
    data class Sync(val slot: Int) : RelayLine
    data class Removed(val slot: Int) : RelayLine

    companion object {
        fun parse(line: String): RelayLine? {
            val parts = line.split(' ', limit = 5)
            val slot = parts.getOrNull(1)?.toIntOrNull()
            return when (parts[0]) {
                "OK" -> Ready
                "A" -> added(parts, slot)
                "E" -> event(parts, slot)
                "S" -> slot?.let(::Sync)
                "R" -> slot?.let(::Removed)
                else -> null
            }
        }

        private fun added(parts: List<String>, slot: Int?): Added? {
            if (slot == null || parts.size < 5) return null
            val productId = parts[2].toIntOrNull(16) ?: return null
            val address = parts[3].takeUnless { it == "-" }
            return Added(slot, productId, address, parts[4].replace('_', ' '))
        }

        private fun event(parts: List<String>, slot: Int?): Event? {
            if (slot == null || parts.size < 5) return null
            val type = parts[2].toIntOrNull() ?: return null
            val code = parts[3].toIntOrNull() ?: return null
            val value = parts[4].toIntOrNull() ?: return null
            return Event(slot, type, code, value)
        }
    }
}
