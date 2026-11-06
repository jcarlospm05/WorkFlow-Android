package com.workflow.operaciones.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.R
import com.workflow.operaciones.export.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun BrandHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.workflow_logo), contentDescription = "Logo WorkFlow", modifier = Modifier.size(36.dp))
        Text("WorkFlow", style = MaterialTheme.typography.titleLarge)
    }
}
internal fun moduleIcon(module: Module): ImageVector = when (module) {
    Module.ATTENDANCE -> Icons.Outlined.People
    Module.MEAL -> Icons.Outlined.Restaurant
    Module.FUEL -> Icons.Outlined.LocalGasStation
    Module.FAULT -> Icons.Outlined.Build
    Module.TICKET -> Icons.Outlined.ConfirmationNumber
    Module.PLAN -> Icons.Outlined.CalendarMonth
    Module.TASK -> Icons.Outlined.Checklist
    Module.STOCK -> Icons.Outlined.Inventory2
    Module.POSITION -> Icons.Outlined.Place
    Module.CLOSE -> Icons.Outlined.TaskAlt
}
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    WFCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
@Composable private fun Status(value: String) = WFStatus(value)
@Composable private fun OperationCard(op: Operation, doc: DailyDocument, edit: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().then(if (edit != null) Modifier.clickable(onClick = edit) else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(moduleIcon(op.module), null, tint = Brand.Teal)
                Column(Modifier.weight(1f)) {
                    if (op.module == Module.TICKET) Text("WF-${op.day.replace("-", "")}-${op.id.take(6).uppercase()}", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText)
                    Text(op.title, style = MaterialTheme.typography.titleSmall)
                }
                if (edit != null) Icon(Icons.Outlined.ChevronRight, "Abrir registro", Modifier.size(20.dp))
            }
            val who = doc.operators.firstOrNull { it.id == op.operatorId }?.name
            val asset = doc.assets.firstOrNull { it.id == op.assetId }?.code
            listOfNotNull(who, asset, op.time.takeIf { it.isNotEmpty() }).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
            Status(op.status)
            if (op.module == Module.ATTENDANCE) Text("${op.shift}${if (op.extended) " · EXT" else ""}")
            if (op.module == Module.MEAL) {
                Text("${op.mealType} · ${op.operatorIds.size} personas")
                Text(op.operatorIds.mapNotNull { id -> doc.operators.firstOrNull { it.id == id }?.name }.joinToString(", "))
            }
            if (op.quantity > 0) Text("Cantidad: ${op.quantity}")
            if (op.notes.isNotBlank()) Text(op.notes)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkflowScreen(vm: WorkflowViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var module by remember { mutableStateOf<Module?>(null) }
    var editor by remember { mutableStateOf<Operation?>(null) }
    var master by remember { mutableStateOf<String?>(null) }
    var editOperator by remember { mutableStateOf<Operator?>(null) }
    var editAsset by remember { mutableStateOf<Asset?>(null) }
    var editItem by remember { mutableStateOf<StockItem?>(null) }
    var viewing by remember { mutableStateOf<DailyDocument?>(null) }
    var dateDialog by remember { mutableStateOf(false) }
    var plannerFilter by remember { mutableStateOf("Pendientes") }
    var attendanceShift by remember { mutableStateOf("Mañana") }
    var operatorSearch by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Cena") }
    var mealPreview by remember { mutableStateOf(false) }
    val savedMeal = vm.document?.operations?.firstOrNull { it.module == Module.MEAL && it.mealType == mealType }
    var mealSelected by remember(vm.day, mealType, savedMeal?.updatedAt) { mutableStateOf(savedMeal?.operatorIds?.toSet() ?: emptySet<String>()) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    val screenScroll = remember(tab, module, vm.day) { LazyListState() }
    BackHandler(enabled = tab != 0 || module != null) { if (module != null) module = null else tab = 0 }
    fun newOperation(which: Module) {
        editor = Operation(day = vm.day, module = which, title = which.label, status = Rules.statuses.getValue(which).first())
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BrandHeader() }
                Surface(Modifier.size(46.dp).clickable { tab = 4; module = null }, shape = CircleShape, color = Brand.Selected) {
                    Box(contentAlignment = Alignment.Center) { Text(vm.document?.supervisor?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.map { it.first().uppercaseChar() }?.joinToString("") ?: "WF", style = MaterialTheme.typography.titleSmall) }
                }
            }
        },
        bottomBar = {
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = Color.White, shadowElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val labels = listOf("Hoy", "Agenda", "Operación", "Reportes", "Más")
                    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.CalendarMonth, Icons.Outlined.Engineering, Icons.Outlined.Assessment, Icons.Outlined.MoreHoriz)
                    labels.forEachIndexed { index, label ->
                        Surface(modifier = Modifier.weight(1f).testTag("nav-$index").selectable(selected = tab == index, role = Role.Tab, onClick = { tab = index; module = null }), shape = RoundedCornerShape(18.dp), color = if (tab == index) Brand.Selected else Color.White) {
                            Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(icons[index], null, Modifier.size(24.dp), tint = if (tab == index) Brand.Petroleum else Brand.SecondaryText)
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (tab == index) Brand.Petroleum else Brand.SecondaryText)
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        val doc = vm.document
        if (doc == null) {
            Column(Modifier.padding(insets).padding(20.dp)) { if (vm.busy) CircularProgressIndicator() else Button(onClick = vm::refresh) { Text("Volver a cargar") } }
        } else LazyColumn(
            Modifier.padding(insets).fillMaxSize().clipToBounds(),
            state = screenScroll,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when (tab) {
                0 -> {
                    val tasks = doc.operations.filter { it.module == Module.TASK }
                    val done = tasks.count { it.status == "Completada" }
                    item { WFHeading("Buenos días, ${doc.supervisor.split(" ").firstOrNull() ?: "supervisor"}", doc.day) { dateDialog = true } }
                    item { Row { WFStatus(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Jornada cerrada" else "Jornada en curso") } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("Tu jornada, bajo control", style = MaterialTheme.typography.titleMedium)
                                Text("Completa tus actividades y mantén la operación al día.", color = Brand.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                            }
                            ProgressRing(done, tasks.size)
                        }
                        Button(onClick = { tab = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.Petroleum), modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Continuar jornada")
                        }
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(DailySummary.present(doc).toString(), "Presentes", Icons.Outlined.People, Brand.Sage, Modifier.weight(1f))
                        MetricTile(DailySummary.pending(doc).size.toString(), "Pendientes", Icons.Outlined.Checklist, Brand.Sky, Modifier.weight(1f))
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(doc.assets.count { it.state == "Activo" }.toString(), "Equipos activos", Icons.Outlined.LocalShipping, Brand.Sky, Modifier.weight(1f))
                        MetricTile(doc.items.count { (doc.stockBalances[it.id] ?: 0.0) < it.minimum }.toString(), "Bajo mínimo", Icons.Outlined.Inventory2, Brand.Sand, Modifier.weight(1f))
                    } }
                    item { Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Module.ATTENDANCE, Module.STOCK, Module.MEAL, Module.FUEL).forEach { entry -> QuickAction(moduleIcon(entry), entry.label, Modifier.weight(1f)) { tab = 2; module = entry } }
                    } }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Próximas tareas", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { tab = 1 }) { Text("Ver agenda") } } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        val upcoming = tasks.filter { it.status != "Completada" }.sortedBy { it.time }.take(3)
                        if (upcoming.isEmpty()) Text("No hay tareas pendientes.", color = Brand.SecondaryText)
                        upcoming.forEachIndexed { index, op ->
                            Row(Modifier.fillMaxWidth().clickable { editor = op }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(color = Brand.Selected, shape = RoundedCornerShape(50)) { Text(op.time.ifBlank { "—" }, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium) }
                                Text(op.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp))
                            }
                            if (index < upcoming.lastIndex) HorizontalDivider(color = Brand.Border)
                        }
                    } }
                    if (vm.operators.isEmpty()) item { Section("Prepara tu libreta") { Text("Añade personal y equipos para empezar.", color = Brand.SecondaryText); TextButton(onClick = { tab = 4 }) { Text("Configurar catálogos") } } }
                }
                1 -> {
                    val tasks = doc.operations.filter { it.module == Module.TASK }
                    val done = tasks.count { it.status == "Completada" }
                    val agenda = doc.operations.filter { it.module in listOf(Module.TASK, Module.PLAN) }.filter { plannerFilter == "Todas" || (plannerFilter == "Completadas" && it.status == "Completada") || (plannerFilter == "Pendientes" && it.status != "Completada") }.sortedBy { it.time }
                    item { WFHeading("Mi jornada", doc.day) { dateDialog = true } }
                    item { WeekStrip(doc.day, vm::chooseDay) }
                    item { ProgressPanel(done, tasks.size) }
                    item { PillTabs(listOf("Todas", "Pendientes", "Completadas"), plannerFilter) { plannerFilter = it } }
                    if (agenda.isEmpty()) item { Text("No hay tareas en esta vista.", color = Brand.SecondaryText) }
                    items(agenda.size, key = { agenda[it].id }) { index -> TimelineRow(agenda[index], index == agenda.lastIndex) { editor = agenda[index] } }
                    if (plannerFilter != "Completadas" && done > 0) item { Surface(Modifier.fillMaxWidth().clickable { plannerFilter = "Completadas" }, shape = RoundedCornerShape(16.dp), color = Brand.Sage) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Green); Text("Ver $done tareas completadas", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) } } }
                    item { Button(onClick = { newOperation(Module.TASK) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir tarea") } }
                    item { TextButton(onClick = { newOperation(Module.CLOSE) }, modifier = Modifier.fillMaxWidth()) { Text(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Reabrir jornada" else "Revisar cierre del día") } }
                    items(doc.operations.filter { it.module == Module.CLOSE }) { op -> OperationCard(op, doc) { editor = op } }
                }
                2 -> {
                    item { WFHeading(module?.label ?: "Operación", doc.day) { dateDialog = true } }
                    if (module == null) {
                        item { Text("Todo lo que necesitas para la operación del día.", color = Brand.SecondaryText) }
                        items(Module.entries.filter { it !in listOf(Module.CLOSE, Module.TASK) }.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { entry ->
                                WFCard(Modifier.weight(1f).testTag("module-${entry.name}").clickable { module = entry }.heightIn(min = 142.dp)) {
                                    WFIcon(moduleIcon(entry), if (entry in listOf(Module.FAULT, Module.TICKET)) Brand.Sand else Brand.Selected)
                                    Text(entry.label, style = MaterialTheme.typography.titleSmall)
                                    Text("${doc.operations.count { it.module == entry }} registros", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText)
                                }
                            }; if (row.size == 1) Spacer(Modifier.weight(1f)) }
                        }
                    } else {
                        val selectedModule = module!!
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { module = null }) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Módulos") }
                            if (selectedModule != Module.MEAL) Button(onClick = { newOperation(selectedModule) }, enabled = !vm.busy) { Icon(Icons.Outlined.Add, null); Text(if (selectedModule == Module.ATTENDANCE) "Detalle" else "Registrar") }
                        } }
                        when (selectedModule) {
                            Module.ATTENDANCE -> {
                                val attendance = doc.operations.filter { it.module == Module.ATTENDANCE && it.shift == attendanceShift }
                                val operators = vm.operators.filter { it.active && (operatorSearch.isBlank() || it.name.contains(operatorSearch, true) || it.code.contains(operatorSearch, true)) }
                                item { PillTabs(Rules.shifts, attendanceShift) { attendanceShift = it } }
                                item { OutlinedTextField(operatorSearch, { operatorSearch = it }, label = { Text("Buscar operador o ficha") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricTile(attendance.count { it.status in listOf("Presente", "Tardanza") }.toString(), "Presentes", Icons.Outlined.People, Brand.Sage, Modifier.weight(1f))
                                    MetricTile(attendance.count { it.status == "Tardanza" }.toString(), "Tardanzas", Icons.Outlined.Schedule, Brand.Sand, Modifier.weight(1f))
                                } }
                                item { Text("P: presente · T: tardanza · AJ/AI: ausencia · L: libre", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText) }
                                if (operators.isEmpty()) item { Text("Añade operadores en Más → Personal.", color = Brand.SecondaryText) }
                                items(operators, key = { it.id }) { operator ->
                                    val op = attendance.firstOrNull { it.operatorId == operator.id }
                                    OperatorAttendanceRow(operator, op, !vm.busy) { status, extended ->
                                        vm.save((op ?: Operation(day = doc.day, module = Module.ATTENDANCE, title = "Asistencia", status = status, operatorId = operator.id, shift = attendanceShift)).copy(status = status, extended = extended)) { }
                                    }
                                }
                            }
                            Module.MEAL -> {
                                item { PillTabs(listOf("Cena", "Almuerzo"), mealType) { mealType = it } }
                                item { WFCard(Modifier.fillMaxWidth(), Brand.Sand) {
                                    Text("${mealSelected.size} seleccionados", style = MaterialTheme.typography.titleLarge)
                                    Text("Selecciona los operadores para la solicitud. La comida es independiente de la asistencia.", style = MaterialTheme.typography.bodyMedium)
                                } }
                                items(vm.operators.filter { it.active || it.id in mealSelected }, key = { it.id }) { operator ->
                                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp) {
                                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Checkbox(operator.id in mealSelected, { checked -> mealSelected = if (checked) mealSelected + operator.id else mealSelected - operator.id })
                                            Column(Modifier.weight(1f)) { Text(operator.name, style = MaterialTheme.typography.titleSmall); Text(operator.code, style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText) }
                                            Text(operator.shift, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                item { WFCard(Modifier.fillMaxWidth()) { Text("$mealType para ${mealSelected.size} personas", style = MaterialTheme.typography.titleMedium)
                                    OutlinedButton(onClick = { mealPreview = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(8.dp)); Text("Vista previa de lista") }
                                    Button(onClick = { vm.save((savedMeal ?: Operation(day = doc.day, module = Module.MEAL, title = "Solicitud de ${mealType.lowercase()}", status = "Solicitada", mealType = mealType)).copy(operatorIds = mealSelected.toList().sorted())) { } }, enabled = mealSelected.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Guardar solicitud") }
                                } }
                            }
                            Module.STOCK -> {
                                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(doc.items.size.toString(), "Artículos", Icons.Outlined.Inventory2, Brand.Sky, Modifier.weight(1f)); MetricTile(doc.items.count { (doc.stockBalances[it.id] ?: 0.0) < it.minimum }.toString(), "Bajo mínimo", Icons.Outlined.WarningAmber, Brand.Sand, Modifier.weight(1f)) } }
                                items(doc.items, key = { it.id }) { item -> WFCard(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.Inventory2, Brand.Sand); Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text("${doc.stockBalances[item.id] ?: 0.0} ${item.unit}", style = MaterialTheme.typography.titleSmall); Text("Mínimo: ${item.minimum}", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText) } }
                                    if ((doc.stockBalances[item.id] ?: 0.0) < item.minimum) WFStatus("Bajo mínimo")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf("Entrada", "Salida").forEach { status ->
                                            OutlinedButton(onClick = { editor = Operation(day = doc.day, module = Module.STOCK, title = "$status · ${item.name}", status = status, itemId = item.id) }, modifier = Modifier.weight(1f)) {
                                                Icon(if (status == "Entrada") Icons.Outlined.South else Icons.Outlined.North, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text(status)
                                            }
                                        }
                                    }
                                } }
                                item { Text("Movimientos del día", style = MaterialTheme.typography.titleMedium) }
                                items(doc.operations.filter { it.module == Module.STOCK }, key = { it.id }) { op -> OperationCard(op, doc) { editor = op } }
                            }
                            Module.FUEL -> {
                                val fuel = doc.operations.filter { it.module == Module.FUEL }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(fuel.count { it.status == "Abastecido" }.toString(), "Abastecidos", Icons.Outlined.CheckCircle, Brand.Sage, Modifier.weight(1f)); MetricTile(fuel.count { it.status == "Pendiente" }.toString(), "Pendientes", Icons.Outlined.Schedule, Brand.Sand, Modifier.weight(1f)) } }
                                items(doc.assets, key = { it.id }) { asset ->
                                    val op = fuel.lastOrNull { it.assetId == asset.id }
                                    WFCard(Modifier.fillMaxWidth().clickable { editor = op ?: Operation(day = doc.day, module = Module.FUEL, title = "Combustible · ${asset.code}", status = "Pendiente", assetId = asset.id) }) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.LocalGasStation); Column(Modifier.weight(1f)) { Text(asset.code, style = MaterialTheme.typography.titleMedium); Text("${asset.type} · ${asset.state}", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText) }; Icon(Icons.Outlined.ChevronRight, null) }
                                        WFStatus(op?.status ?: if (asset.state == "En taller") "En taller" else "Sin requerimiento")
                                        if (op != null && op.quantity > 0) Text("${op.quantity} litros · ${op.time}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            else -> {
                                val rows = doc.operations.filter { it.module == selectedModule }
                                if (selectedModule == Module.PLAN) item { WeekStrip(doc.day, vm::chooseDay) }
                                if (selectedModule == Module.FAULT) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(rows.count { it.status == "Abierta" }.toString(), "Abiertas", Icons.Outlined.Build, Brand.Sand, Modifier.weight(1f)); MetricTile(rows.count { it.status == "Cerrada" }.toString(), "Resueltas", Icons.Outlined.CheckCircle, Brand.Sage, Modifier.weight(1f)) } }
                                if (selectedModule == Module.TICKET) item { WFCard(Modifier.fillMaxWidth(), Brand.Sand) { Text("${rows.count { it.status == "Emitido" }} tickets emitidos", style = MaterialTheme.typography.titleMedium); Text("Registro formal y seguimiento por operador.", style = MaterialTheme.typography.bodyMedium) } }
                                if (rows.isEmpty()) item { Text("Sin registros para esta fecha. Pulsa Registrar para añadir uno.", color = Brand.SecondaryText) }
                                items(rows, key = { it.id }) { op -> OperationCard(op, doc) { editor = op } }
                            }
                        }
                    }
                }
                3 -> {
                    item { WFHeading("Reportes", doc.day) { dateDialog = true } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.Assessment); Column(Modifier.weight(1f)) { Text("Cómo va el día", style = MaterialTheme.typography.titleMedium); Text("${doc.supervisor} · Versión ${doc.revision}", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("${DailySummary.present(doc)} presentes", style = MaterialTheme.typography.titleSmall); Text("${DailySummary.pending(doc).size} pendientes", style = MaterialTheme.typography.titleSmall) }
                        WFStatus("JSON actualizado")
                        TextButton(onClick = { viewing = doc }) { Text("Ver informe completo"); Icon(Icons.Outlined.ChevronRight, null) }
                    } }
                    item { WFCard(Modifier.fillMaxWidth()) { Text("Compartir el progreso", style = MaterialTheme.typography.titleMedium); Text("JSON para abrir en otro WorkFlow. PDF para leer o imprimir.", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText)
                        Button(onClick = { vm.shareRequest = doc }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir JSON / PDF") }
                        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Recibir archivo JSON") }
                    } }
                    item { Text("Informes recibidos", style = MaterialTheme.typography.titleMedium) }
                    if (vm.received.isEmpty()) item { WFCard(Modifier.fillMaxWidth()) { WFIcon(Icons.Outlined.FolderOpen, Brand.Sky); Text("Tus informes recibidos aparecerán aquí.", style = MaterialTheme.typography.titleSmall); Text("Abre un JSON enviado por el supervisor para consultar su última actualización.", color = Brand.SecondaryText, style = MaterialTheme.typography.bodyMedium) } }
                    items(vm.received, key = { "${it.originId}|${it.day}" }) { incoming ->
                        Section("${incoming.supervisor} · ${incoming.day}") { Text("Versión ${incoming.revision}", color = Brand.SecondaryText); Text("${DailySummary.present(incoming)} presentes · ${DailySummary.pending(incoming).size} pendientes"); TextButton(onClick = { viewing = incoming }) { Text("Ver informe recibido") } }
                    }
                }
                4 -> {
                    item { Text("Configuración y catálogos", style = MaterialTheme.typography.titleLarge) }
                    item { Section("Responsable") {
                        var name by remember(doc.supervisor) { mutableStateOf(doc.supervisor) }
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del supervisor") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { vm.supervisor(name) }, enabled = !vm.busy) { Text("Guardar responsable") }
                        Text("Origen del teléfono: ${vm.repository.origin}", style = MaterialTheme.typography.bodyMedium)
                    } }
                    item { Section("Compartir al guardar") {
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = vm.shareAfterSave, onCheckedChange = { vm.shareAfterSave = it }); Text("Ofrecer compartir tras registrar cambios", modifier = Modifier.weight(1f)) }
                        Text("El JSON siempre se actualiza. WhatsApp requiere elegir destinatario y confirmar el envío; no hay sincronización automática.")
                    } }
                    item { Section("Personal · ${vm.operators.size}") { Button(onClick = { master = "operator" }) { Text("Añadir operador") }; vm.operators.forEach { op -> TextButton(onClick = { editOperator = op; master = "operator" }) { Text("${op.code} · ${op.name} · ${if (op.active) op.shift else "Inactivo"}") } } } }
                    item { Section("Equipos · ${vm.assets.size}") { Button(onClick = { master = "asset" }) { Text("Añadir equipo") }; vm.assets.forEach { asset -> TextButton(onClick = { editAsset = asset; master = "asset" }) { Text("${asset.code} · ${asset.type} · ${asset.state}") } } } }
                    item { Section("Artículos · ${vm.items.size}") { Button(onClick = { master = "item" }) { Text("Añadir artículo") }; vm.items.forEach { item -> TextButton(onClick = { editItem = item; master = "item" }) { Text("${item.name} · ${item.unit}") } } } }
                    item { Section("Historial local") { vm.history.forEach { past -> TextButton(onClick = { vm.chooseDay(past.day); tab = 0 }) { Text("${past.day} · Revisión ${past.revision}") } } } }
                    item { Text("Datos privados en SQLite. Bloqueo con Android. Los JSON y PDF compartidos contienen información personal sin cifrado de archivo. Esta versión no restaura respaldos antiguos de ControlOPS ni los datos recibidos sobre los propios.", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    if (mealPreview) SafeAlertDialog(scrollText = false, onDismissRequest = { mealPreview = false }, title = { Text("$mealType · ${mealSelected.size} personas") }, text = { LazyColumn { items(vm.operators.filter { it.id in mealSelected }) { Text("${it.code} · ${it.name}", Modifier.padding(vertical = 6.dp)) } } }, confirmButton = { TextButton(onClick = { mealPreview = false }) { Text("Cerrar") } })
    if (dateDialog) {
        var date by remember { mutableStateOf(vm.day) }
        SafeAlertDialog(onDismissRequest = { dateDialog = false }, title = { Text("Fecha de trabajo") }, text = { OutlinedTextField(date, { date = it }, label = { Text("AAAA-MM-DD") }) }, confirmButton = { TextButton(onClick = { vm.chooseDay(date); dateDialog = false }) { Text("Ver fecha") } }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Cancelar") } })
    }
    editor?.let { op -> OperationEditor(op, vm, onDismiss = { editor = null }) }
    master?.let { kind -> MasterEditor(kind, vm, onDismiss = { master = null; editOperator = null; editAsset = null; editItem = null }, operator = editOperator, asset = editAsset, item = editItem) }
    vm.preview?.let { incoming ->
        SafeAlertDialog(onDismissRequest = vm::dismissPreview, title = { Text("Revisar informe recibido") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${incoming.supervisor}\n${incoming.day} · Revisión ${incoming.revision}\n${incoming.operations.size} registros\nOrigen: ${incoming.originId}")
                Text("Archivo íntegro, remitente no autenticado. Confirma con quién te lo envió. No reemplaza tus datos; actualiza solo este origen y fecha.")
            }
        }, confirmButton = { TextButton(onClick = vm::acceptPreview, enabled = !vm.busy) { Text("Importar informe") } }, dismissButton = { TextButton(onClick = vm::dismissPreview) { Text("Cancelar") } })
    }
    viewing?.let { report ->
        ReportViewer(report, onDismiss = { viewing = null }, share = { vm.shareRequest = report })
    }
    vm.shareRequest?.let { report ->
        SafeAlertDialog(onDismissRequest = { vm.shareRequest = null }, title = { Text("Compartir informe") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${report.day} · Revisión ${report.revision}. Contiene datos personales; revisa el destinatario.")
                listOf("JSON por WhatsApp" to true, "PDF por WhatsApp" to false, "JSON por otra app" to true, "PDF por otra app" to false).forEachIndexed { index, (label, isJson) ->
                    OutlinedButton(onClick = {
                        vm.shareRequest = null
                        vm.action {
                            val file = withContext(Dispatchers.IO) { if (isJson) Reports.json(context, report) else Reports.pdf(context, report) }
                            Reports.share(context, file, whatsapp = index < 2)
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Text(label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.shareRequest = null }) { Text("Ahora no") } })
    }
}
