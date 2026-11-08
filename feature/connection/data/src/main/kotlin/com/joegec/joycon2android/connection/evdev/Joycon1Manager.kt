package com.joegec.joycon2android.connection.evdev

import android.util.Log
import com.joegec.joycon2android.connection.ControllerRepository
import com.joegec.joycon2android.connection.ControllerTypePreferences
import com.joegec.joycon2android.connection.VibrationSample
import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.JoyconInput
import com.joegec.joycon2android.model.MotionSupport
import com.joegec.joycon2android.model.PlayerNumber
import com.joegec.joycon2android.model.Rumble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.concurrent.thread

/** Switch 1 controllers paired in Android's Bluetooth settings, read through `evdev_relay`. */
class Joycon1Manager(
    private val scope: CoroutineScope,
    private val launcher: EvdevRelayLauncher,
    private val typePreferences: ControllerTypePreferences,
    private val disconnector: EvdevDisconnector,
    private val messages: Joycon1Messages,
) : ControllerRepository {

    private val typeOverrides = typePreferences.overrides.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    private val lock = Any()
    private val bySlot = mutableMapOf<Int, EvdevController>()
    private val inputs = mutableMapOf<Int, JoyconInput>()
    private val hidden = mutableSetOf<String>()

    @Volatile
    private var stream: EvdevStream? = null

    @Volatile
    private var starting = false

    private val _controllers = MutableStateFlow<List<ConnectedJoycon>>(emptyList())
    override val controllers: StateFlow<List<ConnectedJoycon>> = _controllers.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    init {
        start(prompt = false)
        scope.launch { typeOverrides.collect { rebuild() } }
    }

    override fun startScan() {
        synchronized(lock) { hidden.clear() }
        rebuild()
        start(prompt = true)
    }

    override fun stopScan() {
        _scanning.value = false
    }

    override fun disconnect(address: String) {
        val product = synchronized(lock) {
            hidden += address
            bySlot.values.firstOrNull { it.address == address }?.product
        }
        rebuild()
        product?.let { turnOff(listOf(it), everyMatch = false) }
    }

    override fun disconnectAll() {
        turnOff(synchronized(lock) { bySlot.values.map { it.product } }, everyMatch = true)
        stream?.close()
        stream = null
        synchronized(lock) {
            bySlot.clear()
            inputs.clear()
        }
        _error.value = null
        rebuild()
    }

    override fun setPlayerLed(address: String, player: PlayerNumber?) = Unit

    override fun vibrate(address: String, sample: VibrationSample) = Unit

    override fun rumble(address: String, rumble: Rumble) = Unit

    override fun setControllerType(address: String, model: ControllerModel?) {
        scope.launch { typePreferences.set(address, model) }
    }

    override fun emitError(message: String) {
        _error.value = message
    }

    // Hidden first, so the card goes at once; the relay's removal follows when the link drops.
    private fun turnOff(products: List<EvdevProduct>, everyMatch: Boolean) {
        if (products.isEmpty()) return
        thread(name = "evdev-turn-off", isDaemon = true) {
            val failed = products.distinct().filterNot { disconnector.disconnect(it, everyMatch) }
            if (failed.isNotEmpty()) _error.value = messages.turnOffFailed
        }
    }

    private fun start(prompt: Boolean) {
        if (stream != null || starting) return
        starting = true
        if (prompt) _scanning.value = true
        launcher.launch(prompt) { started ->
            starting = false
            _scanning.value = false
            when {
                started != null -> {
                    _error.value = null
                    stream = started
                    thread(name = "evdev-relay", isDaemon = true) { read(started) }
                }
                prompt -> _error.value = messages.shizukuMissing
            }
        }
    }

    private fun read(source: EvdevStream) {
        try {
            source.input.bufferedReader().forEachLine { line -> RelayLine.parse(line)?.let(::handle) }
        } catch (e: IOException) {
            Log.w(TAG, "evdev relay stream ended", e)
        }
        if (stream === source) {
            stream = null
            synchronized(lock) {
                bySlot.clear()
                inputs.clear()
            }
            rebuild()
        }
    }

    private fun handle(line: RelayLine) {
        when (line) {
            RelayLine.Ready -> Log.i(TAG, "evdev relay running")
            is RelayLine.Added -> added(line)
            is RelayLine.Event -> synchronized(lock) { bySlot[line.slot]?.state?.apply(line.type, line.code, line.value) }
            is RelayLine.Sync -> synced(line.slot)
            is RelayLine.Removed -> removed(line.slot)
        }
    }

    private fun added(line: RelayLine.Added) {
        val product = EvdevProduct.of(line.productId) ?: return
        val controller = synchronized(lock) {
            val address = line.address ?: fallbackAddress(product)
            EvdevController(line.slot, address, product, line.name).also {
                bySlot[line.slot] = it
                hidden -= address
            }
        }
        Log.i(TAG, "Added ${controller.name} (${controller.address})")
        rebuild()
    }

    // Stable per product, so a reconnect keeps its type override when the address is unreadable.
    private fun fallbackAddress(product: EvdevProduct): String {
        val taken = bySlot.values.map { it.address }.toSet()
        val base = "switch1-${product.name.lowercase()}"
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
    }

    private fun synced(slot: Int) {
        synchronized(lock) {
            val controller = bySlot[slot] ?: return
            inputs[slot] = controller.state.snapshot()
        }
        rebuild()
    }

    private fun removed(slot: Int) {
        synchronized(lock) {
            bySlot.remove(slot)
            inputs.remove(slot)
        }
        rebuild()
    }

    private fun rebuild() {
        val overrides = typeOverrides.value
        _controllers.value = synchronized(lock) {
            bySlot.values
                .filter { it.address !in hidden }
                .sortedBy { it.slot }
                .map { it.toConnected(overrides[it.address], inputs[it.slot] ?: IDLE_INPUT) }
        }
    }

    private fun EvdevController.toConnected(typeOverride: ControllerModel?, input: JoyconInput): ConnectedJoycon {
        val model = typeOverride ?: product.model
        return ConnectedJoycon(
            address = address,
            side = model.defaultSide,
            deviceName = name,
            model = model,
            detectedModel = product.model,
            typeOverride = typeOverride,
            input = input,
            ready = true,
        )
    }

    private companion object {
        const val TAG = "Joycon1Manager"
        val IDLE_INPUT = JoyconInput(motionSupport = MotionSupport.None)
    }
}
