package com.workflow.core

import java.time.LocalDate
import java.time.DayOfWeek

/** RFC 4180 tables, UTF-8 BOM for Excel, invariant decimals and safe text cells. */
data class CsvTable(val name: String, val headers: List<String>, val rows: List<List<String>>) {
    fun encode(): ByteArray {
        fun cell(raw: String): String {
            val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || raw.startsWith('\t') || raw.startsWith('\r')) "'$raw" else raw
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        return ("\uFEFF" + (listOf(headers) + rows).joinToString("\r\n", postfix="\r\n") { it.joinToString(",", transform=::cell) }).toByteArray(Charsets.UTF_8)
    }
}
object ReportCsv {
    fun build(query: ReportQuery, documents: List<DailyDocument>): List<CsvTable> {
        query.validate()
        val docs=documents.sortedBy { it.day }
        val people=docs.flatMap { it.operators }.associateBy { it.id }
        val assets=docs.flatMap { it.assets }.associateBy { it.id }
        if(query.type==ReportType.OPERATORS) return listOf(CsvTable("Personal",listOf("ID","Codigo","Nombre","Turno","Activo"),people.values.filter { query.operatorId==null || it.id==query.operatorId }.map { listOf(it.id,it.code,it.name,it.shift,it.active.toString()) }))
        if(query.type==ReportType.ASSETS) return listOf(CsvTable("Equipos",listOf("ID","Codigo","Tipo","Estado"),assets.values.filter { query.assetId==null || it.id==query.assetId }.filter { query.status==null || it.state==query.status }.map { listOf(it.id,it.code,it.type,it.state) }))
        val allowed=when(query.type) {
            ReportType.GENERAL,ReportType.CLOSE -> Module.entries.filter { it!=Module.STOCK }
            ReportType.ATTENDANCE,ReportType.ABSENCE,ReportType.SUNDAY -> listOf(Module.ATTENDANCE)
            ReportType.DAYS_OFF -> listOf(Module.ATTENDANCE,Module.PLAN)
            ReportType.DINNER,ReportType.LUNCH -> listOf(Module.MEAL)
            ReportType.FUEL -> listOf(Module.FUEL)
            ReportType.WORKSHOP,ReportType.ASSET_HISTORY -> listOf(Module.FAULT)
            ReportType.TICKETS,ReportType.OPERATOR_TICKETS,ReportType.TICKET -> listOf(Module.TICKET)
            ReportType.WEEK,ReportType.MONTH -> listOf(Module.TASK,Module.PLAN)
            ReportType.PLANNING -> listOf(Module.PLAN)
            ReportType.POSITIONS -> listOf(Module.POSITION)
            ReportType.EVENT -> Module.entries.filter { it!=Module.STOCK }
            else -> emptyList()
        }
        val operations=AttendanceList.normalized(docs.flatMap { it.operations+it.carryOverFaults }.associateBy { it.id }.values.toList())
        val rows=operations.filter { op ->
            val overlap=op.module==Module.FAULT && query.type in listOf(ReportType.GENERAL,ReportType.CLOSE,ReportType.WORKSHOP,ReportType.ASSET_HISTORY) && op.day<=query.to && (op.status!="Cerrada" || (op.resolvedAt.isNotBlank() && java.time.Instant.parse(op.resolvedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()>=query.from))
            (op.day in query.from..query.to || overlap) && op.module in allowed &&
                (query.operatorId==null || query.operatorId==op.operatorId || query.operatorId in op.operatorIds) &&
                (query.assetId==null || query.assetId==op.assetId) && (query.operationId==null || query.operationId==op.id) &&
                (query.status==null || query.status==op.status || (query.status=="Sin resolver" && op.module==Module.FAULT && op.status!="Cerrada")) &&
                when(query.type) {
                    ReportType.ABSENCE -> op.status.startsWith("Ausencia")
                    ReportType.SUNDAY -> LocalDate.parse(op.day).dayOfWeek==DayOfWeek.SUNDAY && op.status in listOf("Presente","Tardanza")
                    ReportType.DAYS_OFF -> op.status.startsWith("Libre")
                    ReportType.DINNER -> op.mealType=="Cena"
                    ReportType.LUNCH -> op.mealType=="Almuerzo"
                    else -> true
                }
        }.sortedWith(compareBy({it.day},{it.time},{it.id}))
        val tables=allowed.mapNotNull { module ->
            val selected=rows.filter { it.module==module }
            if(selected.isEmpty())null else CsvTable(module.name,listOf("ID","Fecha","Hora","Modulo","Titulo","Estado","Operador_ID","Operador","Equipo_ID","Equipo","Tipo_comida","Cantidad","Extendida","Notas","Tarea_modulo","Registro_origen","Actualizado","Motivo_nivel","Esperado","Real","Responsable","Resolucion"),selected.flatMap { op ->
                val ids=if(op.module==Module.MEAL)op.operatorIds.filter { query.operatorId==null || it==query.operatorId }.map { it as String? } else listOf(op.operatorId)
                ids.map { id -> listOf(op.id,op.day,op.time,module.label,op.title,op.status,id.orEmpty(),people[id]?.name.orEmpty(),op.assetId.orEmpty(),assets[op.assetId]?.code.orEmpty(),if(module==Module.MEAL)op.mealType else "",if(module==Module.FUEL)op.quantity.toString() else "",op.extended.toString(),op.notes,if(module==Module.TASK)TaskRouting.label(TaskRouting.destination(op,operations))else "",op.sourceId.orEmpty(),op.updatedAt,if(module==Module.TICKET)op.severity else "",op.expected,op.actual,op.recordedBy,op.resolvedAt) }
            })
        }
        return tables.ifEmpty { listOf(CsvTable("Sin_registros",listOf("Fecha_inicio","Fecha_fin","Consulta","Resultado"),listOf(listOf(query.from,query.to,query.type.label,"Sin registros")))) }
    }
}
