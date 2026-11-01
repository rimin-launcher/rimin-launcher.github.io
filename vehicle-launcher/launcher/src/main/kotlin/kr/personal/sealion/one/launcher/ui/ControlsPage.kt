package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.personal.sealion.one.core.Command
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.service.VehicleService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Control(val command: Command, val icon: ImageVector)

private val controls = listOf(
    Control(Command.LOCK, Icons.Rounded.Lock),
    Control(Command.UNLOCK, Icons.Rounded.LockOpen),
    Control(Command.TRUNK_OPEN, Icons.Rounded.Luggage),
    Control(Command.WINDOWS_CLOSE, Icons.Rounded.Window),
    Control(Command.CLIMATE_ON, Icons.Rounded.AcUnit),
    Control(Command.CLIMATE_OFF, Icons.Rounded.PowerSettingsNew),
    Control(Command.FIND_CAR, Icons.Rounded.CarRepair),
)

@Composable
fun ControlsPage() {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val events by app.events.flow.collectAsStateWithLifecycle()
    val keyRunning by VehicleService.phoneKeyRunning.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(controls) { control ->
                Tile(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.Default) { app.monitor.execute(control.command, "화면") }
                        last = "${control.command.label}: ${result.label}"
                    }
                }) {
                    Icon(control.icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(control.command.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Tile(Modifier.weight(1f).fillMaxHeight()) {
            Row {
                Icon(Icons.Rounded.Sensors, null, tint = if (keyRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Text(
                    if (keyRunning) "  휴대폰 키 대기 중" else "  휴대폰 키 꺼짐",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            last?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            TileLabel("최근 동작")
            val format = remember { SimpleDateFormat("M/d HH:mm:ss", Locale.KOREAN) }
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(events.take(60)) { e ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(e.message, fontSize = 15.sp)
                        Text("${format.format(Date(e.at))} · ${e.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
