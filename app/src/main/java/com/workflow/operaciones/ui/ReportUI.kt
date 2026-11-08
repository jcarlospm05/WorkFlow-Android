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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
    GuidedReportDialog(initial,vm,close,show)
}

@Composable internal fun ListReportActions(query: ReportQuery, documents: List<DailyDocument>, open: (ReportQuery) -> Unit, notify: (String) -> Unit) {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedButton(onClick={open(query)},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp)) { Icon(Icons.Outlined.Assessment,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Reporte",maxLines=1) }
        OutlinedIconButton(onClick={copyReportText(context,ReportBuilder.build(query,documents).text());notify("Listado copiado")},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.ContentCopy,"Copiar listado")}
        OutlinedIconButton(onClick={Reports.shareText(context,ReportBuilder.build(query,documents).text())},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Share,"Compartir listado por WhatsApp")}
    }
}

@Composable internal fun VisualReportViewer(query: ReportQuery, documents: List<DailyDocument>, close: () -> Unit) {
    VisualReportViewer(query,documents,edit=null,close=close)
}

@Composable internal fun VisualReportViewer(query: ReportQuery, documents: List<DailyDocument>, edit: ((ReportQuery)->Unit)?, close: () -> Unit) {
    val context=LocalContext.current
    val report=remember(query,documents){ReportBuilder.build(query,documents)}
    var pdf by remember(report){mutableStateOf<File?>(null)}
    var images by remember(report){mutableStateOf<List<File>>(emptyList())}
    var error by remember(report){mutableStateOf<String?>(null)}
    var sharing by rememberSaveable { mutableStateOf(false) }
    var otherApps by rememberSaveable { mutableStateOf(false) }
    var reading by remember(report){mutableStateOf(!report.compact)}
    var prepareFiles by remember(report){mutableStateOf(report.compact)}
    var expanded by remember(report){mutableStateOf(emptySet<Int>())}
    var preparing by remember(report){mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun shareFile(asImages:Boolean) {
        if(preparing)return
        preparing=true;error=null
        scope.launch {
            try {
                val readyPdf=pdf ?: withContext(Dispatchers.IO){Reports.pdf(context,report)}
                pdf=readyPdf
                if(asImages) {
                    val readyImages=images.ifEmpty{withContext(Dispatchers.IO){Reports.images(context,readyPdf)}}
                    images=readyImages;Reports.share(context,readyImages,!otherApps)
                } else {
                    val tables=withContext(Dispatchers.IO){Reports.csv(context,query,documents)}
                    Reports.share(context,listOf(readyPdf)+tables,!otherApps)
                }
                sharing=false
            } catch(e:CancellationException){throw e}
            catch(_:Exception){error="No se pudo preparar el archivo. Revisa el espacio disponible e inténtalo de nuevo."}
            finally{preparing=false}
        }
    }
    fun shareCsv() {
        if(preparing)return
        preparing=true;error=null
        scope.launch {
            try { val files=withContext(Dispatchers.IO){Reports.csv(context,query,documents)};Reports.share(context,files,!otherApps);sharing=false }
            catch(e:CancellationException){throw e}
            catch(_:Exception){error="No se pudieron preparar las tablas. Inténtalo de nuevo."}
            finally{preparing=false}
        }
    }
    LaunchedEffect(report,prepareFiles) {
        if(!prepareFiles)return@LaunchedEffect
        preparing=true
        try { val result=withContext(Dispatchers.IO){ val file=Reports.pdf(context,report);file to Reports.images(context,file) };pdf=result.first;images=result.second }
        catch(e:CancellationException){throw e}
        catch(_:Exception){error="No se pudo preparar el documento. Revisa el espacio disponible e inténtalo de nuevo."}
        finally{preparing=false}
    }
    SafeFullscreenDialog(onDismissRequest=close) {
        BrandHeader()
        Text(report.title,style=MaterialTheme.typography.titleLarge)
        Text(report.period,style=MaterialTheme.typography.bodyMedium)
        if(report.subtitle.isNotBlank())Text(report.subtitle,style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
        if(edit!=null&&query.operationId==null)TextButton(onClick={edit(query)},modifier=Modifier.testTag("report-edit-query")){Text("Cambiar fechas o filtros")}
        PillTabs(listOf("Leer resultado","Ver documento"),if(reading)"Leer resultado"else"Ver documento"){reading=it=="Leer resultado";if(!reading)prepareFiles=true}
        if(error!=null) Text(error!!,color=Brand.Red)
        if(!reading&&pdf==null&&error==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).padding(vertical=12.dp).testTag("report-result-list"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(reading)readableReport(report,expanded){index->expanded=if(index in expanded)expanded-index else expanded+index}
            else {
            items(images,key={it.name}) { file ->
                var bitmap by remember(file) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(file) {
                    bitmap=withContext(Dispatchers.IO){BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=2})?.asImageBitmap()}
                }
                bitmap?.let{Image(it,contentDescription="Página de ${report.title}",modifier=Modifier.fillMaxWidth().aspectRatio(it.width.toFloat()/it.height))}
            }
            }
        }
        Button(onClick={sharing=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Compartir",maxLines=1)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=close,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Cerrar",maxLines=1)}
            OutlinedButton(onClick={copyReportText(context,report.text())},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Copiar texto",maxLines=1)}
        }
    }
    if(sharing)SafeAlertDialog(onDismissRequest={sharing=false},title={Text("Compartir reporte")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(report.period)
            if(preparing){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Preparando el archivo…",style=MaterialTheme.typography.bodySmall)}
            error?.let{Text(it,color=Brand.Red)}
            Button(onClick={Reports.shareText(context,report.text(),!otherApps);sharing=false},modifier=Modifier.fillMaxWidth()){Text("Listado de texto")}
            OutlinedButton(onClick={shareFile(true)},enabled=!preparing,modifier=Modifier.fillMaxWidth()){Text(if(images.size>1)"Imágenes JPG · ${images.size} páginas"else "Imagen JPG")}
            OutlinedButton(onClick={shareCsv()},enabled=!preparing,modifier=Modifier.fillMaxWidth()){Text("Tablas CSV para Excel")}
            OutlinedButton(onClick={shareFile(false)},enabled=!preparing,modifier=Modifier.fillMaxWidth()){Text("Informe · PDF y CSV")}
            Row { Checkbox(otherApps,{otherApps=it},enabled=!preparing);Text("Elegir otra aplicación") }
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
