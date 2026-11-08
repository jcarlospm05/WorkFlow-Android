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
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=12.dp,vertical=4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(edit!=null)Modifier.clickable(onClick=edit)else Modifier),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(op.title,style=MaterialTheme.typography.titleSmall)
                val detail=listOfNotNull(doc.operators.firstOrNull{it.id==op.operatorId}?.name,doc.assets.firstOrNull{it.id==op.assetId}?.code,op.time.takeIf{it.isNotBlank()}).joinToString(" · ")
                if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
            }
            Text(op.status,style=MaterialTheme.typography.labelMedium,color=if(op.status in listOf("Abierta","En proceso","Pendiente"))Brand.Red else Brand.Petroleum)
            if(edit!=null)Icon(Icons.Outlined.ChevronRight,"Abrir registro",Modifier.size(18.dp))
        }
        report?.let { action -> TextButton(onClick={action(op)},contentPadding=PaddingValues(horizontal=0.dp)){Text("Ver / compartir",style=MaterialTheme.typography.labelMedium)} }
        HorizontalDivider(color=Brand.Border)
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
    var reportSearch by rememberSaveable { mutableStateOf("") }
    var moreReportTopics by rememberSaveable { mutableStateOf(false) }
    var visualReport by rememberSaveable(stateSaver = formSaver<ReportQuery>()) { mutableStateOf<ReportQuery?>(null) }
    val reportDocuments = (vm.history + listOfNotNull(vm.document)).associateBy { it.day }.values.toList()
    var catalog by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogSearch by rememberSaveable { mutableStateOf("") }
    var pendingOverview by rememberSaveable { mutableStateOf(false) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var plannerFilter by rememberSaveable { mutableStateOf("Pendientes") }
    var operatorSearch by rememberSaveable { mutableStateOf("") }
    var mealType by rememberSaveable { mutableStateOf("Cena") }
    var mealPreview by rememberSaveable { mutableStateOf(false) }
    var mealEditing by rememberSaveable(vm.day, mealType) { mutableStateOf(false) }
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
        tab = 2; module = which
    }
    LaunchedEffect(module,catalog) { if(module==Module.STOCK)module=null;if(catalog=="item")catalog=null }
    fun captureFormRoute() { formTab = tab; formModule = module; formCatalog = catalog; formDay = vm.day; formVisible = true }
    fun openOperation(op: Operation) { captureFormRoute(); editor = op; master = null }
    fun executeTask(op: Operation) {
        if(op.module!=Module.TASK || op.status=="Completada") { openOperation(op);return }
        val target=TaskRouting.destination(op,vm.document?.operations.orEmpty())
        openModule(target)
        if(target==Module.MEAL) mealType=if(op.templateKey=="lunch")"Almuerzo" else op.mealType
    }
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        Button(onClick = { if (!DayControl.started(doc)) { vm.clearActionError(); startDay = true } else { tab = 2; module = null } }, enabled=!vm.busy&&unfinishedDays.isEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.Petroleum), modifier = Modifier.heightIn(min = 48.dp).testTag("start-or-continue-day"), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (!DayControl.started(doc)) "Iniciar jornada" else "Continuar jornada")
                        }
                    } }
                    item { TextButton(onClick={alarmSettings=true},modifier=Modifier.testTag("alarm-settings")){Text("Alarmas de agenda y cierre · 7:00 p. m.")} }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(DailySummary.present(doc).toString(), "Presentes", Icons.Outlined.People, Brand.Sage, Modifier.weight(1f).testTag("dashboard-attendance"), onClick = { openModule(Module.ATTENDANCE) })
                        MetricTile(DailySummary.pending(doc).size.toString(), "Pendientes", Icons.Outlined.Checklist, Brand.Sky, Modifier.weight(1f).testTag("dashboard-pending"), onClick = { tab = 2; module = null; catalog = null; pendingOverview = true })
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(doc.assets.count { it.state == "Activo" }.toString(), "Equipos activos", Icons.Outlined.LocalShipping, Brand.Sky, Modifier.weight(1f).testTag("dashboard-assets"), onClick = { catalogSearch = "@activos"; tab = 4; module = null; catalog = "asset" })
                        MetricTile(doc.assets.count { it.state in listOf("En taller","Inactivo temporal") }.toString(), "En taller", Icons.Outlined.Build, Brand.Sand, Modifier.weight(1f).testTag("dashboard-workshop"), onClick = { openModule(Module.FAULT) })
                    } }
                    item { Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium) }
                    item {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(Module.ATTENDANCE,Module.MEAL,Module.FUEL,Module.FAULT).chunked(2).forEach { row ->
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    row.forEach { entry -> QuickAction(moduleIcon(entry),entry.label,Modifier.weight(1f)){openModule(entry)} }
                                }
                            }
                        }
                    }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Próximas tareas", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { tab = 1 }) { Text("Ver agenda") } } }
                    item { WFCard(Modifier.fillMaxWidth()) {
                        val upcoming = tasks.filter { it.status != "Completada" }.sortedBy { it.time }.take(3)
                        if (upcoming.isEmpty()) Text(if (tasks.isEmpty()) "Todavía no has añadido actividades." else "No hay tareas pendientes.", color = Brand.SecondaryText)
                        upcoming.forEachIndexed { index, op ->
                            Row(Modifier.fillMaxWidth().clickable { executeTask(op) }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    items(agenda.size, key = { agenda[it].id }) { index -> TimelineRow(agenda[index], index == agenda.lastIndex, share = { openEvent(agenda[index]) }) { executeTask(agenda[index]) } }
                    if (plannerFilter != "Completadas" && done > 0) item { Surface(Modifier.fillMaxWidth().clickable { plannerFilter = "Completadas" }, shape = RoundedCornerShape(16.dp), color = Brand.Sage) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Green); Text("Ver $done tareas completadas", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) } } }
                    item { Button(onClick = { newOperation(Module.TASK) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir tarea") } }
                    item { TextButton(onClick = { newOperation(Module.CLOSE) }, modifier = Modifier.fillMaxWidth()) { Text(if (doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) "Reabrir jornada" else "Revisar cierre del día") } }
                    items(doc.operations.filter { it.module == Module.CLOSE }) { op -> OperationCard(op, doc, report = ::openEvent) { openOperation(op) } }
                }
                2 -> {
                    item { WFHeading(module?.let(TaskRouting::label) ?: if (pendingOverview) "Pendientes del día" else "Operación", doc.day) { dateDialog = true } }
                    if (module == null && pendingOverview) {
                        item { TextButton(onClick = { pendingOverview = false }) { Text("Ver todos los módulos") } }
                        val groups = TaskRouting.modules.map { it to TaskRouting.pending(doc,it) }.filter { it.second.isNotEmpty() }
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
                        item { OutlinedButton(onClick={tab=1;plannerFilter="Pendientes"},modifier=Modifier.fillMaxWidth().testTag("operations-pending")){Icon(Icons.Outlined.Checklist,null);Spacer(Modifier.width(8.dp));Text("Tareas pendientes · ${doc.operations.count{it.module==Module.TASK && it.status!="Completada"}}") } }
                        items(TaskRouting.modules.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { entry ->
                                WFCard(Modifier.weight(1f).testTag("module-${entry.name}").clickable { openModule(entry) }.heightIn(min = 112.dp)) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        WFIcon(moduleIcon(entry), if(entry in listOf(Module.FAULT,Module.TICKET))Brand.Sand else Brand.Selected)
                                        Spacer(Modifier.weight(1f))
                                        val pending=TaskRouting.pending(doc,entry).size
                                        if(pending>0)Badge(containerColor=Brand.Red,contentColor=Color.White,modifier=Modifier.testTag("pending-${entry.name}")){Text(pending.toString())}
                                    }
                                    Text(TaskRouting.label(entry), style = MaterialTheme.typography.titleSmall)
                                    Text("${doc.operations.count{it.module==entry}} registros",style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
                                }
                            }; if (row.size == 1) Spacer(Modifier.weight(1f)) }
                        }
                    } else {
                        val selectedModule = module!!
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { module = null }) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Módulos") }
                            if (selectedModule !in listOf(Module.MEAL,Module.ATTENDANCE)) Button(onClick = { newOperation(selectedModule) }, enabled = !vm.busy) { Icon(Icons.Outlined.Add, null); Text(if (selectedModule == Module.ATTENDANCE) "Detalle" else "Registrar") }
                        } }
                        item { ListReportActions(ReportQuery(if (selectedModule == Module.MEAL && mealType == "Almuerzo") ReportType.LUNCH else ReportType.forModule(selectedModule), doc.day), reportDocuments, { reportOptions = it }, { vm.message = it }) }
                        val moduleTasks=doc.operations.filter{it.module==Module.TASK && it.status!="Completada" && TaskRouting.destination(it,doc.operations)==selectedModule}
                        if(moduleTasks.isNotEmpty())item { Text("Tareas pendientes · ${moduleTasks.size}",style=MaterialTheme.typography.titleSmall) }
                        items(moduleTasks,key={"task-${it.id}"}) { task ->
                            Row(Modifier.fillMaxWidth().background(Brand.Sand).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(false,{checked->if(checked)vm.save(task.copy(status="Completada"),offerShare=false){}},enabled=!vm.busy)
                                Column(Modifier.weight(1f)){Text(task.title,style=MaterialTheme.typography.bodyMedium);if(task.time.isNotBlank())Text(task.time,style=MaterialTheme.typography.bodySmall)}
                                IconButton(onClick={openOperation(task)}){Icon(Icons.Outlined.Edit,"Editar tarea")}
                            }
                        }
                        when (selectedModule) {
                            Module.ATTENDANCE -> {
                                val attendance=AttendanceList.latest(doc.operations)
                                val marked=attendance.mapNotNull{it.operatorId}.toSet()
                                val operators=vm.operators.filter{it.active && (operatorSearch.isBlank() || it.displayLabel.contains(operatorSearch,true))}.sortedBy{it.name.lowercase()}
                                item { OutlinedTextField(operatorSearch,{operatorSearch=it},label={Text("Buscar operador")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
                                item { Text("Sin marcar · ${operators.count{it.id !in marked}}",style=MaterialTheme.typography.titleSmall) }
                                items(operators.filter{it.id !in marked},key={it.id}) { operator ->
                                    CompactAttendanceRow(operator,null,!vm.busy) { status,ext ->
                                        vm.save(Operation(day=doc.day,module=Module.ATTENDANCE,title="Asistencia",status=status,operatorId=operator.id,shift=operator.shift,extended=ext),offerShare=false){}
                                    }
                                }
                                if(vm.operators.isEmpty())item { Button(onClick={newMaster("operator")}){Text("Añadir operadores")} }
                                listOf("Presentes" to attendance.filter{it.status in listOf("Presente","Tardanza")},"Ausencias y libres" to attendance.filter{it.status !in listOf("Presente","Tardanza")}).forEach { (title,rows) ->
                                    item { Text("$title · ${rows.size}",style=MaterialTheme.typography.titleSmall) }
                                    items(rows.filter{op->operatorSearch.isBlank() || vm.operators.firstOrNull{it.id==op.operatorId}?.displayLabel?.contains(operatorSearch,true)==true},key={"marked-${it.id}"}) { op ->
                                        vm.operators.firstOrNull{it.id==op.operatorId}?.let { person -> CompactAttendanceRow(person,op,!vm.busy) { status,ext -> vm.save(op.copy(status=status,extended=ext),offerShare=false){} } }
                                    }
                                }
                            }
                            Module.MEAL -> {
                                item { PillTabs(listOf("Cena","Almuerzo"),mealType){mealType=it} }
                                if(savedMeal!=null && !mealEditing) {
                                    item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${savedMeal.operatorIds.size} personas",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);OutlinedButton(onClick={mealEditing=true}){Text("Modificar lista")} } }
                                    items(vm.operators.filter{it.id in savedMeal.operatorIds}.sortedBy{it.name.lowercase()},key={it.id}) { person -> CompactNameRow(person.name) }
                                    item { TextButton(onClick={openEvent(savedMeal)}){Text("Ver / compartir solicitud")} }
                                } else {
                                    item { Text("${mealSelected.size} seleccionados",style=MaterialTheme.typography.titleSmall) }
                                    items(vm.operators.filter{it.active || it.id in mealSelected}.sortedBy{it.name.lowercase()},key={it.id}) { person ->
                                        Row(Modifier.fillMaxWidth().background(Color.White),verticalAlignment=Alignment.CenterVertically){Checkbox(person.id in mealSelected,{checked->mealSelected=if(checked)mealSelected+person.id else mealSelected-person.id});Text(person.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)}
                                    }
                                    item { Button(onClick={vm.save((savedMeal?:Operation(day=doc.day,module=Module.MEAL,title="Solicitud de ${mealType.lowercase()}",status="Solicitada",mealType=mealType)).copy(operatorIds=mealSelected.toList().sorted()),offerShare=false){mealEditing=false}},enabled=mealSelected.isNotEmpty()&&!vm.busy,modifier=Modifier.fillMaxWidth()){Text("Guardar lista")} }
                                    if(savedMeal!=null)item { TextButton(onClick={mealSelected=savedMeal.operatorIds.toSet();mealEditing=false}){Text("Cancelar cambios")} }
                                }
                            }
                            Module.STOCK -> Unit // Legacy records stay readable internally; no inventory interface.
                            Module.TASK -> {
                                item { Text("Gestiones del día: correo, alquileres y otras actividades.",style=MaterialTheme.typography.bodyMedium) }
                                items(doc.operations.filter{it.module==Module.TASK && TaskRouting.destination(it,doc.operations)==Module.TASK && it.status=="Completada"},key={it.id}) { op -> OperationCard(op,doc,report=::openEvent){openOperation(op)} }
                            }
                            Module.FUEL -> {
                                if (doc.assets.isEmpty()) item { Button(onClick = { newMaster("asset") }) { Text("Añadir equipos") } }
                                val fuel = doc.operations.filter { it.module == Module.FUEL }
                                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    Text("${fuel.count{it.status=="Abastecido"}} abastecidos · ${fuel.count{it.status=="Pendiente"}} pendientes",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                } }
                                item { Text("Listado de equipos pendientes",style=MaterialTheme.typography.titleSmall);ListReportActions(ReportQuery(ReportType.FUEL,doc.day,status="Pendiente"),reportDocuments,{reportOptions=it},{vm.message=it}) }
                                items(doc.assets, key = { it.id }) { asset ->
                                    val op = fuel.lastOrNull { it.assetId == asset.id }
                                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=10.dp)) {
                                        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { openOperation(op ?: Operation(day=doc.day,module=Module.FUEL,title="Combustible · ${asset.code}",status="Pendiente",assetId=asset.id)) },verticalAlignment=Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)){Text(asset.code,style=MaterialTheme.typography.titleSmall);Text("${asset.type} · ${asset.state}",style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)}
                                            Text(op?.status ?: "Sin requerimiento",style=MaterialTheme.typography.labelMedium,color=if(op?.status=="Pendiente")Brand.Red else Brand.SecondaryText)
                                            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))
                                        }
                                        if(op!=null)TextButton(onClick={openEvent(op)},contentPadding=PaddingValues(0.dp)){Text(if(op.quantity>0)"${op.quantity} L · Ver registro"else "Ver / compartir",style=MaterialTheme.typography.labelMedium)}
                                        HorizontalDivider(color=Brand.Border)
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
                    reportCenterRows(doc.day,reportSearch,moreReportTopics,{reportSearch=it},{moreReportTopics=!moreReportTopics},{reportOptions=it})
                    item { WFCard(Modifier.fillMaxWidth()) { Text("Continuar en otro celular", style = MaterialTheme.typography.titleMedium); Text("Envía la jornada guardada para que otra persona pueda seguir trabajando con sus registros.", style = MaterialTheme.typography.bodyMedium, color = Brand.SecondaryText)
                        Button(onClick = { vm.shareRequest = doc }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir jornada") }
                        OutlinedButton(onClick = { importer.launch(arrayOf(ProtectedReport.MIME_TYPE, "application/octet-stream", "application/json")) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Abrir informe recibido") }
                    } }
                    item { Text("Informes recibidos", style = MaterialTheme.typography.titleMedium) }
                    if (vm.received.isEmpty()) item { WFCard(Modifier.fillMaxWidth()) { WFIcon(Icons.Outlined.FolderOpen, Brand.Sky); Text("Las jornadas compartidas aparecerán aquí.", style = MaterialTheme.typography.titleSmall); Text("Abre la jornada recibida para cargar sus datos y continuar desde donde la dejó tu equipo.", color = Brand.SecondaryText, style = MaterialTheme.typography.bodyMedium) } }
                    items(vm.received, key = { "${it.originId}|${it.day}" }) { incoming ->
                        Section("${incoming.supervisor} · ${incoming.day}") { Text("Actualización ${incoming.revision}", color = Brand.SecondaryText); Text("${DailySummary.present(incoming)} presentes · ${DailySummary.pending(incoming).size} pendientes"); TextButton(onClick = { vm.chooseDay(incoming.day); tab = 0 }) { Text("Abrir jornada en el inicio") } }
                    }
                }
                4 -> {
                    if (catalog != null) {
                        item { ListReportActions(ReportQuery(when(catalog) { "operator" -> ReportType.OPERATORS; "asset" -> ReportType.ASSETS; else -> ReportType.STOCK }, doc.day, status=if(catalogSearch=="@activos")"Activo"else null), reportDocuments, { reportOptions = it }, { vm.message = it }) }
                        catalogRows(catalog!!, vm, catalogSearch, { catalogSearch = it }, { catalog = null }, { newMaster(catalog!!) },
                        editOperator = { newMaster("operator"); editOperator = it },
                        editAsset = { newMaster("asset"); editAsset = it },
                        editItem = { newMaster("item"); editItem = it })
                    }
                    else {
                        item { Text("Configuración y catálogos", style = MaterialTheme.typography.headlineMedium) }
                        item { Section("Alarmas de la agenda") { Text("Los avisos son opcionales. Puedes iniciar y continuar la jornada sin activarlos.");OutlinedButton(onClick={alarmSettings=true}){Text("Configurar alarmas")} } }
                        item { Section("Personal y equipos") {
                            listOf(Triple("operator", "Personal", vm.operators.size), Triple("asset", "Equipos", vm.assets.size)).forEach { (kind, label, count) ->
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
    visualReport?.let { query -> VisualReportViewer(query, reportDocuments,edit={visualReport=null;reportOptions=it}) { visualReport = null } }
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
                listOf(Triple(ShareKind.PROGRESS,"Compartir progreso","Continuar el trabajo en otro celular"),Triple(ShareKind.REPORT,"Compartir informe","PDF y tablas para Excel"),Triple(ShareKind.CLOSE,"Compartir cierre","PDF, tablas y jornada para continuar")).forEach { (kind,label,detail) ->
                    OutlinedButton(onClick={vm.shareDay(context,report,kind,!otherApps)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!vm.busy){Column(Modifier.fillMaxWidth()){Text(label,style=MaterialTheme.typography.titleSmall);Text(detail,style=MaterialTheme.typography.bodySmall)}}
                }
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(otherApps,{otherApps=it});Text("Elegir otra aplicación")}
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = { vm.shareRequest = null }, enabled = !vm.busy) { Text("Ahora no") } })
    }
    if (vm.needsTransferSetup) ReportKeyDialog(setup = true, busy = vm.busy, error = vm.transferKeyError, onDismiss = vm::dismissTransferSetup) { phrase, _ -> vm.configureTransferKey(context, phrase) }
    if (vm.incomingNeedsKey) ReportKeyDialog(setup = false, busy = vm.busy, error = vm.incomingKeyError, onDismiss = vm::cancelIncoming, onSubmit = vm::unlockIncoming)
}
