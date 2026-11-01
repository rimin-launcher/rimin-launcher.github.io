package kr.personal.sealion.one.launcher.vehicle

import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult

enum class Gear(val label: String, val spoken: String) {
    P("P", "주차"), R("R", "후진"), N("N", "중립"), D("D", "주행"), UNKNOWN("-", "")
}

enum class LockState(val label: String) { LOCKED("잠김"), UNLOCKED("열림"), UNKNOWN("확인 불가") }

/** One snapshot of what the head unit can tell us. Null = this car/firmware doesn't expose it. */
data class VehicleState(
    val speedKmh: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val socPercent: Double? = null,
    val rangeKm: Double? = null,
    val odometerKm: Double? = null,
    val lock: LockState = LockState.UNKNOWN,
    val acOn: Boolean? = null,
    val insideTempC: Double? = null,
    val outsideTempC: Double? = null,
    val climate: ClimateState = ClimateState(),
    val source: String = "",
)

interface VehicleApi {
    /** Short human label: "BYD SDK", "데모" ... */
    val name: String
    val available: Boolean
    fun read(): VehicleState

    /** Executes a body/climate command. Must never throw. */
    fun execute(command: Command): CommandResult

    /** Applies one climate / seat change. Must never throw. */
    fun climate(action: ClimateAction): CommandResult
}
