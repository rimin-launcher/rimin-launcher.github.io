package kr.personal.sealion.one.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kr.personal.sealion.one.launcher.R
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.key.KeyGattServer
import kr.personal.sealion.one.launcher.system.SystemTools
import kr.personal.sealion.one.launcher.ui.MainActivity

/** Keeps vehicle polling, automations and the BLE phone key alive while the head unit is on. */
class VehicleService : LifecycleService() {
    private var keyServer: KeyGattServer? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val app = app
        app.monitor.start(lifecycleScope)
        if (app.settings.value.wirelessAdbOnStart) SystemTools.enableWirelessAdb(this)
        lifecycleScope.launch {
            app.settings.flow.distinctUntilChangedBy { it.phoneKeyEnabled to it.vehicleName }.collect { s ->
                restartKeyServer(s.phoneKeyEnabled, s.vehicleName)
            }
        }
    }

    fun restartKeyServer(enabled: Boolean, vehicleName: String) {
        keyServer?.stop()
        keyServer = null
        keyRunning.value = false
        if (!enabled) return
        val app = app
        val pairing = app.keyStore.pairing(vehicleName)
        keyServer = KeyGattServer(
            context = this,
            pairing = pairing,
            keyStore = app.keyStore,
            onCommand = { command, who -> app.monitor.execute(command, "휴대폰 키 $who") },
            onRejected = { reason, who -> app.events.add("휴대폰 키 $who", "거부: ${reason.label}") },
        ).also { keyRunning.value = runCatching { it.start() }.getOrDefault(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_REKEY) {
            val s = app.settings.value
            restartKeyServer(s.phoneKeyEnabled, s.vehicleName)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        keyServer?.stop()
        keyRunning.value = false
        app.monitor.stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, 1, notification, type)
    }

    companion object {
        private const val CHANNEL = "vehicle"
        const val ACTION_REKEY = "kr.personal.sealion.one.REKEY"
        private val keyRunning = MutableStateFlow(false)
        val phoneKeyRunning: StateFlow<Boolean> = keyRunning

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, VehicleService::class.java).setAction(action)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
