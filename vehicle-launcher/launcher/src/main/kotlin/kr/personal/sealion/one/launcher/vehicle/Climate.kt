package kr.personal.sealion.one.launcher.vehicle

enum class Seat(val label: String) { DRIVER("운전석"), PASSENGER("동승석") }

enum class ClimateToggle(val label: String) {
    POWER("공조"), AC("A/C"), AUTO("AUTO"), RECIRCULATION("내기 순환"), DEFROST_FRONT("앞유리 제상"), DEFROST_REAR("뒷유리 열선"),
}

/** Seat / wheel levels: 0 = off, 1..3 = low..high. Null fields = not exposed by this car. */
data class ClimateState(
    val driverTempC: Double? = null,
    val passengerTempC: Double? = null,
    val fanLevel: Int? = null,
    val toggles: Map<ClimateToggle, Boolean> = emptyMap(),
    val seatHeat: Map<Seat, Int> = emptyMap(),
    val seatVent: Map<Seat, Int> = emptyMap(),
    val steeringHeat: Boolean? = null,
) {
    companion object {
        const val MIN_TEMP = 17.0
        const val MAX_TEMP = 33.0
        const val MAX_FAN = 7
        const val MAX_SEAT_LEVEL = 3
    }
}

sealed interface ClimateAction {
    val label: String

    data class Temperature(val seat: Seat, val celsius: Double) : ClimateAction {
        override val label get() = "${seat.label} 온도 ${"%.1f".format(celsius)}°"
    }
    data class Fan(val level: Int) : ClimateAction {
        override val label get() = "풍량 $level"
    }
    data class Toggle(val toggle: ClimateToggle, val on: Boolean) : ClimateAction {
        override val label get() = "${toggle.label} ${if (on) "켜기" else "끄기"}"
    }
    data class SeatHeat(val seat: Seat, val level: Int) : ClimateAction {
        override val label get() = "${seat.label} 열선 ${levelText(level)}"
    }
    data class SeatVent(val seat: Seat, val level: Int) : ClimateAction {
        override val label get() = "${seat.label} 통풍 ${levelText(level)}"
    }
    data class SteeringHeat(val on: Boolean) : ClimateAction {
        override val label get() = "핸들 열선 ${if (on) "켜기" else "끄기"}"
    }
}

fun levelText(level: Int) = if (level <= 0) "끔" else "${level}단계"
