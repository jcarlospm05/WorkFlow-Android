package com.workflow.operaciones.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workflow.core.DailyDocument
import com.workflow.core.Module
import com.workflow.core.Operation
import com.workflow.core.Operator
import com.workflow.core.Rules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun friendlyDay(day: String): String = LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.uppercase() }

@Composable internal fun WFCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = color,
        border = BorderStroke(.5.dp, Brand.Border.copy(alpha = .65f)), shadowElevation = 3.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable internal fun WFHeading(title: String, day: String, chooseDate: () -> Unit) {
    Column(Modifier.clickable(onClick = chooseDate).heightIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Row(Modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(friendlyDay(day), style = MaterialTheme.typography.bodyLarge, color = Brand.SecondaryText, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.ExpandMore, "Cambiar fecha", Modifier.size(18.dp), tint = Brand.SecondaryText)
        }
    }
}
@Composable internal fun WFIcon(icon: ImageVector, color: Color = Brand.Selected, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(13.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .55f).dp), tint = Brand.Petroleum)
    }
}
@Composable internal fun MetricTile(value: String, label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    WFCard(modifier.heightIn(min = 84.dp), color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WFIcon(icon, Color.White.copy(alpha = .42f), 36)
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.headlineMedium)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable internal fun WFStatus(value: String) {
    val background = when (value) {
        "Abierta", "Ausencia injustificada" -> Brand.Red.copy(alpha = .13f)
        "Pendiente", "Borrador", "En proceso", "Tardanza", "Bajo mínimo" -> Brand.Amber.copy(alpha = .32f)
        "Completada", "Presente", "Cerrada", "Abastecido" -> Brand.Sage
        else -> Brand.Selected
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(if (value in listOf("Completada", "Presente", "Cerrada", "Abastecido")) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, Modifier.size(13.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = Brand.Petroleum)
        }
    }
}
@Composable internal fun ProgressPanel(done: Int, total: Int) {
    WFCard(Modifier.fillMaxWidth()) {
        Text("$done de $total tareas completadas", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Brand.Teal, trackColor = Brand.Selected)
    }
}
@Composable internal fun ProgressRing(done: Int, total: Int) {
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxSize(), color = Brand.Teal, trackColor = Brand.Selected, strokeWidth = 6.dp, strokeCap = StrokeCap.Round)
        Text("$done/$total", style = MaterialTheme.typography.titleMedium)
    }
}
@Composable internal fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 76.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp, border = BorderStroke(.5.dp, Brand.Border)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(26.dp), tint = Brand.Petroleum)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable internal fun WeekStrip(day: String, choose: (String) -> Unit) {
    val date = LocalDate.parse(day)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = ((maxWidth - 24.dp) / 7).coerceAtLeast(48.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0L..6L).forEach { offset ->
                val d = monday.plusDays(offset); val selected = d == date
                Surface(modifier = Modifier.width(width).heightIn(min = 66.dp).clickable { choose(d.toString()) }, shape = RoundedCornerShape(14.dp), color = if (selected) Brand.Petroleum else Color.White, shadowElevation = if (selected) 0.dp else 2.dp) {
                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("es"))).take(3).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else Brand.Petroleum)
                        Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else Brand.Petroleum)
                    }
                }
            }
        }
    }
}
@Composable internal fun PillTabs(options: List<String>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { label ->
            Surface(Modifier.weight(1f).heightIn(min = 48.dp).clickable { select(label) }, shape = RoundedCornerShape(50), color = if (label == selected) Brand.Selected else Color.Transparent) {
                Box(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (label == selected) Brand.Petroleum else Brand.SecondaryText) }
            }
        }
    }
}
@Composable internal fun TimelineRow(op: Operation, last: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(49.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(op.time.ifBlank { "—" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            Canvas(Modifier.width(16.dp).height(if (last) 16.dp else 72.dp).padding(top = 7.dp)) {
                if (!last) drawLine(Brand.Border, center.copy(y = 0f), center.copy(y = size.height), strokeWidth = 2.dp.toPx())
                drawCircle(if (op.status == "Completada") Brand.Green else Brand.Amber, radius = 5.dp.toPx(), center = center.copy(y = 6.dp.toPx()))
            }
        }
        WFCard(Modifier.weight(1f).clickable(onClick = click)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WFIcon(moduleIcon(op.module), if (op.status == "Completada") Brand.Sage else Brand.Sand, 36)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(op.title, style = MaterialTheme.typography.titleSmall)
                    WFStatus(op.status)
                }
                Icon(Icons.Outlined.ChevronRight, "Abrir tarea", Modifier.size(18.dp), tint = Brand.SecondaryText)
            }
            if (op.notes.isNotBlank()) Text(op.notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable internal fun OperatorAttendanceRow(operator: Operator, operation: Operation?, enabled: Boolean, update: (String, Boolean) -> Unit) {
    WFCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WFIcon(Icons.Outlined.Person, Brand.Selected, 38)
            Column(Modifier.weight(1f)) {
                Text(operator.name, style = MaterialTheme.typography.titleMedium)
                Text("${operator.code} · Habitual: ${operator.shift}", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = operation?.extended == true, enabled = enabled && operation != null, onCheckedChange = { update(operation?.status ?: "Presente", it) })
                Text("EXT", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Rules.statuses.getValue(Module.ATTENDANCE).zip(listOf("P", "T", "AJ", "AI", "L")).forEach { (status, short) ->
                val selected = operation?.status == status
                Surface(modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = enabled) { update(status, operation?.extended == true) }, shape = RoundedCornerShape(11.dp), color = if (selected) Brand.Selected else Brand.Background, border = BorderStroke(if (selected) 1.dp else .5.dp, if (selected) Brand.Teal else Brand.Border)) {
                    Box(contentAlignment = Alignment.Center) { Text(short, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        Text(operation?.status ?: "Pendiente de marcar", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText)
    }
}
