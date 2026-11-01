package kr.personal.sealion.one.core

/**
 * Phone-side decision engine for approach-unlock / walk-away-lock.
 *
 * Inputs: RSSI samples from the paired vehicle's advertisement and step events
 * from the phone's step detector. A transition only fires when the signal change
 * coincides with recent walking, which filters out the phone sitting still near
 * the edge of radio range (e.g. inside the house next to the parking spot).
 */
class ProximityEngine(
    private val config: Config = Config(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Config(
        val nearRssi: Int = -68,
        val farRssi: Int = -86,
        val lostAfterMs: Long = 12_000,
        val walkWindowMs: Long = 30_000,
        val minSteps: Int = 6,
        val requireWalking: Boolean = true,
        val cooldownMs: Long = 45_000,
        val smoothing: Double = 0.3,
    )

    enum class Zone { UNKNOWN, NEAR, FAR }

    enum class Action { UNLOCK, LOCK }

    var zone: Zone = Zone.UNKNOWN
        private set
    var smoothedRssi: Double? = null
        private set

    private var lastSeen = 0L
    private val startedAt = clock()
    private var lastAction = Long.MIN_VALUE / 2
    private val steps = ArrayDeque<Long>()

    fun onStep(at: Long = clock()) {
        steps.addLast(at)
        trimSteps(at)
    }

    fun recentSteps(now: Long = clock()): Int {
        trimSteps(now)
        return steps.size
    }

    private fun trimSteps(now: Long) {
        while (steps.isNotEmpty() && now - steps.first() > config.walkWindowMs) steps.removeFirst()
    }

    private fun walking(now: Long) = !config.requireWalking || recentSteps(now) >= config.minSteps

    /** Feed one advertisement RSSI sample; returns an action if a zone transition should fire. */
    fun onRssi(rssi: Int, now: Long = clock()): Action? {
        lastSeen = now
        val previous = smoothedRssi
        val value = if (previous == null) rssi.toDouble() else previous + config.smoothing * (rssi - previous)
        smoothedRssi = value
        return when {
            value >= config.nearRssi && zone != Zone.NEAR -> transition(Zone.NEAR, now)
            value <= config.farRssi && zone == Zone.NEAR -> transition(Zone.FAR, now)
            // Started out of range: classify as FAR so the first approach can unlock.
            zone == Zone.UNKNOWN && value < config.nearRssi -> { zone = Zone.FAR; null }
            else -> null
        }
    }

    /** Call periodically; detects the vehicle signal disappearing entirely. */
    fun tick(now: Long = clock()): Action? {
        if (zone == Zone.UNKNOWN && now - startedAt > config.lostAfterMs && now - lastSeen > config.lostAfterMs) {
            zone = Zone.FAR
            return null
        }
        if (zone == Zone.NEAR && now - lastSeen > config.lostAfterMs) {
            smoothedRssi = null
            return transition(Zone.FAR, now)
        }
        return null
    }

    private fun transition(target: Zone, now: Long): Action? {
        val from = zone
        // Need walking evidence to change zones (except initial classification).
        if (from != Zone.UNKNOWN && !walking(now)) return null
        zone = target
        if (from == Zone.UNKNOWN) {
            // First fix after service start: classify silently, never act.
            return null
        }
        if (now - lastAction < config.cooldownMs) return null
        lastAction = now
        return if (target == Zone.NEAR) Action.UNLOCK else Action.LOCK
    }
}
