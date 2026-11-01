package kr.personal.sealion.one.key

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult
import kr.personal.sealion.one.core.KeyProtocol
import kr.personal.sealion.one.core.PairingPayload
import kr.personal.sealion.one.core.ProximityEngine

data class KeyStatus(
    val running: Boolean = false,
    val zone: ProximityEngine.Zone = ProximityEngine.Zone.UNKNOWN,
    val rssi: Int? = null,
    val recentSteps: Int = 0,
    val lastSeen: Long = 0,
    val busy: Boolean = false,
    val lastResult: String? = null,
    val stepSensor: Boolean = true,
)

/**
 * Always-on service: scans for the paired car's advertisement, tracks walking via
 * the step detector, and sends LOCK / UNLOCK when [ProximityEngine] says so.
 */
@SuppressLint("MissingPermission")
class KeyService : LifecycleService(), SensorEventListener {
    private val store by lazy { PhoneKeyStore.get(this) }
    private val client by lazy { BleCommandClient(this, store) }
    private val gattLock = Mutex()
    private var engine = ProximityEngine()
    private var device: BluetoothDevice? = null
    private var tag: ByteArray? = null
    private var scanning = false

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val sensors = getSystemService(SensorManager::class.java)
        val step = sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (step != null) sensors.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL)
        status.value = status.value.copy(running = true, stepSensor = step != null)

        lifecycleScope.launch {
            store.settings.collect { s ->
                engine = ProximityEngine(
                    ProximityEngine.Config(
                        nearRssi = s.nearRssi, farRssi = s.farRssi,
                        // Without a step sensor, walking can't be required.
                        requireWalking = s.requireWalking && step != null,
                    ),
                )
            }
        }
        lifecycleScope.launch {
            store.vehicle.collect { v ->
                stopScan()
                tag = v?.let { PairingPayload.advertisedTag(it.id) }
                if (v != null) startScan()
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(2_000)
                engine.tick()?.let(::onProximityAction)
                status.value = status.value.copy(recentSteps = engine.recentSteps(), zone = engine.zone)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val command = intent?.getStringExtra(EXTRA_COMMAND)?.let { name -> Command.entries.firstOrNull { it.name == name } }
        if (command != null) runCommand(command, "수동")
        return START_STICKY
    }

    override fun onDestroy() {
        stopScan()
        getSystemService(SensorManager::class.java)?.unregisterListener(this)
        status.value = KeyStatus()
        super.onDestroy()
    }

    // --- Steps -----------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        engine.onStep()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // --- BLE scan ---------------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        override fun onScanFailed(errorCode: Int) {
            store.log("블루투스 스캔 실패 ($errorCode)")
            scanning = false
        }
    }

    private fun handle(result: ScanResult) {
        val expected = tag ?: return
        val advertised = result.scanRecord?.getServiceData(ParcelUuid(KeyProtocol.SERVICE))
        // Scan response (with the tag) can be missing on some phones; accept the device we've already matched.
        val matches = advertised?.contentEquals(expected) ?: (result.device.address == device?.address)
        if (!matches) return
        device = result.device
        store.rememberAddress(result.device.address)
        status.value = status.value.copy(rssi = result.rssi, lastSeen = System.currentTimeMillis())
        engine.onRssi(result.rssi)?.let(::onProximityAction)
        status.value = status.value.copy(zone = engine.zone)
    }

    private fun startScan() {
        val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(KeyProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanning = runCatching { scanner.startScan(filters, settings, scanCallback) }.isSuccess
        if (!scanning) store.log("블루투스 스캔을 시작할 수 없습니다 (권한/블루투스 확인)")
    }

    private fun stopScan() {
        if (!scanning) return
        runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
    }

    // --- Commands ---------------------------------------------------------------------------

    private fun onProximityAction(action: ProximityEngine.Action) {
        val s = store.settings.value
        when (action) {
            ProximityEngine.Action.UNLOCK -> if (s.autoUnlock) runCommand(Command.UNLOCK, "접근", retries = 1)
            ProximityEngine.Action.LOCK -> if (s.autoLock) runCommand(Command.LOCK, "이탈", retries = 2)
        }
    }

    private fun runCommand(command: Command, reason: String, retries: Int = 0) {
        lifecycleScope.launch(Dispatchers.IO) {
            val target = device ?: store.lastAddress()?.let { addr ->
                getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(addr)
            }
            if (target == null) {
                finish(command, reason, "차량 신호 없음")
                return@launch
            }
            gattLock.withLock {
                status.value = status.value.copy(busy = true)
                var result = CommandResult.VEHICLE_ERROR
                for (attempt in 0..retries) {
                    if (attempt > 0) delay(1_500)
                    result = client.send(target, command)
                    // Only transport failures are worth retrying; auth/policy rejections are final.
                    if (result != CommandResult.VEHICLE_ERROR) break
                }
                status.value = status.value.copy(busy = false)
                finish(command, reason, result.label, result == CommandResult.OK)
            }
        }
    }

    private fun finish(command: Command, reason: String, outcome: String, ok: Boolean = false) {
        val text = "[$reason] ${command.label}: $outcome"
        store.log(text)
        status.value = status.value.copy(lastResult = text)
        if (ok && store.settings.value.notifyActions) {
            vibrate()
            notifyEvent(text)
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
    }

    // --- Notifications ----------------------------------------------------------------------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT))
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, ID_SERVICE, buildServiceNotification(), type)
    }

    private fun action(command: Command, label: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, command.ordinal, commandIntent(this, command),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    private fun buildServiceNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("차량 접근·이탈을 감지하고 있습니다")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action(Command.LOCK, "잠금"))
            .addAction(action(Command.UNLOCK, "열림"))
            .addAction(action(Command.TRUNK_OPEN, "트렁크"))
            .build()
    }

    private fun notifyEvent(text: String) {
        val n = Notification.Builder(this, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(store.vehicle.value?.name ?: getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(ID_EVENT, n) }
    }

    companion object {
        private const val CH_SERVICE = "service"
        private const val CH_EVENTS = "events"
        private const val ID_SERVICE = 1
        private const val ID_EVENT = 2
        const val EXTRA_COMMAND = "command"

        private val status = MutableStateFlow(KeyStatus())
        val state: StateFlow<KeyStatus> = status

        fun commandIntent(context: Context, command: Command) =
            Intent(context, KeyService::class.java).putExtra(EXTRA_COMMAND, command.name)

        fun start(context: Context, command: Command? = null) {
            val intent = if (command == null) Intent(context, KeyService::class.java) else commandIntent(context, command)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) = context.stopService(Intent(context, KeyService::class.java))
    }
}
