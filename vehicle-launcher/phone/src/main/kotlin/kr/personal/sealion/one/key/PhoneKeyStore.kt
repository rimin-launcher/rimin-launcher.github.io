package kr.personal.sealion.one.key

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.personal.sealion.one.core.MacFunction
import kr.personal.sealion.one.core.PairingPayload
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class PairedVehicle(val id: String, val name: String)

data class KeySettings(
    val autoUnlock: Boolean = true,
    val autoLock: Boolean = true,
    val requireWalking: Boolean = true,
    val nearRssi: Int = -68,
    val farRssi: Int = -86,
    val notifyActions: Boolean = true,
)

data class KeyEvent(val at: Long, val message: String)

/**
 * The pairing secret is imported into Android Keystore as a non-exportable HMAC key;
 * only the vehicle id/name and settings live in SharedPreferences.
 */
class PhoneKeyStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("phone_key", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val vehicleState = MutableStateFlow(loadVehicle())
    val vehicle: StateFlow<PairedVehicle?> = vehicleState
    private val settingsState = MutableStateFlow(loadSettings())
    val settings: StateFlow<KeySettings> = settingsState
    private val eventsState = MutableStateFlow(loadEvents())
    val events: StateFlow<List<KeyEvent>> = eventsState

    fun pair(payload: PairingPayload) {
        keyStore.setEntry(
            ALIAS,
            KeyStore.SecretKeyEntry(SecretKeySpec(payload.key, KeyProperties.KEY_ALGORITHM_HMAC_SHA256)),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build(),
        )
        prefs.edit().putString("vehicleId", payload.vehicleId).putString("vehicleName", payload.vehicleName)
            .putLong("counter", 0).apply()
        vehicleState.value = PairedVehicle(payload.vehicleId, payload.vehicleName)
        log("${payload.vehicleName} 등록 완료")
    }

    fun unpair() {
        runCatching { keyStore.deleteEntry(ALIAS) }
        prefs.edit().remove("vehicleId").remove("vehicleName").apply()
        vehicleState.value = null
        log("차량 등록 해제")
    }

    fun mac(): MacFunction? {
        val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
        return MacFunction { data -> Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data) }
    }

    /** Strictly increasing and time-based, so it stays ahead of the car even after reinstalling. */
    @Synchronized
    fun nextCounter(): Long {
        val next = maxOf(prefs.getLong("counter", 0) + 1, System.currentTimeMillis())
        prefs.edit().putLong("counter", next).apply()
        return next
    }

    fun rememberAddress(address: String) {
        if (prefs.getString("address", null) != address) prefs.edit().putString("address", address).apply()
    }

    fun lastAddress(): String? = prefs.getString("address", null)

    fun updateSettings(transform: (KeySettings) -> KeySettings) {
        val s = transform(settingsState.value)
        settingsState.value = s
        prefs.edit()
            .putBoolean("autoUnlock", s.autoUnlock).putBoolean("autoLock", s.autoLock)
            .putBoolean("requireWalking", s.requireWalking).putInt("nearRssi", s.nearRssi)
            .putInt("farRssi", s.farRssi).putBoolean("notifyActions", s.notifyActions)
            .apply()
    }

    @Synchronized
    fun log(message: String) {
        val next = (listOf(KeyEvent(System.currentTimeMillis(), message)) + eventsState.value).take(150)
        eventsState.value = next
        prefs.edit().putString("events", JSONArray(next.map { JSONObject().put("at", it.at).put("m", it.message) }).toString()).apply()
    }

    private fun loadVehicle(): PairedVehicle? {
        val id = prefs.getString("vehicleId", null) ?: return null
        if (!keyStore.containsAlias(ALIAS)) return null
        return PairedVehicle(id, prefs.getString("vehicleName", "내 차")!!)
    }

    private fun loadSettings(): KeySettings {
        val d = KeySettings()
        return KeySettings(
            autoUnlock = prefs.getBoolean("autoUnlock", d.autoUnlock),
            autoLock = prefs.getBoolean("autoLock", d.autoLock),
            requireWalking = prefs.getBoolean("requireWalking", d.requireWalking),
            nearRssi = prefs.getInt("nearRssi", d.nearRssi),
            farRssi = prefs.getInt("farRssi", d.farRssi),
            notifyActions = prefs.getBoolean("notifyActions", d.notifyActions),
        )
    }

    private fun loadEvents(): List<KeyEvent> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]"))
        List(a.length()) { a.getJSONObject(it).let { o -> KeyEvent(o.getLong("at"), o.getString("m")) } }
    }.getOrDefault(emptyList())

    companion object {
        private const val ALIAS = "sealion_one_vehicle_key"
        @Volatile private var instance: PhoneKeyStore? = null
        fun get(context: Context): PhoneKeyStore =
            instance ?: synchronized(this) { instance ?: PhoneKeyStore(context.applicationContext).also { instance = it } }
    }
}
