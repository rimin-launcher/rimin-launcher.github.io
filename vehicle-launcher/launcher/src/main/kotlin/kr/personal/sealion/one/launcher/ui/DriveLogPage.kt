package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.data.LauncherSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DriveLogPage(settings: LauncherSettings) {
    val trips by LocalContext.current.app.driveLog.trips.collectAsStateWithLifecycle()
    val monthStart = remember {
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
    }
    val month = trips.filter { it.start >= monthStart }
    val monthKm = month.sumOf { it.distanceKm }
    val monthKwh = month.sumOf { it.energyKwh(settings.batteryKwh) ?: 0.0 }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile(Modifier.fillMaxWidth()) {
                TileLabel("이번 달 주행")
                Text("%.1f km".format(monthKm), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("${month.size}회 주행", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tile(Modifier.fillMaxWidth()) {
                TileLabel("추정 전력 · 비용")
                Text("%.1f kWh".format(monthKwh), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("약 %,d원 (%,d원/kWh)".format((monthKwh * settings.chargePriceWonPerKwh).toInt(), settings.chargePriceWonPerKwh),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (monthKwh > 0) Text("전비 %.1f km/kWh".format(monthKm / monthKwh), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Tile(Modifier.weight(1.6f).fillMaxHeight()) {
            TileLabel("최근 주행")
            if (trips.isEmpty()) {
                Spacer(Modifier.padding(8.dp))
                Text("아직 기록된 주행이 없습니다. 차량이 움직이면 자동으로 기록합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val format = remember { SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN) }
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(trips) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(format.format(Date(t.start)), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("%.0f분 · 평균 %.0f km/h · 최고 %.0f km/h".format(t.durationMin, t.avgSpeedKmh, t.maxSpeedKmh),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.1f km".format(t.distanceKm), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
