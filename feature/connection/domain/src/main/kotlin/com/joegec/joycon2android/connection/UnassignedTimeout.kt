package com.joegec.joycon2android.connection

/** Which controllers have stayed unassigned for [timeoutMs], so they can be put back to sleep. */
class UnassignedTimeout(private val timeoutMs: Long, private val now: () -> Long) {

    private val unassignedSince = mutableMapOf<String, Long>()

    /** Each address is due once per unassigned stretch; assigning or losing it resets its clock. */
    fun due(unassigned: Set<String>): List<String> {
        val time = now()
        unassignedSince.keys.retainAll(unassigned)
        unassigned.forEach { unassignedSince.putIfAbsent(it, time) }
        val expired = unassignedSince.filterValues { time - it >= timeoutMs }.keys.toList()
        expired.forEach { unassignedSince[it] = Long.MAX_VALUE }
        return expired
    }
}
