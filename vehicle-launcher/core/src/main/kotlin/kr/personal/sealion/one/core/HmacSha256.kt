package kr.personal.sealion.one.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Plain JVM HMAC, used for tests and as a fallback when the keystore is unavailable. */
class HmacSha256(key: ByteArray) : MacFunction {
    private val spec = SecretKeySpec(key.copyOf(), "HmacSHA256")
    override fun mac(data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(spec) }.doFinal(data)
}
