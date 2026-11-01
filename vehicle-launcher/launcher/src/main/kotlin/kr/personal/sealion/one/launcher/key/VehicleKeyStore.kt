package kr.personal.sealion.one.launcher.key

import android.content.Context
import android.util.Base64
import kr.personal.sealion.one.core.PairingPayload

/**
 * Holds the vehicle's pairing secret. The raw key stays in app-private storage so
 * the QR code can be shown again to add another phone; "새 키 만들기" rotates it and
 * every previously paired phone stops working.
 */
class VehicleKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle_key", Context.MODE_PRIVATE)

    fun pairing(vehicleName: String): PairingPayload {
        val id = prefs.getString("id", null)
        val key = prefs.getString("key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (id != null && key != null) {
            return PairingPayload(id, prefs.getString("name", vehicleName)!!, key)
        }
        return rotate(vehicleName)
    }

    fun rotate(vehicleName: String): PairingPayload {
        val fresh = PairingPayload.generate(vehicleName)
        prefs.edit()
            .putString("id", fresh.vehicleId)
            .putString("name", fresh.vehicleName)
            .putString("key", Base64.encodeToString(fresh.key, Base64.NO_WRAP))
            .putLong("counter", 0)
            .apply()
        return fresh
    }

    var lastCounter: Long
        get() = prefs.getLong("counter", 0)
        set(value) = prefs.edit().putLong("counter", value).apply()
}
