package kr.personal.sealion.one.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandVerifierTest {
    private val key = ByteArray(32) { it.toByte() }
    private var now = 1_000L
    private val verifier = CommandVerifier(HmacSha256(key), clock = { now })

    @Test fun acceptsValidCommand() {
        val nonce = verifier.issueNonce()
        val frame = CommandFrame.sign(HmacSha256(key), nonce, Command.UNLOCK, 1)
        assertEquals(CommandVerifier.Outcome.Accepted(Command.UNLOCK, 1), verifier.verify(frame))
    }

    @Test fun rejectsWrongKey() {
        val nonce = verifier.issueNonce()
        val frame = CommandFrame.sign(HmacSha256(ByteArray(32)), nonce, Command.UNLOCK, 1)
        assertEquals(CommandVerifier.Outcome.Rejected(CommandResult.BAD_MAC), verifier.verify(frame))
    }

    @Test fun nonceIsSingleUse() {
        val nonce = verifier.issueNonce()
        val frame = CommandFrame.sign(HmacSha256(key), nonce, Command.LOCK, 1)
        verifier.verify(frame)
        assertEquals(CommandVerifier.Outcome.Rejected(CommandResult.STALE_NONCE), verifier.verify(frame))
    }

    @Test fun rejectsExpiredNonce() {
        val nonce = verifier.issueNonce()
        now += KeyProtocol.NONCE_TTL_MS + 1
        val frame = CommandFrame.sign(HmacSha256(key), nonce, Command.LOCK, 1)
        assertEquals(CommandVerifier.Outcome.Rejected(CommandResult.STALE_NONCE), verifier.verify(frame))
    }

    @Test fun rejectsOldCounter() {
        verifier.verify(CommandFrame.sign(HmacSha256(key), verifier.issueNonce(), Command.LOCK, 5))
        val again = CommandFrame.sign(HmacSha256(key), verifier.issueNonce(), Command.UNLOCK, 5)
        assertEquals(CommandVerifier.Outcome.Rejected(CommandResult.REPLAYED), verifier.verify(again))
    }

    @Test fun rejectsTamperedCommand() {
        val frame = CommandFrame.sign(HmacSha256(key), verifier.issueNonce(), Command.LOCK, 1)
        frame[1] = Command.UNLOCK.code
        assertEquals(CommandVerifier.Outcome.Rejected(CommandResult.BAD_MAC), verifier.verify(frame))
    }

    @Test fun pairingRoundTrip() {
        val payload = PairingPayload.generate("내 씨라이언")
        assertEquals(payload, PairingPayload.decode(payload.encode()))
        assertNull(PairingPayload.decode("garbage"))
        assertNotNull(payload.advertisedTag())
    }
}
