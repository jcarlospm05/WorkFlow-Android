package com.workflow.operaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.workflow.core.*
import java.time.LocalDate

internal fun LazyListScope.reportCenterRows(day:String,search:String,more:Boolean,onSearch:(String)->Unit,onMore:()->Unit,open:(ReportQuery)->Unit) {
    item { Text("¿Qué necesitas saber?",style=MaterialTheme.typography.titleLarge);Text("Elige una consulta, indica las fechas y lee el resultado.",color=Brand.SecondaryText) }
    item { OutlinedTextField(search,onSearch,label={Text("Buscar: asistencia, cena, taller…")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("report-search")) }
    val topics=ReportGuide.topics.filter{ReportGuide.matches(it,search)&&(more||search.isNotBlank()||it.frequent)}
    if(topics.isEmpty())item{Text("No encontramos esa consulta. Prueba con otra palabra o borra la búsqueda.")}
    item { Row(Modifier.fillMaxWidth().background(Brand.Selected).padding(10.dp)){Text("Consulta",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);Text("Abrir",style=MaterialTheme.typography.labelMedium)} }
    items(topics,key={it.type.name}) { topic ->
        Column(Modifier.fillMaxWidth().background(Color.White).testTag("report-topic-${topic.type.name}").clickable(role=Role.Button){open(ReportGuide.initial(topic.type,day))}) {
            Row(Modifier.padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(topic.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))
            }
            HorizontalDivider(color=Brand.Border)
        }
    }
    if(search.isBlank())item{OutlinedButton(onClick=onMore,modifier=Modifier.fillMaxWidth().testTag("report-more-topics")){Text(if(more)"Mostrar consultas frecuentes"else"Todas las consultas")}}
}

@Composable internal fun GuidedReportDialog(initial:ReportQuery,vm:WorkflowViewModel,close:()->Unit,show:(ReportQuery)->Unit) {
    val base=initial
    var from by rememberSaveable { mutableStateOf(base.from) };var to by rememberSaveable { mutableStateOf(base.to) }
    var operator by rememberSaveable { mutableStateOf(base.operatorId) };var asset by rememberSaveable { mutableStateOf(base.assetId) };var status by rememberSaveable { mutableStateOf(base.status) }
    var filters by rememberSaveable { mutableStateOf(base.operatorId!=null||base.assetId!=null||base.type in listOf(ReportType.ASSET_HISTORY,ReportType.OPERATOR_TICKETS)) }
    var period by rememberSaveable { mutableStateOf(ReportGuide.selectedPeriod(base,vm.day,LocalDate.now().toString())) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val title=ReportGuide.topic(base.type).title
    SafeAlertDialog(onDismissRequest=close,title={Text(title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("¿De qué período?",style=MaterialTheme.typography.titleMedium)
            PillTabs(ReportPeriod.entries.map{it.label},period.label){label->
                period=ReportPeriod.entries.first{it.label==label};error=null
                if(period!=ReportPeriod.CUSTOM){val dates=ReportGuide.bounds(period,vm.day,LocalDate.now().toString());from=dates.first;to=dates.second}
            }
            if(period==ReportPeriod.CUSTOM){WorkDateField("Desde",from,"report-from"){from=it;error=null};WorkDateField("Hasta",to,"report-to"){to=it;error=null}}
            else Text(if(from==to)ReportBuilder.date(from)else"${ReportBuilder.date(from)} — ${ReportBuilder.date(to)}",style=MaterialTheme.typography.bodyMedium)
            val applied=listOf(operator,asset,status).count{it!=null}
            if(!filters&&status!=null)WFStatus("Estado: $status")
            TextButton(onClick={filters=!filters},modifier=Modifier.fillMaxWidth().testTag("report-filters")){Text(if(filters)"Ocultar filtros"else if(applied>0)"Ver filtros · $applied activos"else"Elegir una persona, equipo o estado (opcional)")}
            if(filters){
                if(base.type !in listOf(ReportType.STOCK,ReportType.ASSETS))ReportDropdown("Operador",operator,listOf(null to "Todos los operadores")+vm.operators.map{it.id to it.displayLabel}){operator=it}
                if(base.type in listOf(ReportType.GENERAL,ReportType.CLOSE,ReportType.FUEL,ReportType.WORKSHOP,ReportType.ASSET_HISTORY,ReportType.ASSETS))ReportDropdown("Equipo",asset,listOf(null to "Todos los equipos")+vm.assets.map{it.id to it.code}){asset=it}
                val statuses=when(base.type){ReportType.FUEL->Rules.statuses.getValue(com.workflow.core.Module.FUEL);ReportType.TICKETS,ReportType.OPERATOR_TICKETS->Rules.statuses.getValue(com.workflow.core.Module.TICKET);ReportType.WORKSHOP,ReportType.ASSET_HISTORY->listOf("Sin resolver")+Rules.statuses.getValue(com.workflow.core.Module.FAULT);else->emptyList()}
                if(statuses.isNotEmpty())ReportDropdown("Estado",status,listOf(null to "Todos los estados")+statuses.map{it to it}){status=it}
                if(applied>0)TextButton(onClick={operator=null;asset=null;status=null}){Text("Quitar filtros")}
            }
            val days=vm.history.count{it.day in from..to&&it.operations.isNotEmpty()}
            Text("$days jornadas con registros en estas fechas. El resultado usa la información guardada en este teléfono.",style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
            error?.let{Text(it,color=Brand.Red)}
        }
    },confirmButton={Button(onClick={
        try{val type=when{base.type in listOf(ReportType.WEEK,ReportType.MONTH)&&period==ReportPeriod.MONTH->ReportType.MONTH;base.type in listOf(ReportType.WEEK,ReportType.MONTH)&&period==ReportPeriod.WEEK->ReportType.WEEK;else->base.type};val query=base.copy(type=type,from=from,to=to,operatorId=operator,assetId=asset,status=status);query.validate();show(query);close()}catch(e:Exception){error=e.message?:"Revisa las fechas"}
    },modifier=Modifier.testTag("report-generate")){Text("Ver resultado")}},dismissButton={TextButton(onClick=close){Text("Volver")}})
}

internal fun LazyListScope.readableReport(report:VisualReport,expanded:Set<Int>,expand:(Int)->Unit) {
    if(report.isEmpty)item { WFCard(Modifier.fillMaxWidth(),Brand.Sand){Text("No hay registros para esta consulta",style=MaterialTheme.typography.titleMedium);Text("Prueba otras fechas o quita filtros. Si el trabajo se hizo en otro teléfono, carga primero su jornada.") } }
    report.banner?.let{banner->item{WFCard(Modifier.fillMaxWidth(),Brand.Sky){Text(banner.label,style=MaterialTheme.typography.labelMedium);Text(banner.value,style=MaterialTheme.typography.titleLarge);WFStatus(banner.status)}}}
    if(report.metrics.isNotEmpty())item { Column(Modifier.fillMaxWidth().background(Brand.Selected).padding(10.dp)) {
        report.metrics.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{metric->Text("${metric.label}: ${metric.value}",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)}} }
    } }
    report.sections.forEachIndexed { index,section ->
        item(key="heading-$index"){Text(section.title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=4.dp))}
        if(section.headers.isNotEmpty())item(key="table-$index") {
            val wide=section.headers.size>2
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val tableWidth=if(wide)maxOf(540,section.headers.size*150).dp else 300.dp
                Column(Modifier.width(tableWidth)) {
                    Row(Modifier.fillMaxWidth().background(Brand.Selected)) { section.headers.forEach { header -> Text(header,Modifier.weight(1f).padding(8.dp),style=MaterialTheme.typography.labelMedium) } }
                    section.rows.forEachIndexed { rowIndex,row ->
                        Row(Modifier.fillMaxWidth().background(if(rowIndex%2==0)Color.White else Brand.Background)) { section.headers.indices.forEach { col -> Text(row.getOrElse(col){""},Modifier.weight(1f).padding(8.dp),style=MaterialTheme.typography.bodyMedium) } }
                        HorizontalDivider(color=Brand.Border)
                    }
                    if(section.rows.isEmpty())Text("Sin registros",Modifier.padding(8.dp),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(section.chart.isNotEmpty() && section.headers.isEmpty())item(key="chart-$index") {
            Column(Modifier.fillMaxWidth()) { section.chart.forEach { point -> Row(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Text(point.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(ReportBuilder.number(point.value),style=MaterialTheme.typography.bodyMedium) } } }
        }
        section.calendar?.let { cal -> item(key="calendar-$index") { Text(cal.days.sortedBy{it.day}.joinToString(" · "){"${it.day}: ${it.completed}/${it.total}"},style=MaterialTheme.typography.bodyMedium) } }
        if(section.note.isNotBlank())item(key="note-$index"){Text(section.note,style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)}
    }
}
