package kr.personal.sealion.one.core

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/** Computes a MAC over [data]; backed by Android Keystore on device, [HmacSha256] in tests. */
fun interface MacFunction {
    fun mac(data: ByteArray): ByteArray
}

/**
 * Wire format (26 bytes): version(1) | command(1) | counter(8, big endian) | mac(16)
 * MAC input: "SL1" | nonce(16) | version | command | counter
 */
object CommandFrame {
    const val SIZE = 2 + 8 + KeyProtocol.MAC_BYTES
    private val DOMAIN = "SL1".toByteArray()

    fun macInput(nonce: ByteArray, command: Byte, counter: Long): ByteArray =
        ByteBuffer.allocate(DOMAIN.size + nonce.size + 2 + 8)
            .put(DOMAIN).put(nonce).put(KeyProtocol.VERSION).put(command).putLong(counter)
            .array()

    fun sign(mac: MacFunction, nonce: ByteArray, command: Command, counter: Long): ByteArray {
        val tag = mac.mac(macInput(nonce, command.code, counter)).copyOf(KeyProtocol.MAC_BYTES)
        return ByteBuffer.allocate(SIZE)
            .put(KeyProtocol.VERSION).put(command.code).putLong(counter).put(tag)
            .array()
    }
}

/**
 * Vehicle-side verifier. Each nonce can be used exactly once and expires after
 * [KeyProtocol.NONCE_TTL_MS]; counters must strictly increase across sessions.
 */
class CommandVerifier(
    private val mac: MacFunction,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    initialCounter: Long = 0,
) {
    private var nonce: ByteArray? = null
    private var issuedAt = 0L

    /** Highest counter accepted so far — persist it so replays fail after reboot too. */
    var lastCounter: Long = initialCounter
        private set

    @Synchronized
    fun issueNonce(): ByteArray {
        val fresh = ByteArray(KeyProtocol.NONCE_BYTES).also(random::nextBytes)
        nonce = fresh
        issuedAt = clock()
        return fresh.copyOf()
    }

    sealed interface Outcome {
        data class Accepted(val command: Command, val counter: Long) : Outcome
        data class Rejected(val reason: CommandResult) : Outcome
    }

    @Synchronized
    fun verify(frame: ByteArray): Outcome {
        if (frame.size != CommandFrame.SIZE || frame[0] != KeyProtocol.VERSION) {
            return Outcome.Rejected(CommandResult.BAD_FRAME)
        }
        val current = nonce ?: return Outcome.Rejected(CommandResult.STALE_NONCE)
        // Single use: consume before anything else so a failed attempt can't be retried on the same nonce.
        nonce = null
        if (clock() - issuedAt > KeyProtocol.NONCE_TTL_MS) return Outcome.Rejected(CommandResult.STALE_NONCE)

        val buffer = ByteBuffer.wrap(frame)
        buffer.get()
        val code = buffer.get()
        val counter = buffer.getLong()
        val tag = ByteArray(KeyProtocol.MAC_BYTES).also { buffer.get(it) }

        val expected = mac.mac(CommandFrame.macInput(current, code, counter)).copyOf(KeyProtocol.MAC_BYTES)
        if (!MessageDigest.isEqual(expected, tag)) return Outcome.Rejected(CommandResult.BAD_MAC)
        if (counter <= lastCounter) return Outcome.Rejected(CommandResult.REPLAYED)
        val command = Command.of(code) ?: return Outcome.Rejected(CommandResult.BAD_FRAME)
        lastCounter = counter
        return Outcome.Accepted(command, counter)
    }
}
