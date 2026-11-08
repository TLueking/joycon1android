package com.joegec.joycon2android.connection

import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.PlayerNumber
import com.joegec.joycon2android.model.Rumble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Several controller sources as one; per-controller calls go to the source that owns the address. */
class CombinedControllers(scope: CoroutineScope, private val sources: List<ControllerRepository>) :
    ControllerRepository {

    override val controllers: StateFlow<List<ConnectedJoycon>> =
        combine(sources.map { it.controllers }) { lists -> lists.flatMap { it } }
            .stateIn(scope, SharingStarted.Eagerly, sources.flatMap { it.controllers.value })

    override val scanning: StateFlow<Boolean> =
        combine(sources.map { it.scanning }) { states -> states.any { it } }
            .stateIn(scope, SharingStarted.Eagerly, false)

    // A source finding nothing isn't an error while another source has controllers.
    override val error: StateFlow<String?> =
        combine(sources.map { source -> combine(source.error, source.controllers) { e, c -> e to c.isNotEmpty() } }) { states ->
            val anyConnected = states.any { (_, connected) -> connected }
            states.firstNotNullOfOrNull { (error, connected) -> error?.takeIf { connected || !anyConnected } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun startScan() = sources.forEach { it.startScan() }

    override fun stopScan() = sources.forEach { it.stopScan() }

    override fun disconnect(address: String) {
        ownerOf(address)?.disconnect(address)
    }

    override fun disconnectAll() = sources.forEach { it.disconnectAll() }

    override fun setPlayerLed(address: String, player: PlayerNumber?) {
        ownerOf(address)?.setPlayerLed(address, player)
    }

    override fun vibrate(address: String, sample: VibrationSample) {
        ownerOf(address)?.vibrate(address, sample)
    }

    override fun rumble(address: String, rumble: Rumble) {
        ownerOf(address)?.rumble(address, rumble)
    }

    override fun setControllerType(address: String, model: ControllerModel?) {
        ownerOf(address)?.setControllerType(address, model)
    }

    override fun emitError(message: String) = sources.first().emitError(message)

    private fun ownerOf(address: String): ControllerRepository? =
        sources.firstOrNull { source -> source.controllers.value.any { it.address == address } }
}
