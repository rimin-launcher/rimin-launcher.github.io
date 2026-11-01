package kr.personal.sealion.one.launcher.vehicle

import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.CommandResult
import kotlin.math.max
import kotlin.math.sin

/** Simulated car so the whole launcher can be tried on an emulator or tablet. */
class DemoVehicle : VehicleApi {
    override val name = "데모 차량"
    override val available = true

    private val start = System.currentTimeMillis()
    private var lock = LockState.LOCKED
    private var ac = false
    private var odometer = 12_345.0
    private var climate = ClimateState(
        driverTempC = 22.5, passengerTempC = 22.5, fanLevel = 3,
        toggles = ClimateToggle.entries.associateWith { it == ClimateToggle.AUTO },
        seatHeat = mapOf(Seat.DRIVER to 0, Seat.PASSENGER to 0),
        seatVent = mapOf(Seat.DRIVER to 0, Seat.PASSENGER to 0),
        steeringHeat = false,
    )

    override fun read(): VehicleState {
        val t = (System.currentTimeMillis() - start) / 1000.0
        val cycle = t % 120
        val speed = if (cycle < 15 || cycle > 100) 0.0 else max(0.0, 55 + 35 * sin(t / 9))
        odometer += speed / 3600.0
        val gear = when {
            cycle < 10 -> Gear.P
            cycle < 15 -> Gear.D
            cycle < 100 -> Gear.D
            cycle < 110 -> Gear.D
            else -> Gear.P
        }
        return VehicleState(
            speedKmh = speed,
            gear = gear,
            socPercent = 78.0 - (t / 600.0) % 30,
            rangeKm = 410.0 - (t / 120.0) % 120,
            odometerKm = odometer,
            lock = lock,
            acOn = ac,
            climate = climate.copy(toggles = climate.toggles + (ClimateToggle.POWER to ac)),
            insideTempC = 22.5,
            outsideTempC = 14.0,
            source = name,
        )
    }

    override fun execute(command: Command): CommandResult {
        when (command) {
            Command.LOCK -> lock = LockState.LOCKED
            Command.UNLOCK -> lock = LockState.UNLOCKED
            Command.CLIMATE_ON -> ac = true
            Command.CLIMATE_OFF -> ac = false
            else -> Unit
        }
        return CommandResult.OK
    }

    override fun climate(action: ClimateAction): CommandResult {
        climate = when (action) {
            is ClimateAction.Temperature -> if (action.seat == Seat.DRIVER) climate.copy(driverTempC = action.celsius)
                else climate.copy(passengerTempC = action.celsius)
            is ClimateAction.Fan -> climate.copy(fanLevel = action.level)
            is ClimateAction.Toggle -> {
                if (action.toggle == ClimateToggle.POWER) ac = action.on
                climate.copy(toggles = climate.toggles + (action.toggle to action.on))
            }
            is ClimateAction.SeatHeat -> climate.copy(
                seatHeat = climate.seatHeat + (action.seat to action.level),
                // Heating and ventilation are mutually exclusive on real seats.
                seatVent = if (action.level > 0) climate.seatVent + (action.seat to 0) else climate.seatVent,
            )
            is ClimateAction.SeatVent -> climate.copy(
                seatVent = climate.seatVent + (action.seat to action.level),
                seatHeat = if (action.level > 0) climate.seatHeat + (action.seat to 0) else climate.seatHeat,
            )
            is ClimateAction.SteeringHeat -> climate.copy(steeringHeat = action.on)
        }
        return CommandResult.OK
    }
}
