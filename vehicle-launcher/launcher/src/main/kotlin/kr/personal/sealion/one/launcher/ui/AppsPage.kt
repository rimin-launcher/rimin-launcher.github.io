package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.data.LauncherSettings
import kr.personal.sealion.one.launcher.system.AppEntry
import kr.personal.sealion.one.launcher.system.SystemTools

/** All apps. Tap to open, long-press to add/remove from the dock. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsPage(apps: List<AppEntry>, settings: LauncherSettings) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("앱 검색 · 길게 누르면 독에 추가/제거") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        LazyVerticalGrid(columns = GridCells.Adaptive(116.dp), modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.component.flattenToString() }) { entry ->
                val inDock = entry.packageName in settings.dockApps
                val bitmap = remember(entry.component) { entry.icon.toBitmap(144, 144).asImageBitmap() }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { SystemTools.launch(context, entry.component) },
                            onLongClick = {
                                context.app.settings.update { s ->
                                    s.copy(dockApps = if (inDock) s.dockApps - entry.packageName else s.dockApps + entry.packageName)
                                }
                            },
                        )
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(bitmap, entry.label, Modifier.size(64.dp))
                    Text(
                        (if (inDock) "★ " else "") + entry.label,
                        fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center,
                        color = if (inDock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }
    }
}
