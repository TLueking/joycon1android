package com.joegec.joycon2android.connection.evdev

import com.joegec.joycon2android.connection.ControllerTypePreferences
import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.ControllerModel
import com.joegec.joycon2android.model.JoyconButton
import com.joegec.joycon2android.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class Joycon1ManagerTest {

    private val relayOutput = PipedOutputStream()
    private val stream = FakeStream(PipedInputStream(relayOutput))
    private val manager = Joycon1Manager(
        CoroutineScope(Dispatchers.Unconfined),
        { _, onResult -> onResult(stream) },
        FakeTypePreferences(),
        { product, _ -> turnedOff += product; true },
        Joycon1Messages("needs shizuku", "turn off failed"),
    )
    private val turnedOff = java.util.concurrent.CopyOnWriteArrayList<EvdevProduct>()

    @After
    fun closeRelay() = relayOutput.close()

    @Test
    fun `an added Joy-Con appears ready on its side`() {
        relay("A 7 2006 98:b6:e9:00:00:01 Nintendo_Switch_Left_Joy-Con")

        val joycon = awaitController { it.isNotEmpty() }.single()
        assertEquals("98:b6:e9:00:00:01", joycon.address)
        assertEquals(Side.LEFT, joycon.side)
        assertEquals(ControllerModel.JOYCON_LEFT, joycon.model)
        assertTrue(joycon.ready)
    }

    @Test
    fun `input lands on sync`() {
        relay("A 6 2007 - Nintendo_Switch_Right_Joy-Con", "E 6 1 308 1", "S 6")

        val joycon = awaitController { list -> list.any { JoyconButton.Y.id in it.input.pressed } }.single()
        assertEquals("switch1-joycon_right", joycon.address)
    }

    @Test
    fun `a removed controller disappears`() {
        relay("A 6 2007 - Right", "R 6")

        awaitController { it.isEmpty() }
    }

    @Test
    fun `a disconnected controller stays hidden until a new scan`() {
        relay("A 6 2007 - Right")
        awaitController { it.isNotEmpty() }

        manager.disconnect("switch1-joycon_right")
        assertTrue(manager.controllers.value.isEmpty())
        manager.startScan()
        assertEquals(1, manager.controllers.value.size)
    }

    @Test
    fun `a second controller of the same type gets its own fallback address`() {
        relay("A 6 2007 - Right", "A 8 2007 - Right")

        val addresses = awaitController { it.size == 2 }.map { it.address }
        assertEquals(listOf("switch1-joycon_right", "switch1-joycon_right-2"), addresses)
    }

    @Test
    fun `disconnecting a Joy-Con turns off its type`() {
        relay("A 7 2006 - Left")
        awaitController { it.isNotEmpty() }

        manager.disconnect("switch1-joycon_left")

        awaitTrue { turnedOff == listOf(EvdevProduct.JOYCON_LEFT) }
    }

    @Test
    fun `disconnect all keeps grabbing, so a woken Joy-Con comes back unassigned`() {
        relay("A 7 2006 - Left")
        awaitController { it.isNotEmpty() }

        manager.disconnectAll()
        assertTrue(manager.controllers.value.isEmpty())
        relay("R 7", "A 9 2006 - Left")

        awaitController { it.size == 1 }
        assertTrue(stream.open)
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out")
            Thread.sleep(10)
        }
    }

    private fun relay(vararg lines: String) {
        relayOutput.write(lines.joinToString("\n", postfix = "\n").toByteArray())
        relayOutput.flush()
    }

    private fun awaitController(condition: (List<ConnectedJoycon>) -> Boolean): List<ConnectedJoycon> {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            manager.controllers.value.let { if (condition(it)) return it }
            Thread.sleep(10)
        }
        throw AssertionError("Timed out; controllers were ${manager.controllers.value}")
    }

    private class FakeStream(override val input: InputStream) : EvdevStream {
        @Volatile
        var open = true

        override fun close() {
            open = false
            input.close()
        }
    }

    private class FakeTypePreferences : ControllerTypePreferences {
        override val overrides = MutableStateFlow(emptyMap<String, ControllerModel>())
        override suspend fun set(address: String, model: ControllerModel?) {
            overrides.value = if (model == null) overrides.value - address else overrides.value + (address to model)
        }
    }
}
