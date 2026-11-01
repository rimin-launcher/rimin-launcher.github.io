package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.data.LauncherSettings
import kr.personal.sealion.one.launcher.data.Speedometer
import kr.personal.sealion.one.launcher.media.MediaHub
import kr.personal.sealion.one.launcher.system.SystemTools
import kr.personal.sealion.one.launcher.vehicle.VehicleState
import kotlin.math.roundToInt

private fun Double?.fmt(unit: String, digits: Int = 0) =
    if (this == null) "—" else "%.${digits}f".format(this) + unit

@Composable
fun DashboardPage(vehicle: VehicleState, settings: LauncherSettings) {
    val context = LocalContext.current
    val trips by context.app.driveLog.trips.collectAsStateWithLifecycle()
    val todayStart = remember0()
    val todayKm = trips.filter { it.start >= todayStart }.sumOf { it.distanceKm } + (context.app.driveLog.activeDistanceKm ?: 0.0)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Tile(Modifier.weight(1.1f).fillMaxHeight()) {
            TileLabel("속도")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpeedGauge(vehicle.speedKmh, settings.speedometer)
                Text(
                    vehicle.gear.label,
                    fontSize = 40.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    TileLabel("배터리")
                    Spacer(Modifier.weight(1f))
                    Text(vehicle.socPercent.fmt("%"), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ((vehicle.socPercent ?: 0.0) / 100).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    TileLabel("주행 가능")
                    Spacer(Modifier.weight(1f))
                    Text(vehicle.rangeKm.fmt(" km"), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("오늘 ${"%.1f".format(todayKm)} km", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    TileLabel("실내 · 실외")
                    Spacer(Modifier.weight(1f))
                    Text("${vehicle.insideTempC.fmt("°", 1)} / ${vehicle.outsideTempC.fmt("°", 1)}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (vehicle.acOn) { true -> "공조 켜짐"; false -> "공조 꺼짐"; null -> "공조 상태 미지원" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MediaTile(Modifier.weight(1.4f).fillMaxHeight()) { settings.mediaPackage?.let { SystemTools.launch(context, it) } }
            }
        }
    }
}

@Composable
private fun remember0(): Long = androidx.compose.runtime.remember {
    java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun SpeedGauge(speed: Double?, style: Speedometer) {
    val value = speed ?: 0.0
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight(0.9f).aspectRatio(1f)) {
        if (style == Speedometer.ANALOG) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val stroke = size.minDimension * 0.07f
                val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = (value.coerceIn(0.0, 200.0) / 200.0 * 270).toFloat()
                drawArc(primary, 135f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (speed == null) "—" else value.roundToInt().toString(), fontSize = 88.sp, fontWeight = FontWeight.Black)
            Text("km/h", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MediaTile(modifier: Modifier, onOpen: () -> Unit) {
    val now by MediaHub.nowPlaying.collectAsStateWithLifecycle()
    Tile(modifier, onClick = onOpen) {
        TileLabel("미디어")
        val playing = now
        if (playing == null) {
            Spacer(Modifier.weight(1f))
            Text("재생 중인 미디어 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("설정 › 미디어에서 알림 접근을 허용하세요", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Tile
        }
        Row(Modifier.weight(1f).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            playing.art?.let {
                Image(it.asImageBitmap(), null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(playing.title.ifBlank { "제목 없음" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(playing.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = MediaHub::previous) { Icon(Icons.Rounded.SkipPrevious, "이전") }
            IconButton(onClick = MediaHub::playPause) {
                Icon(if (playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "재생/일시정지")
            }
            IconButton(onClick = MediaHub::next) { Icon(Icons.Rounded.SkipNext, "다음") }
        }
    }
}
