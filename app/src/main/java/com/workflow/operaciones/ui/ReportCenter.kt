package com.workflow.operaciones.ui

import androidx.compose.foundation.clickable
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
    items(topics.chunked(2)) { row ->
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach { topic ->
                WFCard(Modifier.weight(1f).testTag("report-topic-${topic.type.name}").clickable(role=Role.Button){open(ReportGuide.initial(topic.type,day))}.heightIn(min=108.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Assessment,null,tint=Brand.Teal,modifier=Modifier.size(20.dp));Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp)) }
                    Text(topic.title,style=MaterialTheme.typography.titleSmall)
                    Text(topic.description,style=MaterialTheme.typography.bodySmall,color=Brand.SecondaryText)
                }
            }
            if(row.size==1)Spacer(Modifier.weight(1f))
        }
    }
    if(search.isBlank())item{OutlinedButton(onClick=onMore,modifier=Modifier.fillMaxWidth().testTag("report-more-topics")){Text(if(more)"Mostrar consultas frecuentes"else"Ver otras consultas: domingos, libres, historiales…")}}
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
    items(report.metrics.chunked(2)){row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{metric->WFCard(Modifier.weight(1f),if(metric.tone=="sage")Brand.Sage else if(metric.tone=="sand")Brand.Sand else Brand.Sky){Text(metric.value,style=MaterialTheme.typography.headlineMedium);Text(metric.label,style=MaterialTheme.typography.bodySmall)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
    report.sections.forEachIndexed { index,section->
        item(key="heading-$index"){Text(section.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=10.dp))}
        if(section.chart.isNotEmpty())item(key="chart-$index"){WFCard(Modifier.fillMaxWidth()){
            val maximum=section.chart.maxOfOrNull{it.value}?:0.0
            section.chart.forEach{point->Row{Text(point.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(ReportBuilder.number(point.value),style=MaterialTheme.typography.titleSmall)};if(maximum>0)LinearProgressIndicator(progress={(point.value/maximum).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=if(point.tone in listOf("rose","red"))Brand.Red else if(point.tone=="amber")Brand.Amber else Brand.Teal)}
        }}
        section.calendar?.let{cal->item(key="calendar-$index"){WFCard(Modifier.fillMaxWidth()){if(cal.days.isEmpty())Text("Sin actividades registradas en este mes.")else cal.days.sortedBy{it.day}.forEach{day->Text("Día ${day.day} · ${day.completed} de ${day.total} actividades completadas")}}}}
        if(section.headers==listOf("Indicador","Resultado"))item(key="balance-$index"){WFCard(Modifier.fillMaxWidth()){section.rows.forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(row.firstOrNull().orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(row.getOrElse(1){""},style=MaterialTheme.typography.titleSmall)}}}}
        else {
            val shown=if(index in expanded)section.rows else section.rows.take(3)
            items(shown.size,key={"row-$index-$it"}){n->val row=shown[n];WFCard(Modifier.fillMaxWidth()){row.forEachIndexed{col,value->if(value.isNotBlank()){Text(section.headers.getOrElse(col){"Detalle"},style=MaterialTheme.typography.labelSmall,color=Brand.SecondaryText);Text(value,style=MaterialTheme.typography.bodyMedium)}}}}
            if(section.rows.size>3)item(key="expand-$index"){TextButton(onClick={expand(index)},modifier=Modifier.fillMaxWidth()){Text(if(index in expanded)"Ver menos"else"Ver los ${section.rows.size} registros")}}
        }
        if(section.note.isNotBlank())item(key="note-$index"){Text(section.note,style=MaterialTheme.typography.bodyMedium,color=Brand.SecondaryText)}
    }
}
