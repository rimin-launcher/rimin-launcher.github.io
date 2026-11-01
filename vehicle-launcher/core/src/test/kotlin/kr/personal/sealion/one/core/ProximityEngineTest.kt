package kr.personal.sealion.one.core

import kr.personal.sealion.one.core.ProximityEngine.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProximityEngineTest {
    private var now = 0L
    private val engine = ProximityEngine(ProximityEngine.Config(smoothing = 1.0, cooldownMs = 1_000), clock = { now })

    private fun walk(steps: Int = 10) = repeat(steps) { engine.onStep(now) }

    @Test fun firstFixNeverActs() {
        assertNull(engine.onRssi(-50))
        assertEquals(ProximityEngine.Zone.NEAR, engine.zone)
    }

    @Test fun walkAwayLocksThenApproachUnlocks() {
        engine.onRssi(-50)
        now += 5_000; walk()
        assertEquals(Action.LOCK, engine.onRssi(-95))
        now += 5_000; walk()
        assertEquals(Action.UNLOCK, engine.onRssi(-55))
    }

    @Test fun noActionWithoutWalking() {
        engine.onRssi(-50)
        now += 5_000
        assertNull(engine.onRssi(-95))
        assertEquals(ProximityEngine.Zone.NEAR, engine.zone)
    }

    @Test fun signalLossLocks() {
        engine.onRssi(-60)
        now += 20_000; walk()
        assertEquals(Action.LOCK, engine.tick())
    }

    @Test fun hysteresisBandDoesNothing() {
        engine.onRssi(-60)
        now += 5_000; walk()
        assertNull(engine.onRssi(-75))
    }

    @Test fun startingOutOfRangeStillUnlocksOnApproach() {
        now += 20_000
        assertNull(engine.tick())
        assertEquals(ProximityEngine.Zone.FAR, engine.zone)
        walk()
        assertEquals(Action.UNLOCK, engine.onRssi(-55))
    }

    @Test fun weakFirstFixIsFar() {
        assertNull(engine.onRssi(-80))
        assertEquals(ProximityEngine.Zone.FAR, engine.zone)
        walk()
        assertEquals(Action.UNLOCK, engine.onRssi(-50))
    }
}
