package kr.personal.sealion.one.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun TileLabel(text: String) = Text(
    text.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
)

@Composable
fun SectionTitle(text: String) = Text(
    text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
)

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange) }

@Composable
fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0,
    format: (Float) -> String, onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row {
            Text(title, fontSize = 17.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(format(local), fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = local, onValueChange = { local = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(local) })
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
fun Dot(on: Boolean) = Box(
    Modifier
        .padding(end = 6.dp)
        .clip(RoundedCornerShape(50))
        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        .padding(5.dp),
)
