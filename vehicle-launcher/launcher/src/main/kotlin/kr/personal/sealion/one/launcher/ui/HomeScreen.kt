package kr.personal.sealion.one.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.personal.sealion.one.launcher.app
import kr.personal.sealion.one.launcher.system.AppEntry
import kr.personal.sealion.one.launcher.system.SystemTools
import kr.personal.sealion.one.launcher.vehicle.LockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Overlay { NONE, SETTINGS, PAIRING, DIAGNOSTICS }

private data class Page(val title: String, val content: @Composable () -> Unit)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val vehicle by app.monitor.flow.collectAsStateWithLifecycle()
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(overlay) { apps = SystemTools.launchableApps(context) }

    val pages = buildList {
        add(Page("대시보드") { DashboardPage(vehicle, settings) })
        add(Page("공조 · 시트") { ClimatePage(vehicle.climate, vehicle.insideTempC, vehicle.outsideTempC) })
        add(Page("차량 제어") { ControlsPage() })
        if (settings.driveLogEnabled) add(Page("주행 기록") { DriveLogPage(settings) })
        add(Page("앱") { AppsPage(apps, settings) })
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(use24h = settings.use24h, lock = vehicle.lock, source = vehicle.source)
            PageIndicator(pages.map { it.title }, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { index ->
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) { pages[index].content() }
            }
            Dock(
                climate = vehicle.climate,
                onClimate = { scope.launch { pager.animateScrollToPage(1) } },
                dockApps = settings.dockApps.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } },
                showLabels = settings.showAppLabels,
                onNav = {
                    val nav = settings.navPackage
                    if (nav == null || !SystemTools.launch(context, nav)) overlay = Overlay.SETTINGS
                },
                onMedia = { settings.mediaPackage?.let { SystemTools.launch(context, it) } },
                onApps = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } },
                onSettings = { overlay = Overlay.SETTINGS },
            )
        }

        if (overlay != Overlay.NONE) {
            BackHandler { overlay = if (overlay == Overlay.SETTINGS) Overlay.NONE else Overlay.SETTINGS }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (overlay) {
                    Overlay.SETTINGS -> SettingsScreen(apps, onClose = { overlay = Overlay.NONE }, onOpen = { overlay = it })
                    Overlay.PAIRING -> PairingScreen(onClose = { overlay = Overlay.SETTINGS })
                    Overlay.DIAGNOSTICS -> DiagnosticsScreen(onClose = { overlay = Overlay.SETTINGS })
                    Overlay.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun StatusBar(use24h: Boolean, lock: LockState, source: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val time = SimpleDateFormat(if (use24h) "HH:mm" else "a h:mm", Locale.KOREAN).format(Date(now))
    val date = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date(now))
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(16.dp))
        Text(date, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(source, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Icon(
            if (lock == LockState.UNLOCKED) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
            contentDescription = lock.label,
            tint = if (lock == LockState.UNLOCKED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(" ${lock.label}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageIndicator(titles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        titles.forEachIndexed { i, title ->
            val selected = i == current
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Dock(
    climate: kr.personal.sealion.one.launcher.vehicle.ClimateState,
    onClimate: () -> Unit,
    dockApps: List<AppEntry>, showLabels: Boolean,
    onNav: () -> Unit, onMedia: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DockButton(Icons.Rounded.Navigation, "내비", onNav)
        DockButton(Icons.Rounded.MusicNote, "미디어", onMedia)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
        Spacer(Modifier.width(8.dp))
        ClimateStrip(climate, onOpenPage = onClimate, modifier = Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
        dockApps.take(4).forEach { entry ->
            AppIcon(entry, showLabels, size = 44) { SystemTools.launch(context, entry.component) }
        }
        DockButton(Icons.Rounded.Apps, "앱", onApps)
        DockButton(Icons.Rounded.Settings, "설정", onSettings)
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppIcon(entry: AppEntry, showLabel: Boolean, size: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmap = remember(entry.packageName, entry.component) { entry.icon.toBitmap(144, 144).asImageBitmap() }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(bitmap, contentDescription = entry.label, modifier = Modifier.size(size.dp))
        if (showLabel) {
            Text(entry.label, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width((size + 24).dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
