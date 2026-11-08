package com.joegec.joycon2android.evdev

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.joegec.joycon2android.connection.evdev.EvdevDisconnector
import com.joegec.joycon2android.connection.evdev.EvdevProduct
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Asks the controller to sleep through the HID host; failing that, calls the host's disconnect through
 * Shizuku, which needs BLUETOOTH_PRIVILEGED (root, not shell). docs/switch1.md#turning-off
 */
class HidHostDisconnector(
    private val context: Context,
    private val shellReady: () -> Boolean,
) : EvdevDisconnector {

    @Volatile
    private var hidHost: BluetoothProfile? = null

    @Volatile
    private var bound = CountDownLatch(1)

    // Declared before init, which hands it to getProfileProxy; a null listener is refused silently.
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            hidHost = proxy
            bound.countDown()
        }

        override fun onServiceDisconnected(profile: Int) {
            hidHost = null
            bound = CountDownLatch(1)
        }
    }

    init {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
        bindHidHost()
    }

    override fun disconnect(product: EvdevProduct, everyMatch: Boolean): Boolean {
        val host = awaitHidHost() ?: return false.also { Log.w(TAG, "HID host service not connected") }
        return try {
            val matches = host.connectedDevices.filter { it.name == product.bluetoothName }
            if (matches.isEmpty() || (matches.size > 1 && !everyMatch)) {
                Log.w(TAG, "${matches.size} connected named ${product.bluetoothName}")
                return false
            }
            matches.all { sendSleep(host, it) || disconnectThroughShizuku(host, it) }
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "HID host refused the disconnect", e.targetException)
            false
        } catch (e: ReflectiveOperationException) {
            Log.e(TAG, "HID host disconnect unavailable", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "HID host refused the disconnect", e)
            false
        }
    }

    private fun awaitHidHost(): BluetoothProfile? {
        hidHost?.let { return it }
        bindHidHost()
        bound.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return hidHost
    }

    @SuppressLint("MissingPermission")
    private fun bindHidHost() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        try {
            if (!adapter.getProfileProxy(context, listener, HID_HOST)) Log.w(TAG, "HID host proxy refused")
        } catch (e: SecurityException) {
            Log.w(TAG, "No Bluetooth permission for the HID host proxy", e)
        }
    }

    // Output report 0x01: packet 0, neutral rumble, subcommand 0x06 (set HCI state) 0x00 (disconnect).
    private fun sendSleep(host: BluetoothProfile, device: BluetoothDevice): Boolean = try {
        val sendData = host.javaClass.getMethod("sendData", BluetoothDevice::class.java, String::class.java)
        val sent = sendData.invoke(host, device, SLEEP_REPORT) == true
        Log.i(TAG, "Sleep command to ${device.address}: $sent")
        sent
    } catch (e: InvocationTargetException) {
        Log.w(TAG, "Sleep command refused", e.targetException)
        false
    }

    private fun disconnectThroughShizuku(host: BluetoothProfile, device: BluetoothDevice): Boolean {
        if (!shellReady()) return false.also { Log.w(TAG, "Shizuku not ready") }
        return disconnectAsShell(shellService(host), device)
    }

    // IBluetoothHidHost.Stub.asInterface over a binder whose calls Shizuku makes as shell.
    private fun shellService(host: BluetoothProfile): Any {
        val service = host.javaClass.getDeclaredMethod("getService").apply { isAccessible = true }
            .invoke(host) as IInterface
        val stub = Class.forName("android.bluetooth.IBluetoothHidHost\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(service.asBinder()))!!
    }

    private fun disconnectAsShell(service: Any, device: BluetoothDevice): Boolean {
        val disconnect = service.javaClass.methods.first { it.name == "disconnect" }
        val result = disconnect.invoke(service, *arguments(disconnect, device))
        Log.i(TAG, "Disconnect ${device.address}: $result")
        return result != false
    }

    // Android 12 takes (device, source); 13+ adds a SynchronousResultReceiver.
    private fun arguments(method: Method, device: BluetoothDevice): Array<Any?> =
        method.parameterTypes.map { type ->
            when {
                type == BluetoothDevice::class.java -> device
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AttributionSource::class.java ->
                    AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()
                type.name.endsWith("SynchronousResultReceiver") -> type.getMethod("get").invoke(null)
                else -> throw NoSuchMethodException("Unexpected disconnect parameter ${type.name}")
            }
        }.toTypedArray()

    private companion object {
        const val TAG = "HidHostDisconnector"
        const val HID_HOST = 4
        const val BIND_TIMEOUT_MS = 3_000L
        const val SHELL_UID = 2000
        const val SHELL_PACKAGE = "com.android.shell"
        const val SLEEP_REPORT = "010000014040000140400600"
    }
}
