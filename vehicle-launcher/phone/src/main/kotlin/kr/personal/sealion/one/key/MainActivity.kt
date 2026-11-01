package kr.personal.sealion.one.key

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.core.PairingPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val store by lazy { PhoneKeyStore.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerShortcuts()
        if (store.vehicle.value != null && hasRequiredPermissions()) KeyService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF3D9BFF), background = Color(0xFF07090D), surface = Color(0xFF12161D))) {
                KeyScreen()
            }
        }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun hasRequiredPermissions() = requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun ignoringBatteryOptimizations() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() = runCatching {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun registerShortcuts() {
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val items = listOf(
            Triple(Command.LOCK, R.string.shortcut_lock, R.drawable.ic_sc_lock),
            Triple(Command.UNLOCK, R.string.shortcut_unlock, R.drawable.ic_sc_unlock),
            Triple(Command.TRUNK_OPEN, R.string.shortcut_trunk, R.drawable.ic_sc_trunk),
            Triple(Command.CLIMATE_ON, R.string.shortcut_climate, R.drawable.ic_sc_climate),
        ).map { (command, label, icon) ->
            ShortcutInfo.Builder(this, command.name)
                .setShortLabel(getString(label))
                .setIcon(Icon.createWithResource(this, icon))
                .setIntent(Intent(this, ShortcutActivity::class.java).setAction("COMMAND").putExtra(KeyService.EXTRA_COMMAND, command.name))
                .build()
        }
        runCatching { manager.dynamicShortcuts = items }
    }

    @Composable
    private fun KeyScreen() {
        val vehicle by store.vehicle.collectAsStateWithLifecycle()
        val settings by store.settings.collectAsStateWithLifecycle()
        val status by KeyService.state.collectAsStateWithLifecycle()
        val events by store.events.collectAsStateWithLifecycle()
        var permissionsOk by remember { mutableStateOf(hasRequiredPermissions()) }
        var batteryOk by remember { mutableStateOf(ignoringBatteryOptimizations()) }
        var manualEntry by remember { mutableStateOf(false) }
        var confirmUnpair by remember { mutableStateOf(false) }
        var pairError by remember { mutableStateOf<String?>(null) }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsOk = hasRequiredPermissions()
            if (permissionsOk && store.vehicle.value != null) KeyService.start(this)
        }
        fun pair(text: String?) {
            val payload = text?.let(PairingPayload::decode)
            if (payload == null) {
                pairError = "Sealion One 차량 QR 코드가 아닙니다."
                return
            }
            store.pair(payload)
            pairError = null
            if (hasRequiredPermissions()) KeyService.start(this) else permissionLauncher.launch(requiredPermissions())
        }
        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(::pair) }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Sealion One Key", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            if (!permissionsOk || !batteryOk) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("필요한 설정", fontWeight = FontWeight.Bold)
                        Text("블루투스·근처 기기·신체 활동 권한과 배터리 최적화 제외가 있어야 백그라운드에서 자동 잠금·열림이 동작합니다.", fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (!permissionsOk) Button(onClick = { permissionLauncher.launch(requiredPermissions()) }) { Text("권한 허용") }
                            if (!batteryOk) OutlinedButton(onClick = { requestBatteryExemption(); batteryOk = true }) { Text("배터리 최적화 제외") }
                        }
                    }
                }
            }

            val v = vehicle
            if (v == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("차량 등록", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("차량 화면에서 설정 › 휴대폰 키 › 휴대폰 등록을 열고 QR 코드를 스캔하세요.", fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            Button(onClick = {
                                scanLauncher.launch(ScanOptions().setPrompt("차량 화면의 QR 코드를 비추세요").setBeepEnabled(false).setOrientationLocked(false))
                            }) { Text("QR 스캔") }
                            OutlinedButton(onClick = { manualEntry = true }) { Text("코드 붙여넣기") }
                        }
                        pairError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            } else {
                StatusCard(v, status)
                CommandGrid(enabled = !status.busy)
                SettingsCard(settings, status.stepSensor)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("동작 기록", fontWeight = FontWeight.Bold)
                        val f = remember { SimpleDateFormat("M/d HH:mm:ss", Locale.KOREAN) }
                        events.take(30).forEach {
                            Text("${f.format(Date(it.at))}  ${it.message}", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (status.running) KeyService.stop(this@MainActivity) else KeyService.start(this@MainActivity) }) {
                        Text(if (status.running) "감지 중지" else "감지 시작")
                    }
                    TextButton(onClick = { confirmUnpair = true }) { Text("등록 해제") }
                }
            }
        }

        if (manualEntry) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { manualEntry = false },
                title = { Text("등록 코드") },
                text = { OutlinedTextField(text, { text = it }, placeholder = { Text("SLONE1;…") }) },
                confirmButton = { TextButton(onClick = { pair(text); manualEntry = false }) { Text("등록") } },
                dismissButton = { TextButton(onClick = { manualEntry = false }) { Text("취소") } },
            )
        }
        if (confirmUnpair) {
            AlertDialog(
                onDismissRequest = { confirmUnpair = false },
                title = { Text("등록을 해제할까요?") },
                text = { Text("이 휴대폰에서 차량 키가 삭제됩니다. 다시 쓰려면 QR 코드를 다시 스캔해야 합니다.") },
                confirmButton = {
                    TextButton(onClick = { KeyService.stop(this@MainActivity); store.unpair(); confirmUnpair = false }) { Text("해제") }
                },
                dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("취소") } },
            )
        }
    }

    @Composable
    private fun StatusCard(v: PairedVehicle, status: KeyStatus) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(v.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val zone = when (status.zone) {
                    kr.personal.sealion.one.core.ProximityEngine.Zone.NEAR -> "차량 근처"
                    kr.personal.sealion.one.core.ProximityEngine.Zone.FAR -> "차량에서 멀어짐"
                    kr.personal.sealion.one.core.ProximityEngine.Zone.UNKNOWN -> "확인 중"
                }
                Text(if (status.running) "감지 중 · $zone" else "감지 꺼짐", color = MaterialTheme.colorScheme.primary)
                val ago = if (status.lastSeen == 0L) "신호 없음" else "${(System.currentTimeMillis() - status.lastSeen) / 1000}초 전 수신"
                Text("신호 ${status.rssi ?: "—"} dBm · $ago · 최근 걸음 ${status.recentSteps}", fontSize = 13.sp)
                status.lastResult?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }

    @Composable
    private fun CommandGrid(enabled: Boolean) {
        val rows = listOf(
            listOf(Triple(Command.LOCK, Icons.Rounded.Lock, "잠금"), Triple(Command.UNLOCK, Icons.Rounded.LockOpen, "열림")),
            listOf(Triple(Command.TRUNK_OPEN, Icons.Rounded.Luggage, "트렁크"), Triple(Command.WINDOWS_CLOSE, Icons.Rounded.Window, "창문 닫기")),
            listOf(Triple(Command.CLIMATE_ON, Icons.Rounded.AcUnit, "공조 켜기"), Triple(Command.FIND_CAR, Icons.Rounded.CarRepair, "차량 찾기")),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (command, icon, label) -> CommandButton(command, icon, label, enabled, Modifier.weight(1f)) }
            }
        }
    }

    @Composable
    private fun CommandButton(command: Command, icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier) {
        FilledTonalButton(
            onClick = { KeyService.start(this, command) }, enabled = enabled,
            modifier = modifier.height(72.dp), shape = RoundedCornerShape(18.dp),
        ) {
            Icon(icon, null, Modifier.size(26.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, fontSize = 17.sp)
        }
    }

    @Composable
    private fun SettingsCard(s: KeySettings, stepSensor: Boolean) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("자동 잠금 · 열림", fontWeight = FontWeight.Bold)
                ToggleRow("다가가면 자동 열림", s.autoUnlock) { v -> store.updateSettings { it.copy(autoUnlock = v) } }
                ToggleRow("멀어지면 자동 잠금", s.autoLock) { v -> store.updateSettings { it.copy(autoLock = v) } }
                ToggleRow(
                    if (stepSensor) "걸을 때만 판단 (오작동 방지)" else "걸음 센서 없음 — 신호 세기만 사용",
                    s.requireWalking && stepSensor,
                ) { v -> store.updateSettings { it.copy(requireWalking = v) } }
                ToggleRow("동작 시 진동·알림", s.notifyActions) { v -> store.updateSettings { it.copy(notifyActions = v) } }
                RssiSlider("열림 거리 (가까울수록 강한 신호)", s.nearRssi, -90..-40) { v -> store.updateSettings { it.copy(nearRssi = v) } }
                RssiSlider("잠금 거리 (멀수록 약한 신호)", s.farRssi, -100..-60) { v -> store.updateSettings { it.copy(farRssi = v) } }
                Text("열림 기준은 잠금 기준보다 최소 8dBm 강해야 경계에서 반복 동작하지 않습니다.", fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked, onChange)
        }
    }

    @Composable
    private fun RssiSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
        var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
        Column(Modifier.padding(top = 8.dp)) {
            Text("$label: ${local.toInt()} dBm", fontSize = 14.sp)
            Slider(local, { local = it }, valueRange = range.first.toFloat()..range.last.toFloat(), onValueChangeFinished = { onChange(local.toInt()) })
        }
    }
}
