package kr.personal.sealion.one.launcher.vehicle

import android.content.Context
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult

/**
 * Vehicle access through the BYD DiLink framework SDK (android.hardware.bydauto.*).
 *
 * Method names below are *candidates* collected from public community research
 * and must be confirmed on the actual SEALION 7 firmware — open
 * 설정 › 진단 › 차량 SDK to see what the head unit really exposes. Anything
 * missing simply reports null / UNSUPPORTED instead of crashing.
 */
class BydAutoVehicle(context: Context) : VehicleApi {
    private val base = "android.hardware.bydauto"
    val speed = ReflectiveDevice(context, "$base.speed.BYDAutoSpeedDevice")
    val statistic = ReflectiveDevice(context, "$base.statistic.BYDAutoStatisticDevice")
    val gearbox = ReflectiveDevice(context, "$base.gearbox.BYDAutoGearboxDevice")
    val doorLock = ReflectiveDevice(context, "$base.doorlock.BYDAutoDoorLockDevice")
    val bodywork = ReflectiveDevice(context, "$base.bodywork.BYDAutoBodyworkDevice")
    val ac = ReflectiveDevice(context, "$base.ac.BYDAutoAcDevice")
    val light = ReflectiveDevice(context, "$base.light.BYDAutoLightDevice")
    val seat = ReflectiveDevice(context, "$base.seat.BYDAutoSeatDevice")
    val setting = ReflectiveDevice(context, "$base.setting.BYDAutoSettingDevice")

    val devices get() = listOf(speed, statistic, gearbox, doorLock, bodywork, ac, light, seat, setting)

    override val name = "BYD 차량 SDK"
    override val available: Boolean get() = devices.any { it.present }

    override fun read(): VehicleState = VehicleState(
        speedKmh = speed.number("getCurrentSpeed"),
        gear = readGear(),
        socPercent = statistic.number("getElecPercentageValue", "getSOCValue"),
        rangeKm = statistic.number("getEVMileageValue", "getElecDrivingRangeValue"),
        odometerKm = statistic.number("getTotalMileageValue"),
        lock = readLock(),
        acOn = ac.number("getAcStartState")?.let { it.toInt() == (ac.constant("AC_POWER_ON") ?: 1) },
        insideTempC = ac.number("getTemprature", "getTemperature", args = arrayOf(ac.constant("AC_TEMPERATURE_INSIDE") ?: 1)),
        outsideTempC = ac.number("getTemprature", "getTemperature", args = arrayOf(ac.constant("AC_TEMPERATURE_OUTSIDE") ?: 2)),
        climate = readClimate(),
        source = name,
    )

    // --- Climate & seats (candidate names; verify in 진단 · 차량 SDK) ---------------------------

    private fun area(device: ReflectiveDevice, seat: Seat, kind: String): Int =
        device.constant("${kind}_AREA_${if (seat == Seat.DRIVER) "MAIN" else "DEPUTY"}")
            ?: device.constant("${kind}_AREA_${if (seat == Seat.DRIVER) "LEFT" else "RIGHT"}")
            ?: if (seat == Seat.DRIVER) 1 else 2

    private fun onOff(raw: Double?, device: ReflectiveDevice, onName: String): Boolean? =
        raw?.let { it.toInt() == (device.constant(onName) ?: 1) }

    private val toggleNames = mapOf(
        ClimateToggle.POWER to ("getAcStartState" to "setAcStartState"),
        ClimateToggle.AC to ("getAcCompressorMode" to "setAcCompressorMode"),
        ClimateToggle.AUTO to ("getAcControlMode" to "setAcControlMode"),
        ClimateToggle.RECIRCULATION to ("getAcCycleMode" to "setAcCycleMode"),
    )

    private fun readClimate(): ClimateState {
        val seatsDevice = if (seat.present) seat else setting
        fun seatLevel(getter: String, s: Seat) =
            seatsDevice.number(getter, args = arrayOf(area(seatsDevice, s, "SEAT")))?.toInt()?.coerceIn(0, ClimateState.MAX_SEAT_LEVEL)
        val toggles = buildMap {
            toggleNames.forEach { (toggle, names) -> onOff(ac.number(names.first), ac, "AC_POWER_ON")?.let { put(toggle, it) } }
            ac.number("getAcDefrostState", args = arrayOf(ac.constant("AC_DEFROST_AREA_FRONT") ?: 1))
                ?.let { put(ClimateToggle.DEFROST_FRONT, it.toInt() != 0) }
            ac.number("getAcDefrostState", args = arrayOf(ac.constant("AC_DEFROST_AREA_REAR") ?: 2))
                ?.let { put(ClimateToggle.DEFROST_REAR, it.toInt() != 0) }
        }
        return ClimateState(
            driverTempC = ac.number("getTemprature", "getTemperature", args = arrayOf(area(ac, Seat.DRIVER, "AC_TEMPERATURE"))),
            passengerTempC = ac.number("getTemprature", "getTemperature", args = arrayOf(area(ac, Seat.PASSENGER, "AC_TEMPERATURE"))),
            fanLevel = ac.number("getAcWindLevel")?.toInt(),
            toggles = toggles,
            seatHeat = Seat.entries.mapNotNull { s -> seatLevel("getSeatHeatingState", s)?.let { s to it } }.toMap(),
            seatVent = Seat.entries.mapNotNull { s -> seatLevel("getSeatVentilatingState", s)?.let { s to it } }.toMap(),
            steeringHeat = seatsDevice.number("getSteeringWheelHeatState")?.let { it.toInt() != 0 },
        )
    }

    override fun climate(action: ClimateAction): CommandResult {
        val seatsDevice = if (seat.present) seat else setting
        val ok: Boolean? = when (action) {
            is ClimateAction.Temperature -> ac.invoke("setAcTemperature", "setTemprature",
                args = arrayOf(area(ac, action.seat, "AC_TEMPERATURE"), action.celsius))
            is ClimateAction.Fan -> ac.invoke("setAcWindLevel", args = arrayOf(action.level))
            is ClimateAction.Toggle -> when (action.toggle) {
                ClimateToggle.DEFROST_FRONT -> ac.invoke("setAcDefrostState",
                    args = arrayOf(ac.constant("AC_DEFROST_AREA_FRONT") ?: 1, if (action.on) 1 else 0))
                ClimateToggle.DEFROST_REAR -> ac.invoke("setAcDefrostState",
                    args = arrayOf(ac.constant("AC_DEFROST_AREA_REAR") ?: 2, if (action.on) 1 else 0))
                else -> toggleNames[action.toggle]?.let { (_, setter) -> ac.invoke(setter, args = arrayOf(if (action.on) 1 else 0)) }
            }
            is ClimateAction.SeatHeat -> seatsDevice.invoke("setSeatHeatingState",
                args = arrayOf(area(seatsDevice, action.seat, "SEAT"), action.level))
            is ClimateAction.SeatVent -> seatsDevice.invoke("setSeatVentilatingState",
                args = arrayOf(area(seatsDevice, action.seat, "SEAT"), action.level))
            is ClimateAction.SteeringHeat -> seatsDevice.invoke("setSteeringWheelHeatState", args = arrayOf(if (action.on) 1 else 0))
        }
        return when (ok) {
            true -> CommandResult.OK
            false -> CommandResult.VEHICLE_ERROR
            null -> CommandResult.UNSUPPORTED
        }
    }

    private fun readGear(): Gear {
        val raw = gearbox.number("getGearboxAutoModeType", "getGearboxType")?.toInt() ?: return Gear.UNKNOWN
        val constant = gearbox.constantName(raw, "MODE") ?: return Gear.UNKNOWN
        return when (constant.substringAfterLast('_')) {
            "P" -> Gear.P
            "R" -> Gear.R
            "N" -> Gear.N
            "D", "S" -> Gear.D
            else -> Gear.UNKNOWN
        }
    }

    private fun readLock(): LockState {
        val area = doorLock.constant("DOOR_LOCK_AREA_LEFT_FRONT") ?: 1
        val raw = doorLock.number("getDoorLockStatus", args = arrayOf(area))?.toInt() ?: return LockState.UNKNOWN
        val locked = doorLock.constant("DOOR_LOCK_STATE_LOCK") ?: doorLock.constant("DOOR_LOCK_STATUS_LOCK")
        val unlocked = doorLock.constant("DOOR_LOCK_STATE_UNLOCK") ?: doorLock.constant("DOOR_LOCK_STATUS_UNLOCK")
        return when (raw) {
            locked -> LockState.LOCKED
            unlocked -> LockState.UNLOCKED
            else -> LockState.UNKNOWN
        }
    }

    override fun execute(command: Command): CommandResult {
        val ok: Boolean? = when (command) {
            Command.LOCK -> doorLock.invoke("lockDoor", "setDoorLock")
                ?: doorLock.invoke("setDoorLockStatus", args = arrayOf(doorLock.constant("DOOR_LOCK_STATE_LOCK") ?: 1))
            Command.UNLOCK -> doorLock.invoke("unlockDoor", "setDoorUnlock")
                ?: doorLock.invoke("setDoorLockStatus", args = arrayOf(doorLock.constant("DOOR_LOCK_STATE_UNLOCK") ?: 2))
            Command.TRUNK_OPEN -> bodywork.invoke("openBackDoor", "setBackDoorOpen", "openTrunk")
            Command.WINDOWS_CLOSE -> bodywork.invoke("closeAllWindows", "setAllWindowClose")
            Command.CLIMATE_ON -> ac.invoke("start", "turnOnAc")
                ?: ac.invoke("setAcStartState", args = arrayOf(ac.constant("AC_POWER_ON") ?: 1))
            Command.CLIMATE_OFF -> ac.invoke("stop", "turnOffAc")
                ?: ac.invoke("setAcStartState", args = arrayOf(ac.constant("AC_POWER_OFF") ?: 0))
            Command.FIND_CAR -> light.invoke("flashLights", "setFindCar")
            Command.PING -> true
        }
        return when (ok) {
            true -> CommandResult.OK
            false -> CommandResult.VEHICLE_ERROR
            null -> CommandResult.UNSUPPORTED
        }
    }
}
