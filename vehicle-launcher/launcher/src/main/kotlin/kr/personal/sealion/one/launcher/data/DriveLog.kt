package kr.personal.sealion.one.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Trip(
    val start: Long,
    val end: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val socStart: Double?,
    val socEnd: Double?,
) {
    val durationMin: Double get() = (end - start) / 60_000.0
    val avgSpeedKmh: Double get() = if (durationMin > 0) distanceKm / (durationMin / 60) else 0.0
    fun energyKwh(batteryKwh: Double): Double? =
        if (socStart != null && socEnd != null && socStart > socEnd) (socStart - socEnd) / 100 * batteryKwh else null
}

/**
 * Trip recorder fed once per second by VehicleMonitor. A trip starts when the car
 * moves and ends after it has stayed in P (or unknown gear at 0 km/h) for 2 minutes.
 */
class DriveLog(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    private val state = MutableStateFlow(load())
    val trips: StateFlow<List<Trip>> = state

    private var current: Active? = null
    private class Active(val start: Long, val socStart: Double?) {
        var distanceKm = 0.0
        var maxSpeed = 0.0
        var lastSample = start
        var stoppedSince: Long? = null
        var socEnd: Double? = socStart
    }

    val activeDistanceKm: Double? get() = current?.distanceKm

    @Synchronized
    fun sample(now: Long, speedKmh: Double?, parked: Boolean, soc: Double?) {
        val speed = speedKmh ?: return
        var trip = current
        if (trip == null) {
            if (speed < 1.0) return
            trip = Active(now, soc).also { current = it }
        }
        val dtHours = (now - trip.lastSample).coerceIn(0, 10_000) / 3_600_000.0
        trip.distanceKm += speed * dtHours
        trip.maxSpeed = maxOf(trip.maxSpeed, speed)
        trip.lastSample = now
        if (soc != null) trip.socEnd = soc
        if (speed < 1.0 && parked) {
            val since = trip.stoppedSince ?: now.also { trip.stoppedSince = it }
            if (now - since > END_AFTER_MS) finish(since)
        } else {
            trip.stoppedSince = null
        }
    }

    @Synchronized
    fun finish(end: Long = System.currentTimeMillis()) {
        val trip = current ?: return
        current = null
        if (trip.distanceKm < 0.1) return
        val next = listOf(Trip(trip.start, end, trip.distanceKm, trip.maxSpeed, trip.socStart, trip.socEnd)) + state.value
        state.value = next.take(MAX)
        persist()
    }

    @Synchronized
    fun clear() {
        state.value = emptyList()
        persist()
    }

    private fun persist() = runCatching {
        file.writeText(JSONArray(state.value.map {
            JSONObject().put("start", it.start).put("end", it.end).put("distanceKm", it.distanceKm)
                .put("maxSpeedKmh", it.maxSpeedKmh).put("socStart", it.socStart ?: JSONObject.NULL)
                .put("socEnd", it.socEnd ?: JSONObject.NULL)
        }).toString())
    }

    private fun load(): List<Trip> = runCatching {
        val array = JSONArray(file.readText())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Trip(
                o.getLong("start"), o.getLong("end"), o.getDouble("distanceKm"), o.getDouble("maxSpeedKmh"),
                o.optDouble("socStart").takeUnless { it.isNaN() }, o.optDouble("socEnd").takeUnless { it.isNaN() },
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val END_AFTER_MS = 120_000L
        private const val MAX = 500
    }
}
