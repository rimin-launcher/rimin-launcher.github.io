package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.personal.sealion.one.launcher.app

/** Lists what the head unit's BYD SDK actually exposes so method candidates can be verified on the car. */
@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val app = LocalContext.current.app
    val state by app.monitor.flow.collectAsStateWithLifecycle()
    val sections = remember { app.byd.devices.map { it.className.substringAfterLast('.') to it.describe() } }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
            Text("진단 · 차량 SDK", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        }
        Text("현재 값: $state", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        SelectionContainer {
            LazyColumn {
                sections.forEach { (title, lines) ->
                    item { SectionTitle(title) }
                    items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                }
            }
        }
    }
}
