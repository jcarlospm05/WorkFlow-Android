package com.workflow.operaciones.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
private fun moduleIcon(module: Module): ImageVector = when (module) {
    Module.ATTENDANCE -> Icons.Rounded.People
    Module.MEAL -> Icons.Rounded.Restaurant
    Module.FUEL -> Icons.Rounded.LocalGasStation
    Module.FAULT -> Icons.Rounded.Build
    Module.TICKET -> Icons.Rounded.ConfirmationNumber
    Module.PLAN -> Icons.Rounded.CalendarMonth
    Module.TASK -> Icons.Rounded.Checklist
    Module.STOCK -> Icons.Rounded.Inventory2
    Module.POSITION -> Icons.Rounded.Place
    Module.CLOSE -> Icons.Rounded.TaskAlt
}
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable private fun Status(value: String) {
    val color = when (value) {
        "Abierta", "Ausencia injustificada" -> Brand.Red
        "Pendiente", "Borrador", "En proceso", "Tardanza" -> Brand.Amber
        "Completada", "Presente", "Cerrada", "Abastecido" -> Brand.Green
        else -> Brand.Blue
    }
    Surface(color = color.copy(alpha = .17f), shape = RoundedCornerShape(50)) {
        Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Brand.Petroleum, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun OperationCard(op: Operation, doc: DailyDocument, edit: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().then(if (edit != null) Modifier.clickable(onClick = edit) else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(moduleIcon(op.module), null, tint = Brand.Teal)
                Text(op.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (edit != null) Icon(Icons.Rounded.Edit, "Editar")
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
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    fun newOperation(which: Module) {
        editor = Operation(day = vm.day, module = which, title = which.label, status = Rules.statuses.getValue(which).first())
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                BrandHeader()
                TextButton(onClick = { dateDialog = true }, enabled = !vm.busy) {
                    Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(vm.day + if (vm.day == LocalDate.now().toString()) " · Hoy" else " · Cambiar fecha")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val labels = listOf("Hoy", "Agenda", "Operación", "Reportes", "Más")
                val icons = listOf(Icons.Rounded.Home, Icons.Rounded.EventNote, Icons.Rounded.Warehouse, Icons.Rounded.Assessment, Icons.Rounded.MoreHoriz)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index; module = null }, icon = { Icon(icons[index], label) }, label = { Text(label, fontSize = 11.sp, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Brand.Selected)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        val doc = vm.document
        if (doc == null) {
            Column(Modifier.padding(insets).padding(20.dp)) { if (vm.busy) CircularProgressIndicator() else Button(onClick = vm::refresh) { Text("Volver a cargar") } }
        } else LazyColumn(
            Modifier.padding(insets).consumeWindowInsets(insets).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when (tab) {
                0 -> {
                    item { Text("Tu jornada, bajo control", style = MaterialTheme.typography.titleLarge) }
                    item {
                        Section("${doc.supervisor} · Estado del día") {
                            Text("${DailySummary.present(doc)} presentes únicos · ${DailySummary.pending(doc).size} pendientes")
                            Text("${doc.operations.count { it.module == Module.FAULT && it.status != "Cerrada" }} averías activas · ${doc.operations.count { it.module == Module.TICKET && it.status == "Emitido" }} tickets emitidos")
                            Text("JSON actualizado · Revisión ${doc.revision}", color = Brand.SecondaryText)
                            Button(onClick = { tab = 1 }) { Text(if (doc.operations.isEmpty()) "Iniciar jornada" else "Continuar jornada") }
                        }
                    }
                    if (vm.operators.isEmpty()) item { Section("Prepara tu libreta") { Text("Añade personal y equipos para empezar a registrar la operación."); Button(onClick = { tab = 4 }) { Text("Configurar catálogos") } } }
                    item { Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium) }
                    items(listOf(Module.ATTENDANCE, Module.STOCK, Module.MEAL, Module.FUEL).chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { entry ->
                            OutlinedButton(onClick = { tab = 2; module = entry }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                                Icon(moduleIcon(entry), null); Spacer(Modifier.width(6.dp)); Text(entry.label)
                            }
                        } }
                    }
                    item { Text("Próximos pendientes", style = MaterialTheme.typography.titleMedium) }
                    if (DailySummary.pending(doc).isEmpty()) item { Text("Sin pendientes registrados. Los operadores sin marcar no se consideran ausentes.") }
                    items(DailySummary.pending(doc).take(6), key = { it.id }) { op -> OperationCard(op, doc) { editor = op } }
                }
                1 -> {
                    item { Text("Mi jornada", style = MaterialTheme.typography.titleLarge) }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val date = LocalDate.parse(vm.day)
                            val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                            (0L..6L).forEach { offset ->
                                val d = monday.plusDays(offset)
                                FilterChip(selected = d.toString() == vm.day, onClick = { vm.chooseDay(d.toString()) }, label = { Text(d.format(DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es")))) })
                            }
                        }
                    }
                    item { Section("Guía del día") {
                        Text("Asistencia de mañana y tarde · Cena antes del mediodía · Combustible si corresponde · Revisión de averías y tickets")
                        if (LocalDate.parse(vm.day).dayOfWeek.value == 5) Text("Viernes: programa el fin de semana y solicita los almuerzos por cada fecha objetivo.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { newOperation(Module.TASK) }) { Text("Añadir tarea") }; OutlinedButton(onClick = { newOperation(Module.PLAN) }) { Text("Planificar") } }
                    } }
                    items(doc.operations.filter { it.module == Module.TASK || it.module == Module.PLAN }.sortedBy { it.time }, key = { it.id }) { op -> OperationCard(op, doc) { editor = op } }
                    item { OutlinedButton(onClick = { newOperation(Module.CLOSE) }, modifier = Modifier.fillMaxWidth()) { Text(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Reabrir jornada" else "Revisar cierre del día") } }
                    items(doc.operations.filter { it.module == Module.CLOSE }) { op -> OperationCard(op, doc) { editor = op } }
                }
                2 -> {
                    item { Text(module?.label ?: "Operación", style = MaterialTheme.typography.titleLarge) }
                    if (module == null) {
                        items(Module.entries.filter { it !in listOf(Module.CLOSE, Module.TASK) }.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { entry ->
                                OutlinedButton(onClick = { module = entry }, modifier = Modifier.weight(1f).heightIn(min = 80.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(moduleIcon(entry), null); Text(entry.label) } }
                            } }
                        }
                    } else {
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = { module = null }) { Text("Módulos") }; Button(onClick = { module?.let(::newOperation) }, enabled = !vm.busy) { Icon(Icons.Rounded.Add, null); Text("Registrar") } } }
                        if (module == Module.STOCK) {
                            items(doc.items) { item -> Section(item.name) {
                                val balance = doc.stockBalances[item.id] ?: 0.0
                                Text("$balance ${item.unit} · Mínimo ${item.minimum}")
                                if (balance < item.minimum) Status("Bajo mínimo")
                            } }
                        }
                        val rows = doc.operations.filter { it.module == module }
                        if (rows.isEmpty()) item { Text("Sin registros para esta fecha. Pulsa Registrar para añadir uno.") }
                        items(rows, key = { it.id }) { op -> OperationCard(op, doc) { editor = op } }
                    }
                }
                3 -> {
                    item { Text("Reportes y comunicación", style = MaterialTheme.typography.titleLarge) }
                    item { Section("Cómo va el día") {
                        Text("Informe del ${doc.day} · Revisión ${doc.revision}")
                        Text("Cada guardado actualiza el JSON privado. Comparte la última revisión para que el otro teléfono pueda importarla.")
                        Button(onClick = { viewing = doc }) { Text("Ver estatus del día") }
                        Button(onClick = { vm.shareRequest = doc }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir JSON / PDF") }
                        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Recibir archivo JSON") }
                    } }
                    item { Text("Informes recibidos", style = MaterialTheme.typography.titleMedium) }
                    if (vm.received.isEmpty()) item { Text("Abre un JSON desde WhatsApp o usa Recibir archivo JSON. Los informes recibidos son de solo lectura.") }
                    items(vm.received, key = { "${it.originId}|${it.day}" }) { incoming ->
                        Section("${incoming.supervisor} · ${incoming.day}") {
                            Text("Revisión ${incoming.revision} · Origen ${incoming.originId.take(8)}")
                            Text("${DailySummary.present(incoming)} presentes · ${DailySummary.pending(incoming).size} pendientes")
                            Button(onClick = { viewing = incoming }) { Text("Ver informe recibido") }
                        }
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
    if (dateDialog) {
        var date by remember { mutableStateOf(vm.day) }
        AlertDialog(onDismissRequest = { dateDialog = false }, title = { Text("Fecha de trabajo") }, text = { OutlinedTextField(date, { date = it }, label = { Text("AAAA-MM-DD") }) }, confirmButton = { TextButton(onClick = { vm.chooseDay(date); dateDialog = false }) { Text("Ver fecha") } }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Cancelar") } })
    }
    editor?.let { op -> OperationEditor(op, vm, onDismiss = { editor = null }) }
    master?.let { kind -> MasterEditor(kind, vm, onDismiss = { master = null; editOperator = null; editAsset = null; editItem = null }, operator = editOperator, asset = editAsset, item = editItem) }
    vm.preview?.let { incoming ->
        AlertDialog(onDismissRequest = vm::dismissPreview, title = { Text("Revisar informe recibido") }, text = {
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
        AlertDialog(onDismissRequest = { vm.shareRequest = null }, title = { Text("Compartir informe") }, text = {
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
