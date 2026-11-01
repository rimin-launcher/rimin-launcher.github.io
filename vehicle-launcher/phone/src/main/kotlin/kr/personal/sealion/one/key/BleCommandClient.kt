package kr.personal.sealion.one.key

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandFrame
import kr.personal.sealion.one.core.CommandResult
import kr.personal.sealion.one.core.KeyProtocol

/**
 * One-shot GATT exchange: connect → discover → subscribe status → read nonce →
 * write signed command → wait for status notification → disconnect.
 */
@SuppressLint("MissingPermission")
class BleCommandClient(private val context: Context, private val store: PhoneKeyStore) {

    private sealed interface Event {
        data class Connected(val ok: Boolean) : Event
        data class Discovered(val ok: Boolean) : Event
        data object DescriptorWritten : Event
        data class Read(val value: ByteArray?) : Event
        data object Written : Event
        data class Status(val value: ByteArray) : Event
    }

    suspend fun send(device: BluetoothDevice, command: Command): CommandResult {
        val mac = store.mac() ?: return CommandResult.BAD_MAC
        val events = Channel<Event>(Channel.UNLIMITED)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(Event.Connected(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED))
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Event.Discovered(status == BluetoothGatt.GATT_SUCCESS))
            }
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                events.trySend(Event.DescriptorWritten)
            }
            @Deprecated("API < 33")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                @Suppress("DEPRECATION")
                events.trySend(Event.Read(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null))
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                events.trySend(Event.Read(if (status == BluetoothGatt.GATT_SUCCESS) value else null))
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                events.trySend(Event.Written)
            }
            @Deprecated("API < 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { events.trySend(Event.Status(it)) }
            }
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                events.trySend(Event.Status(value))
            }
        }

        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: return CommandResult.VEHICLE_ERROR
        return try {
            withTimeout(15_000) {
                suspend fun <T : Event> await(type: Class<T>): T {
                    while (true) {
                        val e = events.receive()
                        if (e is Event.Connected && !e.ok) throw IllegalStateException("disconnected")
                        if (type.isInstance(e)) return type.cast(e)!!
                    }
                }
                await(Event.Connected::class.java)
                gatt.discoverServices()
                if (!await(Event.Discovered::class.java).ok) return@withTimeout CommandResult.VEHICLE_ERROR
                val service = gatt.getService(KeyProtocol.SERVICE) ?: return@withTimeout CommandResult.UNSUPPORTED
                val status = service.getCharacteristic(KeyProtocol.STATUS)
                gatt.setCharacteristicNotification(status, true)
                status.getDescriptor(KeyProtocol.CCCD)?.let { d ->
                    writeDescriptor(gatt, d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    await(Event.DescriptorWritten::class.java)
                }
                gatt.readCharacteristic(service.getCharacteristic(KeyProtocol.NONCE))
                val nonce = await(Event.Read::class.java).value ?: return@withTimeout CommandResult.STALE_NONCE
                val frame = CommandFrame.sign(mac, nonce, command, store.nextCounter())
                writeCharacteristic(gatt, service.getCharacteristic(KeyProtocol.COMMAND), frame)
                val reply = await(Event.Status::class.java).value
                CommandResult.of(reply.firstOrNull() ?: CommandResult.VEHICLE_ERROR.code)
            }
        } catch (e: Exception) {
            CommandResult.VEHICLE_ERROR
        } finally {
            runCatching { gatt.disconnect(); gatt.close() }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(d, value)
        else { d.value = value; gatt.writeDescriptor(d) }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = value
            gatt.writeCharacteristic(c)
        }
    }
}
