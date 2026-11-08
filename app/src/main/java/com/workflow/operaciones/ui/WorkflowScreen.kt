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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
@Composable private fun OperationCard(op: Operation, doc: DailyDocument, report: ((Operation) -> Unit)? = null, edit: (() -> Unit)? = null) {
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
            report?.let { action -> TextButton(onClick = { action(op) }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text(if (op.module == Module.TICKET) "Ver y compartir ticket" else "Ver y compartir registro") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkflowScreen(vm: WorkflowViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var module by rememberSaveable { mutableStateOf<Module?>(null) }
    var editor by rememberSaveable(stateSaver = formSaver<Operation>()) { mutableStateOf<Operation?>(null) }
    var master by rememberSaveable { mutableStateOf<String?>(null) }
    var editOperator by rememberSaveable(stateSaver = formSaver<Operator>()) { mutableStateOf<Operator?>(null) }
    var editAsset by rememberSaveable(stateSaver = formSaver<Asset>()) { mutableStateOf<Asset?>(null) }
    var editItem by rememberSaveable(stateSaver = formSaver<StockItem>()) { mutableStateOf<StockItem?>(null) }
    var formVisible by rememberSaveable { mutableStateOf(false) }
    var formTab by rememberSaveable { mutableIntStateOf(0) }
    var formModule by rememberSaveable { mutableStateOf<Module?>(null) }
    var formCatalog by rememberSaveable { mutableStateOf<String?>(null) }
    var formDay by rememberSaveable { mutableStateOf(vm.day) }
    var masterDraftKey by rememberSaveable { mutableStateOf("") }
    val formState = rememberSaveableStateHolder()
    var startDay by rememberSaveable { mutableStateOf(false) }
    var alarmSettings by rememberSaveable { mutableStateOf(false) }
    val unfinishedDays=DayControl.blockers(vm.history,vm.day)
    var reportOptions by rememberSaveable(stateSaver = formSaver<ReportQuery>()) { mutableStateOf<ReportQuery?>(null) }
    var visualReport by rememberSaveable(stateSaver = formSaver<ReportQuery>()) { mutableStateOf<ReportQuery?>(null) }
    val reportDocuments = (vm.history + listOfNotNull(vm.document)).associateBy { it.day }.values.toList()
    var catalog by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogSearch by rememberSaveable { mutableStateOf("") }
    var pendingOverview by rememberSaveable { mutableStateOf(false) }
    var onlyLowStock by rememberSaveable { mutableStateOf(false) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var plannerFilter by rememberSaveable { mutableStateOf("Pendientes") }
    var attendanceShift by rememberSaveable { mutableStateOf("Mañana") }
    var operatorSearch by rememberSaveable { mutableStateOf("") }
    var mealType by rememberSaveable { mutableStateOf("Cena") }
    var mealPreview by rememberSaveable { mutableStateOf(false) }
    val savedMeal = vm.document?.operations?.firstOrNull { it.module == Module.MEAL && it.mealType == mealType }
    var mealSelected by rememberSaveable(vm.day, mealType, savedMeal?.updatedAt) { mutableStateOf(savedMeal?.operatorIds?.toSet() ?: emptySet<String>()) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    LaunchedEffect(vm.loadedDayEvent) { if (vm.loadedDayEvent > 0) { tab = 0; module = null; catalog = null; formVisible = false } }
    LaunchedEffect(vm.agendaNavigationEvent) { if(vm.agendaNavigationEvent>0){tab=1;module=null;catalog=null;formVisible=false;pendingOverview=false} }
    val screenScroll = rememberSaveable(tab, module, catalog, pendingOverview, vm.day, saver = LazyListState.Saver) { LazyListState() }
    BackHandler(enabled = formVisible || tab != 0 || module != null || catalog != null || pendingOverview) {
        when { formVisible -> formVisible = false; catalog != null -> catalog = null; module != null -> module = null; pendingOverview -> pendingOverview = false; else -> tab = 0 }
    }
    fun openCatalog(kind: String) { tab = 4; module = null; catalog = kind; catalogSearch = ""; pendingOverview = false }
    fun openModule(which: Module) {
        catalog = null; pendingOverview = false
        if (which == Module.TASK) { tab = 1; module = null; plannerFilter = "Pendientes" }
        else { tab = 2; module = which }
    }
    fun captureFormRoute() { formTab = tab; formModule = module; formCatalog = catalog; formDay = vm.day; formVisible = true }
    fun openOperation(op: Operation) { captureFormRoute(); editor = op; master = null }
    fun openEvent(op: Operation) { visualReport = ReportQuery(if (op.module == Module.TICKET) ReportType.TICKET else ReportType.EVENT, op.day, operationId = op.id) }
    fun newMaster(kind: String) { captureFormRoute(); editOperator = null; editAsset = null; editItem = null; master = kind; masterDraftKey = java.util.UUID.randomUUID().toString() }
    fun newOperation(which: Module) {
        openOperation(Operation(day = vm.day, module = which, title = if (which == Module.CLOSE) "Cierre del día" else which.label, status = Rules.statuses.getValue(which).first()))
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BrandHeader() }
                Surface(Modifier.size(46.dp).clickable { formVisible = false; tab = 4; module = null; catalog = null; pendingOverview = false }, shape = CircleShape, color = Brand.Selected) {
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
                        Surface(modifier = Modifier.weight(1f).testTag("nav-$index").selectable(selected = tab == index, role = Role.Tab, onClick = { formVisible = false; tab = index; module = null; catalog = null; pendingOverview = false }), shape = RoundedCornerShape(18.dp), color = if (tab == index) Brand.Selected else Color.White) {
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
        if (formVisible && (editor != null || master != null)) {
            Box(Modifier.padding(insets).fillMaxSize()) {
                CompositionLocalProvider(LocalInlineEditor provides true) {
                    if (master != null) {
                        formState.SaveableStateProvider("master:$masterDraftKey") {
                            MasterEditor(master!!, vm, onDismiss = { formState.removeState("master:$masterDraftKey"); master = null; editOperator = null; editAsset = null; editItem = null; formVisible = editor != null }, operator = editOperator, asset = editAsset, item = editItem)
                        }
                    } else editor?.let { op -> formState.SaveableStateProvider("operation:${op.id}:${op.updatedAt}") {
                        OperationEditor(op, vm, addMaster = ::newMaster, onDismiss = { formState.removeState("operation:${op.id}:${op.updatedAt}"); editor = null; formVisible = false })
                    } }
                }
            }
        } else if (doc == null) {
            Column(Modifier.padding(insets).padding(20.dp)) { if (vm.busy) CircularProgressIndicator() else Button(onClick = vm::refresh) { Text("Volver a cargar") } }
        } else LazyColumn(
            Modifier.padding(insets).fillMaxSize().clipToBounds().testTag("workflow-list"),
            state = screenScroll,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if(doc.day!=LocalDate.now().toString())item {
                TextButton(onClick={vm.chooseDay(LocalDate.now().toString())},modifier=Modifier.testTag("back-to-today")){Text("Viendo ${doc.day} · Volver a hoy")}
            }
            if(unfinishedDays.isNotEmpty())item {
                WFCard(Modifier.fillMaxWidth(),Brand.Sand) {
                    Text("Hay jornadas anteriores pendientes",style=MaterialTheme.typography.titleSmall)
                    unfinishedDays.forEach { previous ->
                        Text("${previous.day} · ${previous.tasks.size} tareas pendientes · ${if(previous.closed) "Día cerrado" else "Falta cerrar el día"}")
                        TextButton(onClick={vm.openAgendaDay(previous.day)}){Text("Resolver jornada del ${previous.day}")}
                    }
                    Text("Puedes consultar y preparar fechas futuras. Para iniciar sus operaciones debes terminar las jornadas anteriores.",style=MaterialTheme.typography.bodyMedium)
                }
            }
            if (editor != null || master != null) item {
                WFCard(Modifier.fillMaxWidth(), Brand.Sand) {
                    Text("Tienes un formulario sin terminar", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { tab = formTab; module = formModule; catalog = formCatalog; if (vm.day != formDay) vm.chooseDay(formDay); formVisible = true }, modifier = Modifier.testTag("resume-draft")) { Text("Continuar formulario") }
                }
            }
            when (tab) {
                0 -> {
                    val tasks = doc.operations.filter { it.module == Module.TASK }
                    val done = tasks.count { it.status == "Completada" }
                    item { WFHeading("Buenos días, ${doc.supervisor.split(" ").firstOrNull() ?: "supervisor"}", doc.day) { dateDialog = true } }
                    item { Row { WFStatus(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Jornada cerrada" else if (!DayControl.started(doc)) "Jornada sin iniciar" else "Jornada en curso") } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("Tu jornada, bajo control", style = MaterialTheme.typography.titleMedium)
                                Text(if (tasks.isEmpty()) "Añade tus actividades y prepara la jornada." else "Completa tus actividades y mantén la operación al día.", color = Brand.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                            }
                            ProgressRing(done, tasks.size)
                        }
                        Button(onClick = { if (!DayControl.started(doc)) { vm.clearActionError(); startDay = true } else tab = 1 }, enabled=!vm.busy&&unfinishedDays.isEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.Petroleum), modifier = Modifier.heightIn(min = 48.dp).testTag("start-or-continue-day"), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (!DayControl.started(doc)) "Iniciar jornada" else "Continuar jornada")
                        }
                    } }
                    item { TextButton(onClick={alarmSettings=true},modifier=Modifier.testTag("alarm-settings")){Text("Alarmas de agenda y cierre · 7:00 p. m.")} }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(DailySummary.present(doc).toString(), "Presentes", Icons.Outlined.People, Brand.Sage, Modifier.weight(1f).testTag("dashboard-attendance"), onClick = { openModule(Module.ATTENDANCE) })
                        MetricTile(DailySummary.pending(doc).size.toString(), "Pendientes", Icons.Outlined.Checklist, Brand.Sky, Modifier.weight(1f).testTag("dashboard-pending"), onClick = { tab = 2; module = null; catalog = null; pendingOverview = true })
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(doc.assets.count { it.state == "Activo" }.toString(), "Equipos activos", Icons.Outlined.LocalShipping, Brand.Sky, Modifier.weight(1f).testTag("dashboard-assets"), onClick = { openCatalog("asset") })
                        MetricTile(doc.items.count { (doc.stockBalances[it.id] ?: 0.0) < it.minimum }.toString(), "Bajo mínimo", Icons.Outlined.Inventory2, Brand.Sand, Modifier.weight(1f).testTag("dashboard-stock"), onClick = { onlyLowStock = true; openModule(Module.STOCK) })
                    } }
                    item { Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Module.ATTENDANCE, Module.STOCK, Module.MEAL, Module.FUEL).forEach { entry -> QuickAction(moduleIcon(entry), entry.label, Modifier.weight(1f)) { openModule(entry) } }
                    } }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Próximas tareas", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { tab = 1 }) { Text("Ver agenda") } } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        val upcoming = tasks.filter { it.status != "Completada" }.sortedBy { it.time }.take(3)
                        if (upcoming.isEmpty()) Text(if (tasks.isEmpty()) "Todavía no has añadido actividades." else "No hay tareas pendientes.", color = Brand.SecondaryText)
                        upcoming.forEachIndexed { index, op ->
                            Row(Modifier.fillMaxWidth().clickable { openOperation(op) }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(color = Brand.Selected, shape = RoundedCornerShape(50)) { Text(op.time.ifBlank { "—" }, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium) }
                                Text(op.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp))
                            }
                            if (index < upcoming.lastIndex) HorizontalDivider(color = Brand.Border)
                        }
                    } }
                    if (vm.operators.isEmpty()) item { Section("Prepara tu libreta") { Text("Añade personal y equipos para empezar.", color = Brand.SecondaryText); TextButton(onClick = { tab = 4 }) { Text("Añadir personal y equipos") } } }
                }
                1 -> {
                    val tasks = doc.operations.filter { it.module == Module.TASK }
                    val done = tasks.count { it.status == "Completada" }
                    val agenda = doc.operations.filter { it.module == Module.TASK || (it.module == Module.PLAN && doc.operations.none { task -> task.module == Module.TASK && task.sourceId == it.id }) }.filter { plannerFilter == "Todas" || (plannerFilter == "Completadas" && it.status == "Completada") || (plannerFilter == "Pendientes" && it.status != "Completada") }.sortedBy { it.time }
                    item { WFHeading("Mi jornada", doc.day) { dateDialog = true } }
                    item { WeekStrip(doc.day, vm::chooseDay) }
                    item { ListReportActions(ReportQuery(ReportType.WEEK, doc.day), reportDocuments, { reportOptions = it }, { vm.message = it }) }
                    item { ProgressPanel(done, tasks.size) }
                    item { PillTabs(listOf("Todas", "Pendientes", "Completadas"), plannerFilter) { plannerFilter = it } }
                    if (agenda.isEmpty()) item { Text("No hay tareas en esta vista.", color = Brand.SecondaryText) }
                    items(agenda.size, key = { agenda[it].id }) { index -> TimelineRow(agenda[index], index == agenda.lastIndex, share = { openEvent(agenda[index]) }) { openOperation(agenda[index]) } }
                    if (plannerFilter != "Completadas" && done > 0) item { Surface(Modifier.fillMaxWidth().clickable { plannerFilter = "Completadas" }, shape = RoundedCornerShape(16.dp), color = Brand.Sage) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Green); Text("Ver $done tareas completadas", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) } } }
                    item { Button(onClick = { newOperation(Module.TASK) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir tarea") } }
                    item { TextButton(onClick = { newOperation(Module.CLOSE) }, modifier = Modifier.fillMaxWidth()) { Text(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Reabrir jornada" else "Revisar cierre del día") } }
                    items(doc.operations.filter { it.module == Module.CLOSE }) { op -> OperationCard(op, doc, report = ::openEvent) { openOperation(op) } }
                }
                2 -> {
                    item { WFHeading(module?.label ?: if (pendingOverview) "Pendientes del día" else "Operación", doc.day) { dateDialog = true } }
                    if (module == null && pendingOverview) {
                        item { TextButton(onClick = { pendingOverview = false }) { Text("Ver todos los módulos") } }
                        val groups = DailySummary.pending(doc).groupBy { it.module }.toList()
                        if (groups.isEmpty()) item { Text("No hay pendientes para esta fecha.") }
                        items(groups, key = { it.first.name }) { (which, rows) ->
                            WFCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { openModule(which) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    WFIcon(moduleIcon(which), Brand.Sand)
                                    Column(Modifier.weight(1f)) { Text(which.label, style = MaterialTheme.typography.titleSmall); Text("${rows.size} pendientes", color = Brand.SecondaryText) }
                                    Icon(Icons.Outlined.ChevronRight, "Abrir ${which.label}")
                                }
                            }
                        }
                    } else if (module == null) {
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
                        item { ListReportActions(ReportQuery(if (selectedModule == Module.MEAL && mealType == "Almuerzo") ReportType.LUNCH else ReportType.forModule(selectedModule), doc.day), reportDocuments, { reportOptions = it }, { vm.message = it }) }
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
                                if (operators.isEmpty()) item {
                                    if (vm.operators.isEmpty()) { Text("Añade operadores para marcar su asistencia."); Button(onClick = { newMaster("operator") }) { Text("Añadir operadores") } }
                                    else { Text("No encontramos operadores con esa búsqueda."); TextButton(onClick = { operatorSearch = "" }) { Text("Limpiar búsqueda") } }
                                }
                                items(operators, key = { it.id }) { operator ->
                                    val op = attendance.firstOrNull { it.operatorId == operator.id }
                                    OperatorAttendanceRow(operator, op, !vm.busy, share = op?.let { { openEvent(it) } }) { status, extended ->
                                        vm.save((op ?: Operation(day = doc.day, module = Module.ATTENDANCE, title = "Asistencia", status = status, operatorId = operator.id, shift = attendanceShift)).copy(status = status, extended = extended), offerShare = false) { }
                                    }
                                }
                                item { OutlinedButton(onClick = { vm.shareRequest = doc }, enabled = attendance.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir avance del día") } }
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
                                            Column(Modifier.weight(1f)) { Text(operator.name, style = MaterialTheme.typography.titleSmall); if (operator.code.isNotBlank()) Text(operator.code, style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText) }
                                            Text(operator.shift, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                item { WFCard(Modifier.fillMaxWidth()) { Text("$mealType para ${mealSelected.size} personas", style = MaterialTheme.typography.titleMedium)
                                    OutlinedButton(onClick = { mealPreview = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(8.dp)); Text("Vista previa de lista") }
                                    Button(onClick = { vm.save((savedMeal ?: Operation(day = doc.day, module = Module.MEAL, title = "Solicitud de ${mealType.lowercase()}", status = "Solicitada", mealType = mealType)).copy(operatorIds = mealSelected.toList().sorted())) { } }, enabled = mealSelected.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Guardar solicitud") }
                                    if (savedMeal != null) TextButton(onClick = { openEvent(savedMeal) }) { Text("Ver y compartir solicitud guardada") }
                                } }
                            }
                            Module.STOCK -> {
                                item { PillTabs(listOf("Todos", "Bajo mínimo"), if (onlyLowStock) "Bajo mínimo" else "Todos") { onlyLowStock = it == "Bajo mínimo" } }
                                if (doc.items.isEmpty()) item { Button(onClick = { newMaster("item") }) { Text("Añadir artículos") } }
                                if (onlyLowStock && doc.items.isNotEmpty() && doc.items.none { (doc.stockBalances[it.id] ?: 0.0) < it.minimum }) item {
                                    WFCard(Modifier.fillMaxWidth(), Brand.Sage) { Text("Las existencias están al día."); TextButton(onClick = { onlyLowStock = false }) { Text("Ver todos los artículos") } }
                                }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(doc.items.size.toString(), "Artículos", Icons.Outlined.Inventory2, Brand.Sky, Modifier.weight(1f)); MetricTile(doc.items.count { (doc.stockBalances[it.id] ?: 0.0) < it.minimum }.toString(), "Bajo mínimo", Icons.Outlined.WarningAmber, Brand.Sand, Modifier.weight(1f)) } }
                                items(doc.items.filter { !onlyLowStock || (doc.stockBalances[it.id] ?: 0.0) < it.minimum }, key = { it.id }) { item -> WFCard(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.Inventory2, Brand.Sand); Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text("${doc.stockBalances[item.id] ?: 0.0} ${item.unit}", style = MaterialTheme.typography.titleSmall); Text("Mínimo: ${item.minimum}", style = MaterialTheme.typography.bodySmall, color = Brand.SecondaryText) } }
                                    if ((doc.stockBalances[item.id] ?: 0.0) < item.minimum) WFStatus("Bajo mínimo")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf("Entrada", "Salida").forEach { status ->
                                            OutlinedButton(onClick = { openOperation(Operation(day = doc.day, module = Module.STOCK, title = "$status · ${item.name}", status = status, itemId = item.id)) }, modifier = Modifier.weight(1f)) {
                                                Icon(if (status == "Entrada") Icons.Outlined.South else Icons.Outlined.North, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text(status)
                                            }
                                        }
                                    }
                                } }
                                item { Text("Movimientos del día", style = MaterialTheme.typography.titleMedium) }
                                items(doc.operations.filter { it.module == Module.STOCK }, key = { it.id }) { op -> OperationCard(op, doc, report = ::openEvent) { openOperation(op) } }
                            }
                            Module.FUEL -> {
                                if (doc.assets.isEmpty()) item { Button(onClick = { newMaster("asset") }) { Text("Añadir equipos") } }
                                val fuel = doc.operations.filter { it.module == Module.FUEL }
                                item { WFCard(Modifier.fillMaxWidth(), Brand.Sand) {
                                    Text("Equipos pendientes de combustible", style = MaterialTheme.typography.titleSmall)
                                    Text("${fuel.count { it.status == "Pendiente" }} equipos con abastecimiento pendiente", style = MaterialTheme.typography.bodyMedium)
                                    ListReportActions(ReportQuery(ReportType.FUEL, doc.day, status = "Pendiente"), reportDocuments, { reportOptions = it }, { vm.message = it })
                                } }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(fuel.count { it.status == "Abastecido" }.toString(), "Abastecidos", Icons.Outlined.CheckCircle, Brand.Sage, Modifier.weight(1f)); MetricTile(fuel.count { it.status == "Pendiente" }.toString(), "Pendientes", Icons.Outlined.Schedule, Brand.Sand, Modifier.weight(1f)) } }
                                items(doc.assets, key = { it.id }) { asset ->
                                    val op = fuel.lastOrNull { it.assetId == asset.id }
                                    WFCard(Modifier.fillMaxWidth().clickable { openOperation(op ?: Operation(day = doc.day, module = Module.FUEL, title = "Combustible · ${asset.code}", status = "Pendiente", assetId = asset.id)) }) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.LocalGasStation); Column(Modifier.weight(1f)) { Text(asset.code, style = MaterialTheme.typography.titleMedium); Text("${asset.type} · ${asset.state}", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText) }; Icon(Icons.Outlined.ChevronRight, null) }
                                        WFStatus(op?.status ?: if (asset.state == "En taller") "En taller" else "Sin requerimiento")
                                        if (op != null && op.quantity > 0) Text("${op.quantity} litros · ${op.time}", style = MaterialTheme.typography.bodyMedium)
                                        if (op != null) TextButton(onClick = { openEvent(op) }) { Text("Ver y compartir registro") }
                                    }
                                }
                            }
                            else -> {
                                val rows = (doc.operations + if (selectedModule == Module.FAULT) doc.carryOverFaults else emptyList()).filter { it.module == selectedModule }
                                if (selectedModule == Module.PLAN) item { WeekStrip(doc.day, vm::chooseDay) }
                                if (selectedModule == Module.FAULT) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile(rows.count { it.status == "Abierta" }.toString(), "Abiertas", Icons.Outlined.Build, Brand.Sand, Modifier.weight(1f)); MetricTile(rows.count { it.status == "Cerrada" }.toString(), "Resueltas", Icons.Outlined.CheckCircle, Brand.Sage, Modifier.weight(1f)) } }
                                if (selectedModule == Module.FAULT) item { WFCard(Modifier.fillMaxWidth(), Brand.Sand) {
                                    Text("Equipos en taller sin resolver", style = MaterialTheme.typography.titleSmall)
                                    ListReportActions(ReportQuery(ReportType.WORKSHOP, doc.day, status = "Sin resolver"), reportDocuments, { reportOptions = it }, { vm.message = it })
                                } }
                                if (selectedModule == Module.TICKET) item { WFCard(Modifier.fillMaxWidth(), Brand.Sand) { Text("${rows.count { it.status == "Emitido" }} tickets emitidos", style = MaterialTheme.typography.titleMedium); Text("Registro formal y seguimiento por operador.", style = MaterialTheme.typography.bodyMedium) } }
                                if (rows.isEmpty()) item { Text("Sin registros para esta fecha. Pulsa Registrar para añadir uno.", color = Brand.SecondaryText) }
                                items(rows, key = { it.id }) { op -> OperationCard(op, doc, report = ::openEvent) { openOperation(op) } }
                            }
                        }
                    }
                }
                3 -> {
                    item { WFHeading("Reportes", doc.day) { dateDialog = true } }
                    item { Button(onClick = { reportOptions = ReportQuery(ReportType.GENERAL, doc.day) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("report-options")) { Icon(Icons.Outlined.DateRange, null); Spacer(Modifier.width(8.dp)); Text("Reporte por módulo o período") } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { WFIcon(Icons.Outlined.Assessment); Column(Modifier.weight(1f)) { Text("Cómo va el día", style = MaterialTheme.typography.titleMedium); Text("${doc.supervisor} · Actualización ${doc.revision}", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("${DailySummary.present(doc)} presentes", style = MaterialTheme.typography.titleSmall); Text("${DailySummary.pending(doc).size} pendientes", style = MaterialTheme.typography.titleSmall) }
                        WFStatus("Informe al día")
                        TextButton(onClick = { visualReport = ReportQuery(ReportType.GENERAL, doc.day) }) { Text("Ver informe completo"); Icon(Icons.Outlined.ChevronRight, null) }
                    } }
                    item { WFCard(Modifier.fillMaxWidth()) { Text("Compartir el progreso", style = MaterialTheme.typography.titleMedium); Text("Envía un informe protegido para abrir en WorkFlow, o un documento para leer e imprimir.", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText)
                        Button(onClick = { vm.shareRequest = doc }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir informe") }
                        OutlinedButton(onClick = { importer.launch(arrayOf(ProtectedReport.MIME_TYPE, "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Abrir informe recibido") }
                    } }
                    item { Text("Informes recibidos", style = MaterialTheme.typography.titleMedium) }
                    if (vm.received.isEmpty()) item { WFCard(Modifier.fillMaxWidth()) { WFIcon(Icons.Outlined.FolderOpen, Brand.Sky); Text("Las jornadas compartidas aparecerán aquí.", style = MaterialTheme.typography.titleSmall); Text("Abre la jornada recibida para cargar sus datos y continuar desde donde la dejó tu equipo.", color = Brand.SecondaryText, style = MaterialTheme.typography.bodyMedium) } }
                    items(vm.received, key = { "${it.originId}|${it.day}" }) { incoming ->
                        Section("${incoming.supervisor} · ${incoming.day}") { Text("Actualización ${incoming.revision}", color = Brand.SecondaryText); Text("${DailySummary.present(incoming)} presentes · ${DailySummary.pending(incoming).size} pendientes"); TextButton(onClick = { vm.chooseDay(incoming.day); tab = 0 }) { Text("Abrir jornada en el inicio") } }
                    }
                }
                4 -> {
                    if (catalog != null) {
                        item { ListReportActions(ReportQuery(when(catalog) { "operator" -> ReportType.OPERATORS; "asset" -> ReportType.ASSETS; else -> ReportType.STOCK }, doc.day), reportDocuments, { reportOptions = it }, { vm.message = it }) }
                        catalogRows(catalog!!, vm, catalogSearch, { catalogSearch = it }, { catalog = null }, { newMaster(catalog!!) },
                        editOperator = { newMaster("operator"); editOperator = it },
                        editAsset = { newMaster("asset"); editAsset = it },
                        editItem = { newMaster("item"); editItem = it })
                    }
                    else {
                        item { Text("Configuración y catálogos", style = MaterialTheme.typography.headlineMedium) }
                        item { Section("Alarmas de la agenda") { Text("Los avisos son opcionales. Puedes iniciar y continuar la jornada sin activarlos.");OutlinedButton(onClick={alarmSettings=true}){Text("Configurar alarmas")} } }
                        item { Section("Personal y equipos") {
                            listOf(Triple("operator", "Personal", vm.operators.size), Triple("asset", "Equipos", vm.assets.size), Triple("item", "Artículos", vm.items.size)).forEach { (kind, label, count) ->
                                OutlinedButton(onClick = { openCatalog(kind) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("catalog-open-$kind")) {
                                    Text(label, Modifier.weight(1f)); Text(count.toString()); Icon(Icons.Outlined.ChevronRight, null)
                                }
                            }
                        } }
                        item { Section("Responsable") {
                            var name by remember(doc.supervisor) { mutableStateOf(doc.supervisor) }
                            OutlinedTextField(name, { name = it }, label = { Text("Nombre del supervisor") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { vm.supervisor(name) }, enabled = !vm.busy) { Text("Guardar responsable") }
                        } }
                        item { Section("Compartir al guardar") {
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(vm.shareAfterSave, vm::setOfferShare); Text("Ofrecer compartir tras guardar", Modifier.weight(1f)) }
                            Text("Desactivado inicialmente. Los registros se guardan al pulsar Guardar; compartir es una acción independiente.")
                        } }
                        item { Section("Protección de informes") {
                            Text(if (vm.hasTransferKey) "Clave guardada en este teléfono" else "Configura una clave para compartir con tu equipo")
                            Text("Usa la misma clave en los teléfonos del equipo para cargar y continuar las jornadas compartidas.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = vm::requestTransferSetup, enabled = !vm.busy) { Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(8.dp)); Text(if (vm.hasTransferKey) "Cambiar clave" else "Configurar clave") }
                        } }
                        item { Section("Jornadas anteriores") { vm.history.forEach { past -> TextButton(onClick = { vm.chooseDay(past.day); tab = 0 }) { Text(past.day) } } } }
                        item { Text("Tu información permanece en este teléfono. Comparte solo con las personas de tu equipo.", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText) }
                    }
                }
            }
        }
    }
    if (mealPreview) {
        val names = vm.operators.filter { it.id in mealSelected }
        val text = "*${mealType.uppercase()} · ${ReportBuilder.date(vm.day)}*\n*Total: ${names.size} personas*\n\n" + names.mapIndexed { i,op -> "${i+1}. ${op.name}" }.joinToString("\n") + "\n\nWorkFlow"
        SafeAlertDialog(scrollText = false, onDismissRequest = { mealPreview = false }, title = { Text("$mealType · ${mealSelected.size} personas") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyReportText(context, text); vm.message = "Listado copiado" }, modifier = Modifier.weight(1f)) { Text("Copiar") }
                    Button(onClick = { com.workflow.operaciones.export.Reports.shareText(context, text) }, modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) { items(names) { Text(it.displayLabel, Modifier.padding(vertical = 6.dp)) } }
            }
        }, confirmButton = { TextButton(onClick = { mealPreview = false }) { Text("Cerrar") } })
    }
    if (dateDialog) WorkDatePicker(vm.day,{dateDialog=false}){vm.chooseDay(it);dateDialog=false}
    if (startDay) StartDayDialog(vm) { startDay = false }
    if (alarmSettings) AgendaSettingsDialog(vm) { alarmSettings = false }
    reportOptions?.let { initial -> ReportOptionsDialog(initial, vm, close = { reportOptions = null }, show = { visualReport = it }) }
    visualReport?.let { query -> VisualReportViewer(query, reportDocuments) { visualReport = null } }
    vm.preview?.let { incoming ->
        val conflict = vm.incomingDecision in listOf(ContinuationDecision.CONFLICT, ContinuationDecision.OLDER)
        var replacing by rememberSaveable(incoming.checksum) { mutableStateOf(false) }
        var inspectIncoming by rememberSaveable(incoming.checksum) { mutableStateOf(false) }
        var inspectLocal by rememberSaveable(incoming.checksum) { mutableStateOf(false) }
        val local = vm.history.firstOrNull { it.day == incoming.day }
        SafeAlertDialog(onDismissRequest = vm::dismissPreview, title = { Text(if (conflict) "Revisar versiones de la jornada" else "Cargar jornada compartida") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${incoming.supervisor}\n${incoming.day} · Actualización ${incoming.revision}\n${incoming.operations.size} registros")
                Text("Se cargarán en el inicio los datos de esta fecha: tareas, asistencia, comidas, combustible, averías y demás registros. Podrás continuar la operación y volver a compartirla.")
                if (conflict) {
                    Text(if (vm.incomingDecision == ContinuationDecision.OLDER) "Este teléfono tiene una versión más reciente." else "Ambos teléfonos hicieron cambios distintos.", color = Brand.Red)
                    Text("En este teléfono: ${local?.operations?.size ?: 0} registros\nRecibidos: ${incoming.operations.size} registros")
                    Text(if (replacing) "Al continuar usarás la jornada recibida en esta fecha. Se conserva una copia de la versión anterior en el historial interno." else "Puedes cancelar para conservar tu jornada o revisar la sustitución antes de continuar.")
                }
                TextButton(onClick = { inspectIncoming = true }) { Text("Ver jornada recibida") }
                if (conflict && local != null) TextButton(onClick = { inspectLocal = true }) { Text("Ver mi jornada") }
                vm.actionError?.let { Text(it, color = Brand.Red) }
            }
        }, confirmButton = { TextButton(onClick = { if (conflict && !replacing) replacing = true else vm.acceptPreview(replaceConflict = replacing) }, enabled = !vm.busy) { Text(if (conflict && !replacing) "Revisar sustitución" else if (replacing) "Sustituir y continuar" else "Cargar jornada") } }, dismissButton = { TextButton(onClick = vm::dismissPreview) { Text("Cancelar") } })
        if (inspectIncoming) VisualReportViewer(ReportQuery(ReportType.GENERAL, incoming.day), listOf(incoming)) { inspectIncoming = false }
        if (inspectLocal && local != null) VisualReportViewer(ReportQuery(ReportType.GENERAL, local.day), listOf(local)) { inspectLocal = false }
    }
    vm.shareRequest?.let { report ->
        var otherApps by remember { mutableStateOf(false) }
        SafeAlertDialog(onDismissRequest = { if (!vm.busy) vm.shareRequest = null }, title = { Text("Compartir informe") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Informe del ${report.day}")
                Text("Para cargar los datos y continuar la jornada en otro teléfono:", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.shareReport(context, report, pdf = false, whatsapp = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !vm.busy) {
                    Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(8.dp)); Text("Continuar jornada por WhatsApp")
                }
                Text("Para leer, imprimir o enviar a alguien que no usa WorkFlow:", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { vm.shareReport(context, report, pdf = true, whatsapp = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !vm.busy) { Text("Documento PDF por WhatsApp") }
                TextButton(onClick = { otherApps = !otherApps }, enabled = !vm.busy) { Text("Usar otra aplicación") }
                if (otherApps) {
                    OutlinedButton(onClick = { vm.shareReport(context, report, pdf = false, whatsapp = false) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Text("Compartir informe protegido") }
                    OutlinedButton(onClick = { vm.shareReport(context, report, pdf = true, whatsapp = false) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Text("Compartir documento PDF") }
                }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = { vm.shareRequest = null }, enabled = !vm.busy) { Text("Ahora no") } })
    }
    if (vm.needsTransferSetup) ReportKeyDialog(setup = true, busy = vm.busy, error = vm.transferKeyError, onDismiss = vm::dismissTransferSetup) { phrase, _ -> vm.configureTransferKey(context, phrase) }
    if (vm.incomingNeedsKey) ReportKeyDialog(setup = false, busy = vm.busy, error = vm.incomingKeyError, onDismiss = vm::cancelIncoming, onSubmit = vm::unlockIncoming)
}
