package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.vehicle.ClimateAction
import kr.personal.sealion.one.launcher.vehicle.ClimateState
import kr.personal.sealion.one.launcher.vehicle.ClimateToggle
import kr.personal.sealion.one.launcher.vehicle.Seat
import kr.personal.sealion.one.launcher.vehicle.levelText

private val Heat = Color(0xFFFF7A45)
private val Vent = Color(0xFF4FC3F7)

/** Sends a climate action off the main thread. */
@Composable
fun rememberClimateSender(): (ClimateAction) -> Unit {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    return { action -> scope.launch(Dispatchers.Default) { app.monitor.climate(action) } }
}

private fun step(temp: Double?, delta: Double) =
    ((temp ?: 22.0) + delta).coerceIn(ClimateState.MIN_TEMP, ClimateState.MAX_TEMP)

private fun next(level: Int?) = ((level ?: 0) + 1) % (ClimateState.MAX_SEAT_LEVEL + 1)

/** Large round button used everywhere in climate UI (≥ 64dp touch target for driving). */
@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Dp = 64.dp, active: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val on = tint ?: MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50))
            .background(if (active) on.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size * 0.45f), tint = if (active) on else MaterialTheme.colorScheme.onSurface)
    }
}

/** Level indicator dots under seat buttons (e.g. ●●○ = 2단계). */
@Composable
private fun LevelDots(level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
        repeat(ClimateState.MAX_SEAT_LEVEL) { i ->
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(if (i < level) color else MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun SeatQuick(icon: ImageVector, label: String, level: Int?, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(icon, label, size = 52.dp, active = (level ?: 0) > 0, tint = color, onClick = onClick)
        LevelDots(level ?: 0, color)
    }
}

@Composable
private fun TempStepper(temp: Double?, compact: Boolean, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Rounded.Remove, "온도 내림", size = if (compact) 48.dp else 72.dp) { onChange(step(temp, -0.5)) }
        Text(
            temp?.let { "%.1f°".format(it) } ?: "—",
            fontSize = if (compact) 24.sp else 56.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = if (compact) 76.dp else 170.dp).padding(horizontal = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundButton(Icons.Rounded.Add, "온도 올림", size = if (compact) 48.dp else 72.dp) { onChange(step(temp, 0.5)) }
    }
}

/**
 * Always-visible climate controls in the dock: driver temp, seat heat/vent,
 * steering heat, fan, passenger temp. Each tap cycles one step.
 */
@Composable
fun ClimateStrip(climate: ClimateState, onOpenPage: () -> Unit, modifier: Modifier = Modifier) {
    val send = rememberClimateSender()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TempStepper(climate.driverTempC, compact = true) { send(ClimateAction.Temperature(Seat.DRIVER, it)) }
        SeatQuick(Icons.Rounded.LocalFireDepartment, "운전석 열선", climate.seatHeat[Seat.DRIVER], Heat) {
            send(ClimateAction.SeatHeat(Seat.DRIVER, next(climate.seatHeat[Seat.DRIVER])))
        }
        SeatQuick(Icons.Rounded.Air, "운전석 통풍", climate.seatVent[Seat.DRIVER], Vent) {
            send(ClimateAction.SeatVent(Seat.DRIVER, next(climate.seatVent[Seat.DRIVER])))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenPage).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Rounded.AcUnit, "공조 열기", tint = if (climate.toggles[ClimateToggle.POWER] == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("풍량 ${climate.fanLevel ?: "—"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SeatQuick(Icons.Rounded.DirectionsCar, "핸들 열선", if (climate.steeringHeat == true) ClimateState.MAX_SEAT_LEVEL else 0, Heat) {
            send(ClimateAction.SteeringHeat(climate.steeringHeat != true))
        }
        SeatQuick(Icons.Rounded.LocalFireDepartment, "동승석 열선", climate.seatHeat[Seat.PASSENGER], Heat) {
            send(ClimateAction.SeatHeat(Seat.PASSENGER, next(climate.seatHeat[Seat.PASSENGER])))
        }
        TempStepper(climate.passengerTempC, compact = true) { send(ClimateAction.Temperature(Seat.PASSENGER, it)) }
    }
}

/** Full climate page: every control big enough to hit without looking twice. */
@Composable
fun ClimatePage(climate: ClimateState, insideTempC: Double? = null, outsideTempC: Double? = null) {
    val send = rememberClimateSender()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SeatPanel(Seat.DRIVER, climate, send, Modifier.weight(1f).fillMaxHeight())

        Tile(Modifier.weight(1.2f).fillMaxHeight()) {
            TileLabel("공조")
            Spacer(Modifier.height(12.dp))
            val toggles = listOf(
                ClimateToggle.POWER to Icons.Rounded.PowerSettingsNew,
                ClimateToggle.AC to Icons.Rounded.AcUnit,
                ClimateToggle.AUTO to Icons.Rounded.AutoMode,
                ClimateToggle.RECIRCULATION to Icons.Rounded.Sync,
                ClimateToggle.DEFROST_FRONT to Icons.Rounded.Window,
                ClimateToggle.DEFROST_REAR to Icons.Rounded.WbSunny,
            )
            toggles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (toggle, icon) ->
                        val on = climate.toggles[toggle] == true
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RoundButton(icon, toggle.label, size = 76.dp, active = on) { send(ClimateAction.Toggle(toggle, !on)) }
                            Text(toggle.label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("실내" to insideTempC, "실외" to outsideTempC).forEach { (label, t) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t?.let { "%.1f°".format(it) } ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TileLabel("풍량")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..ClimateState.MAX_FAN).forEach { level ->
                    val active = (climate.fanLevel ?: 0) >= level
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { send(ClimateAction.Fan(level)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$level", fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (climate.toggles.isEmpty() && climate.fanLevel == null) {
                Text("이 차량 SDK에서 공조 상태를 읽지 못했습니다. 설정 › 시스템 › 진단에서 확인하세요.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        SeatPanel(Seat.PASSENGER, climate, send, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SeatPanel(seat: Seat, climate: ClimateState, send: (ClimateAction) -> Unit, modifier: Modifier) {
    Tile(modifier) {
        TileLabel(seat.label)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val temp = if (seat == Seat.DRIVER) climate.driverTempC else climate.passengerTempC
            TempStepper(temp, compact = false) { send(ClimateAction.Temperature(seat, it)) }
        }
        Spacer(Modifier.height(16.dp))
        TempBar(if (seat == Seat.DRIVER) climate.driverTempC else climate.passengerTempC) { send(ClimateAction.Temperature(seat, it)) }
        Spacer(Modifier.weight(1f))
        LevelSelector("시트 열선", Icons.Rounded.LocalFireDepartment, climate.seatHeat[seat], Heat) { send(ClimateAction.SeatHeat(seat, it)) }
        Spacer(Modifier.height(14.dp))
        LevelSelector("시트 통풍", Icons.Rounded.AirlineSeatReclineNormal, climate.seatVent[seat], Vent) { send(ClimateAction.SeatVent(seat, it)) }
        if (seat == Seat.DRIVER) {
            Spacer(Modifier.height(14.dp))
            val on = climate.steeringHeat == true
            Row(
                Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Heat.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { send(ClimateAction.SteeringHeat(!on)) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = if (on) Heat else MaterialTheme.colorScheme.onSurface)
                Text("  핸들 열선", fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(if (on) "켜짐" else "꺼짐", color = if (on) Heat else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Segmented 끔 / 1 / 2 / 3 selector — one tap sets the exact level. */
@Composable
private fun LevelSelector(title: String, icon: ImageVector, level: Int?, color: Color, onSelect: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if ((level ?: 0) > 0) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text("  $title", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(level?.let(::levelText) ?: "미지원", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..ClimateState.MAX_SEAT_LEVEL).forEach { value ->
                val selected = (level ?: 0) == value
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) (if (value == 0) MaterialTheme.colorScheme.outline else color) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (value == 0) "끔" else "$value", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = if (selected && value > 0) Color.Black else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}


/** Blue→red bar; tap or drag anywhere to jump straight to a temperature (0.5° steps). */
@Composable
private fun TempBar(temp: Double?, onSet: (Double) -> Unit) {
    val range = ClimateState.MAX_TEMP - ClimateState.MIN_TEMP
    fun toTemp(x: Float, width: Int) =
        (ClimateState.MIN_TEMP + (x / width).coerceIn(0f, 1f) * range).let { Math.round(it * 2) / 2.0 }
    Column {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .pointerInput(Unit) { detectTapGestures { onSet(toTemp(it.x, size.width)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = {}) { change, _ -> onSet(toTemp(change.position.x, size.width)) }
                },
        ) {
            val barH = size.height * 0.36f
            val top = (size.height - barH) / 2
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF4FC3F7), Color(0xFFB0BEC5), Color(0xFFFF7043))),
                topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2),
            )
            temp?.let {
                val x = ((it - ClimateState.MIN_TEMP) / range).toFloat().coerceIn(0f, 1f) * size.width
                drawCircle(Color.White, radius = size.height / 2.4f, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("LO ${ClimateState.MIN_TEMP.toInt()}°", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("HI ${ClimateState.MAX_TEMP.toInt()}°", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
