package kr.personal.sealion.one.launcher.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult
import kr.personal.sealion.one.launcher.data.DriveLog
import kr.personal.sealion.one.launcher.data.EventLog
import kr.personal.sealion.one.launcher.data.SettingsStore
import kr.personal.sealion.one.launcher.voice.Announcer

/**
 * Polls the vehicle once per second and runs the on-car automations:
 *  - 주행 중 자동 잠금 (speed ≥ threshold, once per drive)
 *  - P 단 자동 열림
 *  - 기어 / 과속 음성 안내
 *  - 주행 기록
 */
class VehicleMonitor(
    private val vehicleProvider: () -> VehicleApi,
    private val settings: SettingsStore,
    private val events: EventLog,
    private val driveLog: DriveLog,
    private val announcer: Announcer,
) {
    private val state = MutableStateFlow(VehicleState())
    val flow: StateFlow<VehicleState> = state

    private var job: Job? = null
    private var lockedThisDrive = false
    private var overspeedArmed = true
    private var previous = VehicleState()

    val vehicle: VehicleApi get() = vehicleProvider()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val current = runCatching { vehicle.read() }.getOrDefault(VehicleState(source = "읽기 실패"))
                state.value = current
                react(previous, current, now)
                previous = current
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        driveLog.finish()
    }

    /** Single entry point for every command (UI buttons, phone key, automations). */
    fun execute(command: Command, source: String): CommandResult {
        val speed = state.value.speedKmh ?: 0.0
        if (speed > 3 && command in setOf(Command.UNLOCK, Command.TRUNK_OPEN)) {
            events.add(source, "${command.label} 거부: 주행 중")
            return CommandResult.REFUSED_MOVING
        }
        val result = runCatching { vehicle.execute(command) }.getOrDefault(CommandResult.VEHICLE_ERROR)
        events.add(source, "${command.label}: ${result.label}")
        return result
    }

    /** Climate change from the dock strip or climate page; reflects the new value immediately. */
    fun climate(action: ClimateAction): CommandResult {
        val result = runCatching { vehicle.climate(action) }.getOrDefault(CommandResult.VEHICLE_ERROR)
        if (result == CommandResult.OK) {
            runCatching { state.value = vehicle.read() }
        } else {
            events.add("공조", "${action.label}: ${result.label}")
        }
        return result
    }

    private fun react(before: VehicleState, now: VehicleState, at: Long) {
        val s = settings.value
        val speed = now.speedKmh

        if (s.driveLogEnabled) driveLog.sample(at, speed, now.gear == Gear.P || now.gear == Gear.UNKNOWN, now.socPercent)

        // Auto-lock once per drive when passing the threshold speed.
        if (s.autoLockSpeedKmh > 0 && speed != null) {
            if (speed >= s.autoLockSpeedKmh && !lockedThisDrive && now.lock != LockState.LOCKED) {
                lockedThisDrive = true
                execute(Command.LOCK, "주행 자동 잠금")
            }
        }
        if (now.gear == Gear.P) lockedThisDrive = false

        // Unlock when shifting into P from a driving gear.
        if (s.autoUnlockOnPark && before.gear in setOf(Gear.D, Gear.R) && now.gear == Gear.P) {
            execute(Command.UNLOCK, "P단 자동 열림")
        }

        if (s.announceGear && before.gear != Gear.UNKNOWN && now.gear != before.gear && now.gear != Gear.UNKNOWN) {
            announcer.say(now.gear.spoken, s.announcementVolume)
        }

        if (s.overspeedKmh > 0 && speed != null) {
            if (overspeedArmed && speed > s.overspeedKmh) {
                overspeedArmed = false
                announcer.say("${s.overspeedKmh}킬로미터를 넘었습니다", s.announcementVolume)
            } else if (speed < s.overspeedKmh - 5) {
                overspeedArmed = true
            }
        }
    }
}
