package kr.personal.sealion.one.launcher

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kr.personal.sealion.one.launcher.data.DriveLog
import kr.personal.sealion.one.launcher.data.EventLog
import kr.personal.sealion.one.launcher.data.SettingsStore
import kr.personal.sealion.one.launcher.key.VehicleKeyStore
import kr.personal.sealion.one.launcher.vehicle.BydAutoVehicle
import kr.personal.sealion.one.launcher.vehicle.DemoVehicle
import kr.personal.sealion.one.launcher.vehicle.VehicleApi
import kr.personal.sealion.one.launcher.vehicle.VehicleMonitor
import kr.personal.sealion.one.launcher.voice.Announcer

class LauncherApp : Application() {
    val scope = CoroutineScope(SupervisorJob())
    lateinit var settings: SettingsStore private set
    lateinit var events: EventLog private set
    lateinit var driveLog: DriveLog private set
    lateinit var keyStore: VehicleKeyStore private set
    lateinit var byd: BydAutoVehicle private set
    lateinit var monitor: VehicleMonitor private set
    private val demo = DemoVehicle()

    /** Real SDK when present; demo when asked for or when not running on a BYD head unit. */
    val vehicle: VehicleApi
        get() = if (settings.value.demoMode || !byd.available) demo else byd

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        events = EventLog(this)
        driveLog = DriveLog(this)
        keyStore = VehicleKeyStore(this)
        byd = BydAutoVehicle(this)
        monitor = VehicleMonitor({ vehicle }, settings, events, driveLog, Announcer(this))
    }
}

val android.content.Context.app: LauncherApp get() = applicationContext as LauncherApp
