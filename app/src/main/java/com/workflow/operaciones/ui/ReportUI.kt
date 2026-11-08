package com.workflow.operaciones.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.workflow.core.*
import com.workflow.operaciones.export.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

internal fun copyReportText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("WorkFlow", text))
}
@Composable internal fun ReportDropdown(label: String, value: String?, choices: List<Pair<String?, String>>, onChoose: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(choices.firstOrNull { it.first == value }?.second ?: "Todos") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            choices.forEach { (id,name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChoose(id); expanded = false }) }
        }
    }
}
@Composable internal fun ReportOptionsDialog(initial: ReportQuery, vm: WorkflowViewModel, close: () -> Unit, show: (ReportQuery) -> Unit) {
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var from by rememberSaveable { mutableStateOf(initial.from) }
    var to by rememberSaveable { mutableStateOf(initial.to) }
    var operator by rememberSaveable { mutableStateOf(initial.operatorId) }
    var asset by rememberSaveable { mutableStateOf(initial.assetId) }
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(initial.operatorId!=null || initial.assetId!=null || initial.status!=null || initial.type in listOf(ReportType.OPERATOR_TICKETS,ReportType.ASSET_HISTORY)) }
    SafeAlertDialog(onDismissRequest = close, title = { Text("Preparar reporte") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportDropdown("Reporte", type.name, ReportType.entries.filter { it !in listOf(ReportType.TICKET, ReportType.EVENT) }.map { it.name to it.label }) { value -> type = ReportType.valueOf(value!!); status = null; operator = null; asset = null;filters=type in listOf(ReportType.OPERATOR_TICKETS,ReportType.ASSET_HISTORY) }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = { from=vm.day;to=vm.day }) { Text("Día") }
                TextButton(onClick = { val d=LocalDate.parse(vm.day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));from=d.toString();to=d.plusDays(6).toString() }) { Text("Semana") }
                TextButton(onClick = { val d=LocalDate.parse(vm.day);from=d.withDayOfMonth(1).toString();to=d.withDayOfMonth(d.lengthOfMonth()).toString() }) { Text("Mes") }
            }
            WorkDateField("Desde",from,"report-from"){from=it;error=null}
            WorkDateField("Hasta",to,"report-to"){to=it;error=null}
            val applied=listOf(operator,asset,status).count{it!=null}
            TextButton(onClick={filters=!filters},modifier=Modifier.fillMaxWidth().testTag("report-filters")){Text(if(filters)"Ocultar filtros" else if(applied>0)"Ver filtros · $applied activos" else "Filtrar por operador, equipo o estado")}
            if(filters) {
            if(type !in listOf(ReportType.ASSETS,ReportType.STOCK)) ReportDropdown("Operador",operator,listOf(null to "Todos los operadores")+vm.operators.map{it.id to it.displayLabel}) { operator=it }
            if(type in listOf(ReportType.GENERAL,ReportType.CLOSE,ReportType.FUEL,ReportType.WORKSHOP,ReportType.ASSET_HISTORY,ReportType.ASSETS)) ReportDropdown("Equipo",asset,listOf(null to "Todos los equipos")+vm.assets.map{it.id to it.code}) { asset=it }
            val statuses = when(type) {
                ReportType.FUEL -> Rules.statuses.getValue(com.workflow.core.Module.FUEL)
                ReportType.TICKETS,ReportType.OPERATOR_TICKETS -> Rules.statuses.getValue(com.workflow.core.Module.TICKET)
                ReportType.WORKSHOP,ReportType.ASSET_HISTORY -> listOf("Sin resolver")+Rules.statuses.getValue(com.workflow.core.Module.FAULT)
                else -> emptyList()
            }
            if(statuses.isNotEmpty())ReportDropdown("Estado",status,listOf(null to "Todos los estados")+statuses.map{it to it}){status=it}
            if(applied>0)TextButton(onClick={operator=null;asset=null;status=null}){Text("Quitar filtros")}
            }
            error?.let { Text(it,color=Brand.Red) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { val query=ReportQuery(type,from.trim(),to.trim(),operator,asset,status=status);query.validate();show(query);close() }
        catch(_:java.time.DateTimeException){error="Revisa las fechas. Usa AAAA-MM-DD."}
        catch(e:IllegalArgumentException){error=e.message}
    },modifier=Modifier.testTag("report-generate")){Text("Ver reporte")} }, dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}

@Composable internal fun ListReportActions(query: ReportQuery, documents: List<DailyDocument>, open: (ReportQuery) -> Unit, notify: (String) -> Unit) {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={open(query)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Icon(Icons.Outlined.Assessment,null);Text("Reporte")}
        OutlinedButton(onClick={copyReportText(context,ReportBuilder.build(query,documents).text());notify("Listado copiado")},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Icon(Icons.Outlined.ContentCopy,"Copiar listado")}
        OutlinedButton(onClick={Reports.shareText(context,ReportBuilder.build(query,documents).text())},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Icon(Icons.Outlined.Share,"Compartir listado por WhatsApp")}
    }
}

@Composable internal fun VisualReportViewer(query: ReportQuery, documents: List<DailyDocument>, close: () -> Unit) {
    val context=LocalContext.current
    val report=remember(query,documents){ReportBuilder.build(query,documents)}
    var pdf by remember(report){mutableStateOf<File?>(null)}
    var images by remember(report){mutableStateOf<List<File>>(emptyList())}
    var error by remember(report){mutableStateOf<String?>(null)}
    var sharing by rememberSaveable { mutableStateOf(false) }
    var otherApps by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(report) {
        try { val result=withContext(Dispatchers.IO){ val file=Reports.pdf(context,report);file to Reports.images(context,file) };pdf=result.first;images=result.second }
        catch(_:Exception){error="No se pudo preparar el documento. Revisa el espacio disponible e inténtalo de nuevo."}
    }
    SafeFullscreenDialog(onDismissRequest=close) {
        Text(report.title,style=MaterialTheme.typography.titleLarge)
        Text(report.period,style=MaterialTheme.typography.bodyMedium)
        if(error!=null) Text(error!!,color=Brand.Red)
        if(pdf==null&&error==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(images,key={it.name}) { file ->
                var bitmap by remember(file) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(file) {
                    bitmap=withContext(Dispatchers.IO){BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=2})?.asImageBitmap()}
                }
                bitmap?.let{Image(it,contentDescription="Página de ${report.title}",modifier=Modifier.fillMaxWidth().aspectRatio(it.width.toFloat()/it.height))}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick=close,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Cerrar")}
            OutlinedButton(onClick={copyReportText(context,report.text())},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Copiar texto")}
            Button(onClick={sharing=true},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Compartir")}
        }
    }
    if(sharing)SafeAlertDialog(onDismissRequest={sharing=false},title={Text("Compartir reporte")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(report.period)
            Button(onClick={Reports.shareText(context,report.text(),!otherApps);sharing=false},modifier=Modifier.fillMaxWidth()){Text("Listado de texto")}
            OutlinedButton(onClick={Reports.share(context,images,!otherApps);sharing=false},enabled=images.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(if(images.size>1)"Imágenes JPG · ${images.size} páginas"else "Imagen JPG")}
            OutlinedButton(onClick={pdf?.let{Reports.share(context,it,!otherApps)};sharing=false},enabled=pdf!=null,modifier=Modifier.fillMaxWidth()){Text("Documento PDF")}
            Row { Checkbox(otherApps,{otherApps=it});Text("Elegir otra aplicación") }
            if(!otherApps)Text("Se abrirá WhatsApp para que elijas el destinatario.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={sharing=false}){Text("Cerrar")}})
}

@Composable internal fun StartDayDialog(vm: WorkflowViewModel, close: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var times by rememberSaveable { mutableStateOf(emptyMap<String,String>()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf(false) }
    val defaults=vm.repository.agendaAccess.defaultTime()
    val blockers=DayControl.blockers(vm.history,vm.day)
    SafeAlertDialog(onDismissRequest={if(!vm.busy)close()},title={Text("Iniciar jornada")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(friendlyDay(vm.day),style=MaterialTheme.typography.titleMedium)
            Text("Marca las actividades de esta jornada. Se añadirán a tu agenda y podrás crear otras después.")
            DayTemplates.all.forEach { template -> Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(template.key in selected,{checked->selected=if(checked)selected+template.key else selected-template.key},enabled=!vm.busy)
                Text(template.title,Modifier.weight(1f))
            } }
            if(selected.isNotEmpty())TextButton(onClick={hours=!hours}){Text(if(hours)"Ocultar horarios opcionales" else "Añadir horarios opcionales")}
            if(hours)DayTemplates.all.filter{it.key in selected}.forEach { task ->
                OutlinedTextField(times[task.key]?:defaults,{times=times+(task.key to it)},label={Text("${task.title} · HH:mm (opcional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            }
            Text("Las horas y las alarmas son opcionales. Puedes iniciar la jornada sin configurarlas.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={settings=true}){Text("Alarmas opcionales")}
            if(blockers.isNotEmpty())Text("Primero termina las tareas y cierra las jornadas anteriores.",color=Brand.Red)
            Text("${selected.size} tareas seleccionadas",color=Brand.SecondaryText)
            vm.actionError?.let{Text(it,color=Brand.Red)}
        }
    },confirmButton={TextButton(onClick={vm.startDay(selected, times + (DayTemplates.all.filter{it.key in selected}.associate{it.key to (times[it.key]?:defaults)}),close)},enabled=!vm.busy&&blockers.isEmpty(),modifier=Modifier.testTag("start-day-confirm")){Text("Iniciar jornada")}},dismissButton={TextButton(onClick=close,enabled=!vm.busy){Text("Cancelar")}})
    if(settings)AgendaSettingsDialog(vm){settings=false}
}
