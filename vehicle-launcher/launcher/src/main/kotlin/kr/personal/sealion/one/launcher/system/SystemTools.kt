package kr.personal.sealion.one.launcher.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.core.content.ContextCompat

data class AppEntry(val label: String, val packageName: String, val component: ComponentName, val icon: Drawable)

object SystemTools {
    fun canWriteSecureSettings(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Turns on wireless debugging (Android 11+). Needs WRITE_SECURE_SETTINGS granted via ADB once. */
    fun enableWirelessAdb(context: Context): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
        }.getOrDefault(false)
    }

    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.isSuccess
    }

    fun launch(context: Context, component: ComponentName) = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }.isSuccess

    fun openHomeSettings(context: Context) = runCatching {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun openNotificationAccess(context: Context) = runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Known Korean navigation apps, shown first in the picker when installed. */
    val navigationCandidates = listOf(
        "com.skt.tmap.ku", "com.skt.skaf.l001mtm091", "com.nhn.android.nmap", "net.daum.android.map",
        "com.kakao.taxi", "com.locnall.KimGiSa", "com.google.android.apps.maps", "com.waze",
    )
}
