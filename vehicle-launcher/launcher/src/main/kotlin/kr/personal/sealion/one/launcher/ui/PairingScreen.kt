package kr.personal.sealion.one.launcher.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.service.VehicleService

@Composable
fun PairingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    var generation by remember { mutableIntStateOf(0) }
    val pairing = remember(generation, settings.vehicleName) { app.keyStore.pairing(settings.vehicleName) }
    val qr = remember(pairing) { qrBitmap(pairing.encode(), 720) }
    var confirmRotate by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
                Text("휴대폰 등록", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            listOf(
                "1. 휴대폰에 Sealion One Key 앱을 설치합니다.",
                "2. 앱에서 ‘차량 등록’을 누르고 오른쪽 QR 코드를 스캔합니다.",
                "3. 블루투스·근처 기기·신체 활동 권한과 배터리 최적화 제외를 허용합니다.",
                "4. 차에서 걸어 나가면 잠기고, 다가오면 열립니다 (각각 앱에서 켜고 끌 수 있습니다).",
            ).forEach { Text(it, fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("차량: ${pairing.vehicleName} · ID ${pairing.vehicleId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "QR 코드는 차량 키입니다. 다른 사람에게 보여주지 마세요. 휴대폰을 분실했다면 새 키를 만들어 기존 휴대폰을 모두 해제하세요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmRotate = true }) { Text("새 키 만들기 (기존 휴대폰 해제)") }
        }
        Spacer(Modifier.width(24.dp))
        Image(
            qr.asImageBitmap(), "등록 QR 코드",
            Modifier.fillMaxHeight().size(420.dp).clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color.White).padding(16.dp),
        )
    }

    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text("새 키를 만들까요?") },
            text = { Text("지금까지 등록한 모든 휴대폰이 더 이상 이 차량을 잠그거나 열 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    app.keyStore.rotate(settings.vehicleName)
                    VehicleService.start(context, VehicleService.ACTION_REKEY)
                    app.events.add("설정", "휴대폰 키를 새로 만들었습니다")
                    generation++
                    confirmRotate = false
                }) { Text("새로 만들기") }
            },
            dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("취소") } },
        )
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
