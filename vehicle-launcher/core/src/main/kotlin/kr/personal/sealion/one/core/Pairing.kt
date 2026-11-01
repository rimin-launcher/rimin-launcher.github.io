package kr.personal.sealion.one.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Pairing data shown as a QR code on the vehicle screen and scanned by the phone.
 * Text form: `SLONE1;<vehicleId>;<name>;<base64url key>`
 */
data class PairingPayload(val vehicleId: String, val vehicleName: String, val key: ByteArray) {
    init {
        require(vehicleId.matches(Regex("[0-9a-f]{12}"))) { "vehicleId must be 12 hex chars" }
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        require(';' !in vehicleName) { "name must not contain ';'" }
    }

    fun encode(): String =
        listOf(PREFIX, vehicleId, vehicleName, Base64.getUrlEncoder().withoutPadding().encodeToString(key))
            .joinToString(";")

    /** 6-byte tag the vehicle advertises as service data so phones can find *their* car. */
    fun advertisedTag(): ByteArray = advertisedTag(vehicleId)

    override fun equals(other: Any?): Boolean =
        other is PairingPayload && other.vehicleId == vehicleId && other.vehicleName == vehicleName &&
            other.key.contentEquals(key)

    override fun hashCode(): Int = vehicleId.hashCode()

    companion object {
        const val PREFIX = "SLONE1"
        const val KEY_BYTES = 32

        fun generate(vehicleName: String, random: SecureRandom = SecureRandom()): PairingPayload {
            val id = ByteArray(6).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val key = ByteArray(KEY_BYTES).also(random::nextBytes)
            return PairingPayload(id, vehicleName.replace(";", " ").take(24), key)
        }

        fun decode(text: String): PairingPayload? = runCatching {
            val parts = text.trim().split(";")
            if (parts.size != 4 || parts[0] != PREFIX) return null
            PairingPayload(parts[1], parts[2], Base64.getUrlDecoder().decode(parts[3]))
        }.getOrNull()

        fun advertisedTag(vehicleId: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(("SLONE-ADV:$vehicleId").toByteArray()).copyOf(6)
    }
}
