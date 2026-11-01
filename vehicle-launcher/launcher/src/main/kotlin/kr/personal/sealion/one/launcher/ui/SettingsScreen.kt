package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.data.Accent
import kr.personal.sealion.one.launcher.data.LauncherSettings
import kr.personal.sealion.one.launcher.data.Speedometer
import kr.personal.sealion.one.launcher.system.AppEntry
import kr.personal.sealion.one.launcher.system.SystemTools

private enum class Section(val title: String) {
    DISPLAY("화면"), DOCK("독 · 앱"), CAR("차량 자동화"), KEY("휴대폰 키"), SOUND("음성 안내"),
    LOG("주행 기록"), MEDIA("내비 · 미디어"), SYSTEM("시스템"),
}

private enum class Picker { NONE, NAV, MEDIA }

@Composable
fun SettingsScreen(apps: List<AppEntry>, onClose: () -> Unit, onOpen: (Overlay) -> Unit) {
    val context = LocalContext.current
    val store = context.app.settings
    val s by store.flow.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(Section.DISPLAY) }
    var picker by remember { mutableStateOf(Picker.NONE) }
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = store::update

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(16.dp)) {
            Row {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "닫기") }
                Text("설정", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            }
            Section.entries.forEach { item ->
                val selected = item == section
                Text(
                    item.title,
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { section = item }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(section.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            when (section) {
                Section.DISPLAY -> {
                    ChoiceRow("강조 색상", Accent.entries, s.accent, { it.label }) { a -> update { it.copy(accent = a) } }
                    SwitchRow("어두운 화면", checked = s.darkMode) { v -> update { it.copy(darkMode = v) } }
                    SwitchRow("24시간제", checked = s.use24h) { v -> update { it.copy(use24h = v) } }
                    SwitchRow("전체 화면", "Android 상태 표시줄을 숨깁니다", s.fullScreen) { v -> update { it.copy(fullScreen = v) } }
                    ChoiceRow("속도계", Speedometer.entries, s.speedometer, { it.label }) { v -> update { it.copy(speedometer = v) } }
                }
                Section.DOCK -> {
                    SwitchRow("앱 이름 표시", checked = s.showAppLabels) { v -> update { it.copy(showAppLabels = v) } }
                    SettingRow("독 앱", "앱 화면에서 아이콘을 길게 눌러 추가/제거합니다 (${s.dockApps.size}개)")
                    s.dockApps.forEach { pkg ->
                        val label = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        SettingRow(label, onClick = { update { it.copy(dockApps = it.dockApps - pkg) } }) { Text("제거", color = MaterialTheme.colorScheme.primary) }
                    }
                    SettingRow("기본 홈 앱 설정", "Android 설정에서 Sealion One을 기본 홈으로 선택합니다",
                        onClick = { SystemTools.openHomeSettings(context) })
                }
                Section.CAR -> {
                    SliderRow("주행 중 자동 잠금", s.autoLockSpeedKmh.toFloat(), 0f..40f, 7,
                        { if (it < 1) "끄기" else "${it.toInt()} km/h 이상" }) { v -> update { it.copy(autoLockSpeedKmh = v.toInt()) } }
                    SwitchRow("P단에서 자동 열림", "주행(D/R)에서 P로 바꾸면 문을 엽니다", s.autoUnlockOnPark) { v ->
                        update { it.copy(autoUnlockOnPark = v) }
                    }
                    SettingRow("차량 연결", "${context.app.vehicle.name} 사용 중")
                }
                Section.KEY -> {
                    SwitchRow("휴대폰 키 사용", "차량 화면이 켜져 있는 동안 휴대폰으로 잠금·열림", s.phoneKeyEnabled) { v ->
                        update { it.copy(phoneKeyEnabled = v) }
                    }
                    SettingRow("휴대폰 등록", "QR 코드를 Sealion One Key 앱으로 스캔합니다", onClick = { onOpen(Overlay.PAIRING) }) {
                        Text("열기", color = MaterialTheme.colorScheme.primary)
                    }
                    var name by remember(s.vehicleName) { mutableStateOf(s.vehicleName) }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24) }, label = { Text("차량 이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    TextButton(onClick = { update { it.copy(vehicleName = name.ifBlank { "SEALION 7" }) } }) { Text("이름 저장") }
                }
                Section.SOUND -> {
                    SwitchRow("기어 변경 안내", checked = s.announceGear) { v -> update { it.copy(announceGear = v) } }
                    SliderRow("과속 알림", s.overspeedKmh.toFloat(), 0f..160f, 15,
                        { if (it < 1) "끄기" else "${it.toInt()} km/h" }) { v -> update { it.copy(overspeedKmh = v.toInt()) } }
                    SliderRow("안내 음량", s.announcementVolume, 0f..1f, 9, { "${(it * 100).toInt()}%" }) { v ->
                        update { it.copy(announcementVolume = v) }
                    }
                }
                Section.LOG -> {
                    SwitchRow("주행 기록", "주행 기록 페이지를 홈에 추가합니다", s.driveLogEnabled) { v -> update { it.copy(driveLogEnabled = v) } }
                    SliderRow("충전 단가", s.chargePriceWonPerKwh.toFloat(), 100f..600f, 49, { "${it.toInt()}원/kWh" }) { v ->
                        update { it.copy(chargePriceWonPerKwh = v.toInt()) }
                    }
                    SliderRow("배터리 용량", s.batteryKwh.toFloat(), 60f..100f, 79, { "%.1f kWh".format(it) }) { v ->
                        update { it.copy(batteryKwh = v.toDouble()) }
                    }
                    var confirm by remember { mutableStateOf(false) }
                    SettingRow("기록 지우기", "되돌릴 수 없습니다", onClick = { confirm = true })
                    if (confirm) {
                        AlertDialog(
                            onDismissRequest = { confirm = false },
                            title = { Text("주행 기록을 모두 지울까요?") },
                            confirmButton = { TextButton(onClick = { context.app.driveLog.clear(); confirm = false }) { Text("지우기") } },
                            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
                        )
                    }
                }
                Section.MEDIA -> {
                    SettingRow("내비게이션 앱", appLabel(apps, s.navPackage), onClick = { picker = Picker.NAV })
                    SettingRow("미디어 앱", appLabel(apps, s.mediaPackage), onClick = { picker = Picker.MEDIA })
                    SettingRow("알림 접근 허용", "재생 정보·컨트롤을 홈에 표시하려면 필요합니다",
                        onClick = { SystemTools.openNotificationAccess(context) })
                }
                Section.SYSTEM -> {
                    val canAdb = SystemTools.canWriteSecureSettings(context)
                    SwitchRow(
                        "시작 시 무선 ADB 켜기",
                        if (canAdb) "부팅할 때마다 무선 디버깅을 켭니다" else "먼저 PC에서 WRITE_SECURE_SETTINGS 권한을 부여하세요 (README 참고)",
                        s.wirelessAdbOnStart && canAdb,
                    ) { v -> update { it.copy(wirelessAdbOnStart = v) }; if (v) SystemTools.enableWirelessAdb(context) }
                    SwitchRow("데모 모드", "차량 SDK 대신 가상 차량 데이터를 사용합니다", s.demoMode) { v -> update { it.copy(demoMode = v) } }
                    SettingRow("진단 · 차량 SDK", "헤드유닛에서 사용 가능한 BYD SDK 기능을 확인합니다", onClick = { onOpen(Overlay.DIAGNOSTICS) })
                    SettingRow("버전", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                }
            }
        }
    }

    if (picker != Picker.NONE) {
        val sorted = if (picker == Picker.NAV) {
            apps.sortedBy { e -> SystemTools.navigationCandidates.indexOf(e.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
        } else apps
        AlertDialog(
            onDismissRequest = { picker = Picker.NONE },
            title = { Text(if (picker == Picker.NAV) "내비게이션 앱" else "미디어 앱") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sorted) { e ->
                        Text(e.label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable {
                            update { if (picker == Picker.NAV) it.copy(navPackage = e.packageName) else it.copy(mediaPackage = e.packageName) }
                            picker = Picker.NONE
                        }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = Picker.NONE }) { Text("닫기") } },
        )
    }
}

private fun appLabel(apps: List<AppEntry>, pkg: String?) =
    pkg?.let { p -> apps.firstOrNull { it.packageName == p }?.label ?: p } ?: "선택 안 함"
