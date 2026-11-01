package kr.personal.sealion.one.launcher.key

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult
import kr.personal.sealion.one.core.CommandVerifier
import kr.personal.sealion.one.core.HmacSha256
import kr.personal.sealion.one.core.KeyProtocol
import kr.personal.sealion.one.core.PairingPayload

/**
 * Head-unit side of the phone key. Advertises the key service (with a per-car tag
 * in the scan response) and runs a GATT server that executes verified commands.
 *
 * Only works while the head unit is powered — see README "휴대폰 키 동작 범위".
 */
@SuppressLint("MissingPermission")
class KeyGattServer(
    private val context: Context,
    private val pairing: PairingPayload,
    private val keyStore: VehicleKeyStore,
    private val onCommand: (Command, String) -> CommandResult,
    private val onRejected: (CommandResult, String) -> Unit,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private val verifier = CommandVerifier(HmacSha256(pairing.key), initialCounter = keyStore.lastCounter)
    private val statusCharacteristic = BluetoothGattCharacteristic(
        KeyProtocol.STATUS,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ,
    ).apply {
        addDescriptor(BluetoothGattDescriptor(
            KeyProtocol.CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        ))
    }

    var running = false
        private set

    fun start(): Boolean {
        val adapter = manager?.adapter ?: return false.also { Log.w(TAG, "no bluetooth adapter") }
        if (!adapter.isEnabled) return false
        val advertiser = adapter.bluetoothLeAdvertiser ?: return false.also { Log.w(TAG, "advertising unsupported") }

        server = manager.openGattServer(context, callback)?.also { gatt ->
            val service = BluetoothGattService(KeyProtocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(
                KeyProtocol.NONCE, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ,
            ))
            service.addCharacteristic(BluetoothGattCharacteristic(
                KeyProtocol.COMMAND, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE,
            ))
            service.addCharacteristic(statusCharacteristic)
            gatt.addService(service)
        } ?: return false

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(KeyProtocol.SERVICE)).build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(KeyProtocol.SERVICE), pairing.advertisedTag())
            .build()
        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        running = true
        return true
    }

    fun stop() {
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { server?.close() }
        server = null
        running = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "advertising failed: $errorCode")
            running = false
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic,
        ) {
            val value = when (characteristic.uuid) {
                KeyProtocol.NONCE -> verifier.issueNonce()
                KeyProtocol.STATUS -> byteArrayOf(CommandResult.OK.code, Command.PING.code)
                else -> null
            }
            if (value == null) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            } else {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value.copyOfRange(offset.coerceAtMost(value.size), value.size))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (characteristic.uuid != KeyProtocol.COMMAND || value == null) {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            val who = device.address.takeLast(5)
            val (result, command) = when (val outcome = verifier.verify(value)) {
                is CommandVerifier.Outcome.Accepted -> {
                    keyStore.lastCounter = outcome.counter
                    onCommand(outcome.command, who) to outcome.command
                }
                is CommandVerifier.Outcome.Rejected -> {
                    onRejected(outcome.reason, who)
                    outcome.reason to null
                }
            }
            notifyStatus(device, byteArrayOf(result.code, command?.code ?: 0))
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor,
        ) {
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }
    }

    @Suppress("DEPRECATION")
    private fun notifyStatus(device: BluetoothDevice, value: ByteArray) {
        statusCharacteristic.value = value
        runCatching { server?.notifyCharacteristicChanged(device, statusCharacteristic, false) }
    }

    companion object { private const val TAG = "SealionKey" }
}
