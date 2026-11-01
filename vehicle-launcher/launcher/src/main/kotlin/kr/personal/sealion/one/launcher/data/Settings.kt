package kr.personal.sealion.one.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Accent(val label: String, val argb: Long) {
    OCEAN("오션", 0xFF3D9BFF),
    TEAL("틸", 0xFF20C3A6),
    AMBER("앰버", 0xFFFFB020),
    CORAL("코랄", 0xFFFF6B5A),
    VIOLET("바이올렛", 0xFF9C7CFF),
    LIME("라임", 0xFF9BD447),
}

enum class Speedometer(val label: String) { DIGITAL("숫자"), ANALOG("아날로그") }

data class LauncherSettings(
    // 화면
    val accent: Accent = Accent.OCEAN,
    val darkMode: Boolean = true,
    val use24h: Boolean = true,
    val fullScreen: Boolean = false,
    val speedometer: Speedometer = Speedometer.ANALOG,
    val showAppLabels: Boolean = true,
    // 독 · 앱
    val dockApps: List<String> = emptyList(),
    val navPackage: String? = null,
    val mediaPackage: String? = null,
    // 차량 자동화
    val autoLockSpeedKmh: Int = 15,
    val autoUnlockOnPark: Boolean = false,
    val announceGear: Boolean = false,
    val overspeedKmh: Int = 0,
    val announcementVolume: Float = 0.8f,
    // 휴대폰 키
    val phoneKeyEnabled: Boolean = true,
    val vehicleName: String = "SEALION 7",
    // 주행 기록
    val driveLogEnabled: Boolean = true,
    val chargePriceWonPerKwh: Int = 300,
    val batteryKwh: Double = 82.5,
    // 시스템
    val wirelessAdbOnStart: Boolean = false,
    val demoMode: Boolean = false,
)

/** SharedPreferences-backed settings exposed as a StateFlow so the UI recomposes on change. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<LauncherSettings> = state.asStateFlow()
    val value: LauncherSettings get() = state.value

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(state.value)
        state.value = next
        save(next)
    }

    private fun load(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            accent = enumOr(prefs.getString("accent", null), d.accent),
            darkMode = prefs.getBoolean("darkMode", d.darkMode),
            use24h = prefs.getBoolean("use24h", d.use24h),
            fullScreen = prefs.getBoolean("fullScreen", d.fullScreen),
            speedometer = enumOr(prefs.getString("speedometer", null), d.speedometer),
            showAppLabels = prefs.getBoolean("showAppLabels", d.showAppLabels),
            dockApps = prefs.getString("dockApps", "")!!.split(',').filter { it.isNotBlank() },
            navPackage = prefs.getString("navPackage", null),
            mediaPackage = prefs.getString("mediaPackage", null),
            autoLockSpeedKmh = prefs.getInt("autoLockSpeedKmh", d.autoLockSpeedKmh),
            autoUnlockOnPark = prefs.getBoolean("autoUnlockOnPark", d.autoUnlockOnPark),
            announceGear = prefs.getBoolean("announceGear", d.announceGear),
            overspeedKmh = prefs.getInt("overspeedKmh", d.overspeedKmh),
            announcementVolume = prefs.getFloat("announcementVolume", d.announcementVolume),
            phoneKeyEnabled = prefs.getBoolean("phoneKeyEnabled", d.phoneKeyEnabled),
            vehicleName = prefs.getString("vehicleName", d.vehicleName)!!,
            driveLogEnabled = prefs.getBoolean("driveLogEnabled", d.driveLogEnabled),
            chargePriceWonPerKwh = prefs.getInt("chargePriceWonPerKwh", d.chargePriceWonPerKwh),
            batteryKwh = prefs.getFloat("batteryKwh", d.batteryKwh.toFloat()).toDouble(),
            wirelessAdbOnStart = prefs.getBoolean("wirelessAdbOnStart", d.wirelessAdbOnStart),
            demoMode = prefs.getBoolean("demoMode", d.demoMode),
        )
    }

    private fun save(s: LauncherSettings) {
        prefs.edit()
            .putString("accent", s.accent.name)
            .putBoolean("darkMode", s.darkMode)
            .putBoolean("use24h", s.use24h)
            .putBoolean("fullScreen", s.fullScreen)
            .putString("speedometer", s.speedometer.name)
            .putBoolean("showAppLabels", s.showAppLabels)
            .putString("dockApps", s.dockApps.joinToString(","))
            .putString("navPackage", s.navPackage)
            .putString("mediaPackage", s.mediaPackage)
            .putInt("autoLockSpeedKmh", s.autoLockSpeedKmh)
            .putBoolean("autoUnlockOnPark", s.autoUnlockOnPark)
            .putBoolean("announceGear", s.announceGear)
            .putInt("overspeedKmh", s.overspeedKmh)
            .putFloat("announcementVolume", s.announcementVolume)
            .putBoolean("phoneKeyEnabled", s.phoneKeyEnabled)
            .putString("vehicleName", s.vehicleName)
            .putBoolean("driveLogEnabled", s.driveLogEnabled)
            .putInt("chargePriceWonPerKwh", s.chargePriceWonPerKwh)
            .putFloat("batteryKwh", s.batteryKwh.toFloat())
            .putBoolean("wirelessAdbOnStart", s.wirelessAdbOnStart)
            .putBoolean("demoMode", s.demoMode)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
