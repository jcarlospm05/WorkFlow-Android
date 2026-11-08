package com.workflow.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Serializable enum class ReportType(val label: String) {
    GENERAL("Progreso del día"), CLOSE("Cierre del día"), WEEK("Agenda semanal"), MONTH("Agenda mensual"),
    ATTENDANCE("Asistencia"), ABSENCE("Ausencias"), SUNDAY("Domingos trabajados"), DAYS_OFF("Días libres y compensatorios"),
    DINNER("Solicitud de cena"), LUNCH("Almuerzos"), FUEL("Control de combustible"), WORKSHOP("Averías y equipos en taller"),
    ASSET_HISTORY("Historial de taller por equipo"), TICKETS("Infracciones del día"), OPERATOR_TICKETS("Infracciones por operador"),
    TICKET("Ticket de infracción"), STOCK("Existencias y movimientos"), PLANNING("Programación del personal"),
    POSITIONS("Posiciones y asignaciones"), EVENT("Registro individual"), OPERATORS("Listado de operadores"), ASSETS("Listado de equipos");
    companion object {
        fun forModule(module: Module) = when (module) {
            Module.ATTENDANCE -> ATTENDANCE; Module.MEAL -> DINNER; Module.FUEL -> FUEL; Module.FAULT -> WORKSHOP
            Module.TICKET -> TICKETS; Module.PLAN -> PLANNING; Module.TASK -> WEEK; Module.STOCK -> STOCK
            Module.POSITION -> POSITIONS; Module.CLOSE -> CLOSE
        }
    }
}

@Serializable data class ReportQuery(
    val type: ReportType, val from: String, val to: String = from,
    val operatorId: String? = null, val assetId: String? = null, val operationId: String? = null,
    val status: String? = null
) {
    fun validate() {
        Rules.day(from); Rules.day(to)
        require(from <= to) { "La fecha inicial debe ser anterior o igual a la final" }
        require(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) <= 3660) { "Selecciona un intervalo de hasta diez años" }
        operatorId?.let(Rules::id); assetId?.let(Rules::id); operationId?.let(Rules::id)
    }
}
data class ReportMetric(val value: String, val label: String, val tone: String = "sky")
data class ChartValue(val label: String, val value: Double, val tone: String = "teal")
data class ReportCalendarDay(val day: Int, val total: Int, val completed: Int)
data class ReportCalendar(val year: Int, val month: Int, val days: List<ReportCalendarDay>)
data class ReportSection(
    val title: String, val headers: List<String> = emptyList(), val rows: List<List<String>> = emptyList(),
    val note: String = "", val chart: List<ChartValue> = emptyList(), val donut: Boolean = false,
    val calendar: ReportCalendar? = null, val centerLabel: String = ""
)
data class ReportBanner(val label: String, val value: String, val status: String)
data class VisualReport(
    val query: ReportQuery, val title: String, val period: String, val subtitle: String,
    val metrics: List<ReportMetric>, val sections: List<ReportSection>, val compact: Boolean = false, val banner: ReportBanner? = null,
    val isEmpty: Boolean = false
) {
    fun text(): String = buildString {
        appendLine("*WorkFlow · $title*"); appendLine(period); if (subtitle.isNotBlank()) appendLine(subtitle)
        metrics.forEach { appendLine("${it.label}: ${it.value}") }
        sections.forEach { s ->
            appendLine(); appendLine("*${s.title}*")
            s.rows.forEachIndexed { index, row ->
                appendLine("${index + 1}. " + row.mapIndexed { col, value ->
                    if (s.headers.size > 1) "${s.headers.getOrElse(col) { "Detalle" }}: $value" else value
                }.joinToString(" · "))
            }
            if (s.rows.isEmpty()) s.chart.forEach { appendLine("${it.label}: ${ReportBuilder.number(it.value)}") }
            if (s.note.isNotBlank()) appendLine(s.note)
        }
    }.trim()
}

/** Data only. PDF, JPG, previews and messages all consume this same filtered result. */
object ReportBuilder {
    private val es = Locale.forLanguageTag("es")
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", es)
    fun date(value: String) = LocalDate.parse(value).format(dateFormat)
    fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(es, "%.2f", value)
    fun ticketReference(op: Operation) = "WF-${op.day.replace("-", "")}-${op.id.take(6).uppercase(Locale.ROOT)}"
    fun build(query: ReportQuery, documents: List<DailyDocument>): VisualReport {
        query.validate()
        val docs = documents.sortedBy { it.day }
        val inRange = docs.filter { it.day in query.from..query.to }
        val operators = docs.flatMap { it.operators }.associateBy { it.id }
        val assets = docs.flatMap { it.assets }.associateBy { it.id }
        val items = docs.flatMap { it.items }.associateBy { it.id }
        val all = docs.flatMap { it.operations + it.carryOverFaults }.associateBy { it.id }.values.toList()
        val rows = all.filter { op -> op.day in query.from..query.to || (query.type in listOf(ReportType.GENERAL, ReportType.CLOSE, ReportType.WORKSHOP, ReportType.ASSET_HISTORY) && op.module == Module.FAULT && op.day <= query.to && (op.status != "Cerrada" || (op.resolvedAt.isNotBlank() && java.time.Instant.parse(op.resolvedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() >= query.from))) }
            .filter { query.operatorId == null || it.operatorId == query.operatorId || query.operatorId in it.operatorIds }
            .filter { query.assetId == null || it.assetId == query.assetId }
            .filter { query.operationId == null || it.id == query.operationId }
            .filter { query.status == null || it.status == query.status || (query.status == "Sin resolver" && it.module == Module.FAULT && it.status != "Cerrada") }
            .sortedWith(compareBy({ it.day }, { it.time }, { it.title }))
        fun operator(id: String?) = operators[id]?.name ?: "Sin asignar"
        fun asset(id: String?) = assets[id]?.code ?: "Sin asignar"
        fun mealNames(op: Operation) = op.operatorIds.filter { query.operatorId == null || it == query.operatorId }.map(::operator)
        fun recordTable(title: String, records: List<Operation>) = ReportSection(title,
            listOf("Fecha / hora", "Actividad", "Operador / equipo", "Estado / detalles"), records.map {
                listOf("${date(it.day)}\n${it.time}", it.title,
                    listOfNotNull(it.operatorId?.let(::operator), it.assetId?.let(::asset)).joinToString(" · ").ifBlank { "—" },
                    listOf(it.status, it.notes).filter(String::isNotBlank).joinToString("\n"))
            }, note = if (records.isEmpty()) "Sin registros para los filtros seleccionados." else "")
        val metrics = mutableListOf<ReportMetric>(); val sections = mutableListOf<ReportSection>()
        val period = if (query.from == query.to) date(query.from) else "${date(query.from)} – ${date(query.to)}"
        val filters = listOfNotNull(query.operatorId?.let { "Operador: ${operator(it)}" }, query.assetId?.let { "Equipo: ${asset(it)}" }, query.status)
        val supervisor = inRange.lastOrNull()?.supervisor.orEmpty()
        val updated = inRange.lastOrNull()?.generatedAt?.let { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", es).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(it)) }
        var subtitle = (filters + listOfNotNull(supervisor.takeIf(String::isNotBlank), updated?.let { "Actualizado $it" })).joinToString(" · ").ifBlank { "Todos los registros del período" }
        var title = query.type.label
        var banner: ReportBanner? = null
        when (query.type) {
            ReportType.GENERAL, ReportType.CLOSE -> {
                val attendance = rows.filter { it.module == Module.ATTENDANCE }
                val present = attendance.filter { it.status in listOf("Presente", "Tardanza") }.mapNotNull { it.operatorId }.distinct().size
                val tasks = rows.filter { it.module == Module.TASK }; val done = tasks.count { it.status == "Completada" }
                val latest = inRange.lastOrNull()
                val scopedAssets = latest?.assets.orEmpty()
                    .filter { query.assetId == null || it.id == query.assetId }
                    .filter { asset -> query.operatorId == null || rows.any { it.assetId == asset.id } }
                metrics += listOf(ReportMetric("$present", "Presentes únicos", "sage"), ReportMetric("$done/${tasks.size}", "Tareas completadas"),
                    ReportMetric("${scopedAssets.count { it.state in listOf("En taller", "Inactivo temporal") }}", "Equipos en taller", "sand"),
                    ReportMetric("${rows.count { it.module == Module.TICKET }}", "Tickets registrados"))
                if (tasks.isNotEmpty()) sections += ReportSection("Avance de la jornada", chart = listOf(ChartValue("Completadas", done.toDouble()), ChartValue("Pendientes", (tasks.size-done).toDouble(), "amber")), donut = true, centerLabel = "${(100.0*done/tasks.size).roundToInt()}%")
                val supplied=rows.filter{it.module==Module.FUEL&&it.status=="Abastecido"}
                val requested=rows.filter{it.module==Module.MEAL&&it.status=="Solicitada"}
                sections += ReportSection("Balance operativo",listOf("Indicador","Resultado"),listOf(
                    listOf("Abastecimientos realizados","${supplied.size}"),
                    listOf("Litros confirmados",number(supplied.sumOf{it.quantity})),
                    listOf("Servicios de cena solicitados","${requested.filter{it.mealType=="Cena"}.sumOf{mealNames(it).size}}"),
                    listOf("Servicios de almuerzo solicitados","${requested.filter{it.mealType=="Almuerzo"}.sumOf{mealNames(it).size}}"),
                    listOf("Tickets emitidos","${rows.count{it.module==Module.TICKET&&it.status=="Emitido"}}")
                ),note="Se cuentan los registros guardados para estas fechas y filtros. Las cantidades previstas no se suman a los litros confirmados.")
                sections += ReportSection("Pendientes que requieren atención",listOf("Indicador","Resultado"),listOf(
                    listOf("Tareas por completar","${tasks.count{it.status!="Completada"}}"),
                    listOf("Abastecimientos pendientes","${rows.count{it.module==Module.FUEL&&it.status=="Pendiente"}}"),
                    listOf("Equipos con averías sin resolver","${rows.filter{it.module==Module.FAULT&&it.status!="Cerrada"}.mapNotNull{it.assetId}.distinct().size}")
                ))
                sections += ReportSection("Estado por módulo", listOf("Módulo", "Registros", "Pendientes"), Module.entries.filter { it != Module.CLOSE }.map { module ->
                    val data = rows.filter { it.module == module }
                    listOf(module.label, "${data.size}", "${data.count { it.status in listOf("Pendiente", "Abierta", "En proceso", "Borrador", "Preparada") }}")
                })
                sections += recordTable("Actividades y tareas del período", tasks)
                if (query.type == ReportType.CLOSE) {
                    val closes = rows.filter { it.module == Module.CLOSE }
                    sections += recordTable("Cierres y excepciones", closes)
                    sections += recordTable("Pendientes que permanecen abiertos", rows.filter { it.status in listOf("Pendiente", "Abierta", "En proceso", "Borrador") })
                } else sections += recordTable("Novedades de la operación", rows.filter { it.module !in listOf(Module.TASK, Module.CLOSE) })
            }
            ReportType.WEEK, ReportType.MONTH -> {
                val representedPlans = rows.filter { it.module == Module.TASK }.mapNotNull { it.sourceId }.toSet()
                val tasks = rows.filter { it.module == Module.TASK || (it.module == Module.PLAN && it.id !in representedPlans) }
                metrics += listOf(ReportMetric("${tasks.size}", "Actividades programadas"), ReportMetric("${tasks.count { it.status == "Completada" }}", "Completadas", "sage"),
                    ReportMetric("${tasks.count { it.status !in listOf("Completada", "Libre disfrutado", "Libre confirmado") }}", "Pendientes / previstas", "sand"))
                if (query.type == ReportType.MONTH) {
                    var month = YearMonth.from(LocalDate.parse(query.from)); val end = YearMonth.from(LocalDate.parse(query.to))
                    while (month <= end) {
                        val current = month
                        val monthTasks = tasks.filter { YearMonth.from(LocalDate.parse(it.day)) == current }
                        val days = monthTasks.groupBy { LocalDate.parse(it.day).dayOfMonth }.map { (day, records) -> ReportCalendarDay(day, records.size, records.count { it.status == "Completada" }) }
                        sections += ReportSection(current.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", es)).replaceFirstChar { it.uppercase() }, calendar = ReportCalendar(current.year, current.monthValue, days))
                        month = month.plusMonths(1)
                    }
                } else sections += ReportSection("Actividades por fecha", chart = tasks.groupBy { it.day }.map { ChartValue(date(it.key), it.value.size.toDouble()) })
                sections += recordTable("Agenda y seguimiento", tasks)
            }
            ReportType.ATTENDANCE, ReportType.ABSENCE, ReportType.SUNDAY -> {
                val attendance = rows.filter { it.module == Module.ATTENDANCE }
                val selected = attendance.filter { when (query.type) {
                    ReportType.ABSENCE -> it.status.startsWith("Ausencia")
                    ReportType.SUNDAY -> LocalDate.parse(it.day).dayOfWeek == DayOfWeek.SUNDAY && it.status in listOf("Presente", "Tardanza")
                    else -> true
                } }
                metrics += listOf(ReportMetric("${selected.map { it.operatorId }.distinct().size}", "Personas únicas", "sage"), ReportMetric("${selected.size}", "Registros"), ReportMetric("${selected.map { it.day }.distinct().size}", "Días registrados", "sand"))
                if (selected.isNotEmpty()) sections += ReportSection("Distribución de asistencia", chart = selected.groupBy { it.status }.map { ChartValue(it.key, it.value.size.toDouble(), statusTone(it.key)) }, donut = true)
                sections += ReportSection("Detalle de asistencia", listOf("Fecha", "Operador", "Turno / hora", "Estado"), selected.map {
                    listOf(date(it.day), operator(it.operatorId), "${it.shift}${if (it.extended) " · EXT" else ""}\n${it.time}", it.status)
                }, note = "Tardanza cuenta como presencia. Varias jornadas o turnos no duplican personas únicas. Libre no cuenta como ausencia.")
                if (query.type == ReportType.SUNDAY) sections += ReportSection("Domingos por operador", listOf("Operador", "Domingos", "Fechas"), selected.groupBy { it.operatorId }.map { (id, records) ->
                    val days = records.map { it.day }.distinct(); listOf(operator(id), "${days.size}", days.joinToString(", ", transform = ::date))
                })
            }
            ReportType.DAYS_OFF -> {
                val free = rows.filter { (it.module == Module.PLAN && it.status.startsWith("Libre")) || (it.module == Module.ATTENDANCE && it.status == "Libre") }
                    .groupBy { "${it.day}|${it.operatorId}" }.values.map { records -> records.firstOrNull { it.status == "Libre disfrutado" } ?: records.firstOrNull { it.module == Module.ATTENDANCE } ?: records.last() }
                val enjoyed = free.count { it.status in listOf("Libre", "Libre disfrutado") }
                metrics += listOf(ReportMetric("${free.size}", "Días libres registrados"), ReportMetric("$enjoyed", "Disfrutados", "sage"), ReportMetric("${free.size-enjoyed}", "Previstos / confirmados", "sand"))
                sections += recordTable("Días libres por persona y fecha", free)
                sections += ReportSection("Seguimiento por operador", listOf("Operador", "Registrados", "Disfrutados", "Pendientes"), free.groupBy { it.operatorId }.map { (id, records) ->
                    val used = records.count { it.status in listOf("Libre", "Libre disfrutado") }; listOf(operator(id), "${records.size}", "$used", "${records.size-used}")
                }, note = "Se muestran únicamente días libres registrados. No se presume una equivalencia automática entre domingos y compensatorios.")
            }
            ReportType.DINNER, ReportType.LUNCH -> {
                val type = if (query.type == ReportType.DINNER) "Cena" else "Almuerzo"
                val meals = rows.filter { it.module == Module.MEAL && it.mealType == type }
                metrics += listOf(ReportMetric("${meals.sumOf { mealNames(it).size }}", "Servicios solicitados", "sand"), ReportMetric("${meals.flatMap { mealNames(it) }.distinct().size}", "Personas únicas", "sage"), ReportMetric("${meals.size}", "Solicitudes"))
                meals.forEach { meal -> sections += ReportSection("$type · ${date(meal.day)}", listOf("N.º", "Operador"), mealNames(meal).mapIndexed { i, name -> listOf("${i+1}", name) }, note = "${meal.status} · ${mealNames(meal).size} personas${if (meal.notes.isBlank()) "" else "\n${meal.notes}"}") }
                if (meals.isEmpty()) sections += ReportSection("Listado de $type", note = "No hay solicitudes para las fechas y filtros seleccionados.")
            }
            ReportType.FUEL -> {
                val fuel = rows.filter { it.module == Module.FUEL }
                val confirmed = fuel.filter { it.status == "Abastecido" }; val pending = fuel.filter { it.status == "Pendiente" }
                metrics += listOf(ReportMetric("${confirmed.size}", "Abastecimientos", "sage"), ReportMetric("${pending.size}", "Pendientes", "sand"), ReportMetric(number(confirmed.sumOf { it.quantity }), "Litros confirmados"))
                if (fuel.isNotEmpty()) sections += ReportSection("Estado de abastecimientos", chart = fuel.groupBy { it.status }.map { ChartValue(it.key, it.value.size.toDouble(), statusTone(it.key)) }, donut = true)
                sections += ReportSection("Detalle de combustible", listOf("Fecha", "Equipo / operador", "Litros", "Estado / notas"), fuel.map { listOf(date(it.day), "${asset(it.assetId)}\n${operator(it.operatorId)}", number(it.quantity), listOf(it.status, it.notes).filter(String::isNotBlank).joinToString("\n")) })
                sections += ReportSection("Equipos pendientes de combustible", listOf("Equipo", "Cantidad prevista", "Fecha"), pending.map { listOf(asset(it.assetId), "${number(it.quantity)} L", date(it.day)) }, note = if (pending.isEmpty()) "No hay abastecimientos pendientes en este período." else "Las cantidades pendientes son previstas; no se suman a los litros confirmados.")
            }
            ReportType.WORKSHOP, ReportType.ASSET_HISTORY -> {
                val faults = rows.filter { it.module == Module.FAULT }
                metrics += listOf(ReportMetric("${faults.size}", "Averías registradas", "sand"), ReportMetric("${faults.map { it.assetId }.distinct().size}", "Equipos afectados"), ReportMetric("${faults.count { it.status != "Cerrada" }}", "Pendientes de resolver", "rose"))
                if (faults.isNotEmpty()) sections += ReportSection("Intervenciones por equipo", chart = faults.groupBy { it.assetId }.map { ChartValue(asset(it.key), it.value.size.toDouble(), "amber") })
                sections += ReportSection("Historial de averías y paradas", listOf("Fecha / equipo", "Avería", "Operador", "Estado / resolución"), faults.map {
                    listOf("${date(it.day)}\n${asset(it.assetId)} · ${it.time}", "${it.title}\n${it.notes}", operator(it.operatorId), "${it.status}${if (it.resolvedAt.isNotBlank()) "\nResolución: ${it.resolvedAt}" else "\nInactivo temporalmente"}")
                })
            }
            ReportType.TICKETS, ReportType.OPERATOR_TICKETS -> {
                val tickets = rows.filter { it.module == Module.TICKET }
                metrics += listOf(ReportMetric("${tickets.size}", "Tickets registrados"), ReportMetric("${tickets.count { it.status == "Emitido" }}", "Emitidos", "sand"), ReportMetric("${tickets.map { it.operatorId }.distinct().size}", "Operadores"))
                if (tickets.isNotEmpty()) sections += ReportSection("Infracciones por operador", chart = tickets.groupBy { it.operatorId }.map { ChartValue(operator(it.key), it.value.size.toDouble(), "amber") })
                sections += ReportSection("Detalle de infracciones", listOf("Ticket / fecha", "Operador", "Motivo / nivel", "Estado / detalles"), tickets.map {
                    listOf("${ticketReference(it)}\n${date(it.day)}", operator(it.operatorId), "${it.title}\n${it.severity}", "${it.status}\n${it.notes}")
                }, note = "Los borradores y anulados se identifican por separado; no se consideran tickets emitidos.")
            }
            ReportType.TICKET, ReportType.EVENT -> {
                val op = rows.firstOrNull()
                if (op == null) sections += ReportSection("Registro", note = "Selecciona un registro individual para generar este documento.")
                else {
                    title = if (query.type == ReportType.TICKET) "Ticket de infracción" else op.title
                    subtitle = if (op.module == Module.TICKET) "Departamento de operaciones · ${ticketReference(op)}" else "Registro individual · ${op.module.label}"
                    if (query.type == ReportType.TICKET) banner = ReportBanner("OPERADOR", operator(op.operatorId), op.status)
                    else metrics += ReportMetric(op.status, "Estado", statusTone(op.status))
                    val fields = mutableListOf(listOf("Fecha y hora", "${date(op.day)} · ${op.time.ifBlank { "Sin hora registrada" }}"), listOf("Operador", operator(op.operatorId)))
                    op.assetId?.let { fields += listOf("Equipo", asset(it)) }
                    fields += listOf("Motivo / actividad", op.title)
                    if (op.module == Module.TICKET) {
                        fields += listOf("Nivel", op.severity)
                        fields += listOf("Supervisor", op.recordedBy.ifBlank { inRange.lastOrNull()?.supervisor.orEmpty() })
                        val repeats = all.count { it.module == Module.TICKET && it.id != op.id && it.operatorId == op.operatorId && it.status == "Emitido" && it.title.trim().equals(op.title.trim(), true) && (it.day < op.day || (it.day == op.day && it.updatedAt < op.updatedAt)) }
                        fields += listOf("Reincidencias", "$repeats anteriores para el mismo motivo")
                        fields += listOf("Objetivo / real", "${op.expected.ifBlank { "Sin registrar" }} / ${op.actual.ifBlank { "Sin registrar" }}")
                    }
                    if (op.quantity > 0) fields += listOf("Cantidad", "${number(op.quantity)} ${items[op.itemId]?.unit ?: if (op.module == Module.FUEL) "L" else ""}")
                    if (op.module == Module.ATTENDANCE || op.module == Module.PLAN) fields += listOf("Turno", "${op.shift}${if (op.extended) " · EXT" else ""}")
                    fields += listOf("Estado", op.status)
                    sections += ReportSection("Datos del registro", listOf("Campo", "Detalle"), fields)
                    if (op.module == Module.MEAL) sections += ReportSection("${op.mealType} · ${mealNames(op).size} personas", listOf("Operador"), mealNames(op).map { listOf(it) })
                    sections += ReportSection("Descripción y seguimiento", note = op.notes.ifBlank { "Sin observaciones adicionales." })
                }
            }
            ReportType.STOCK -> {
                val latest = docs.lastOrNull { it.day <= query.to }
                val stockItems = latest?.items.orEmpty(); val balances = latest?.stockBalances.orEmpty()
                metrics += listOf(ReportMetric("${stockItems.size}", "Artículos registrados"), ReportMetric("${stockItems.count { balances.getOrDefault(it.id, 0.0) < it.minimum }}", "Bajo mínimo", "sand"), ReportMetric("${rows.count { it.module == Module.STOCK }}", "Movimientos"))
                sections += ReportSection("Existencias al final del período", listOf("Artículo", "Saldo", "Mínimo", "Estado"), stockItems.map {
                    val balance = balances.getOrDefault(it.id, 0.0)
                    listOf(it.name, "${number(balance)} ${it.unit}", "${number(it.minimum)} ${it.unit}", if (balance < it.minimum) "Bajo mínimo" else "Suficiente")
                })
                val ratio = stockItems.filter { it.minimum > 0 }.map { ChartValue(it.name, 100*balances.getOrDefault(it.id, 0.0)/it.minimum, if (balances.getOrDefault(it.id, 0.0)<it.minimum) "red" else "teal") }
                if (ratio.isNotEmpty()) sections += ReportSection("Saldo respecto al mínimo (%)", chart = ratio, note = "100% equivale al mínimo de cada artículo. No se suman cantidades de unidades distintas.")
                sections += recordTable("Movimientos del período", rows.filter { it.module == Module.STOCK }.map { it.copy(title = "${items[it.itemId]?.name ?: it.title} · ${number(it.quantity)} ${items[it.itemId]?.unit.orEmpty()}") })
            }
            ReportType.PLANNING, ReportType.POSITIONS -> {
                val module = if (query.type == ReportType.PLANNING) Module.PLAN else Module.POSITION
                val selected = rows.filter { it.module == module }
                metrics += listOf(ReportMetric("${selected.size}", "Registros"), ReportMetric("${selected.map { it.operatorId }.distinct().size}", "Personas únicas", "sage"))
                sections += recordTable(query.type.label, selected)
                if (selected.isNotEmpty()) sections += ReportSection("Distribución", chart = selected.groupBy { if (module == Module.PLAN) it.shift else it.status }.map { ChartValue(it.key, it.value.size.toDouble(), "selected") }, donut = true)
            }
            ReportType.OPERATORS -> {
                val selected = (inRange.lastOrNull()?.operators ?: operators.values.toList()).filter { query.operatorId == null || it.id == query.operatorId }.sortedBy { it.name }
                metrics += listOf(ReportMetric("${selected.size}", "Operadores registrados"), ReportMetric("${selected.count { it.active }}", "Activos", "sage"))
                sections += ReportSection("Personal", listOf("Operador", "Código opcional", "Turno", "Estado"), selected.map { listOf(it.name, it.code.ifBlank { "Sin código" }, it.shift, if (it.active) "Activo" else "Inactivo") })
            }
            ReportType.ASSETS -> {
                val selected = (inRange.lastOrNull()?.assets ?: assets.values.toList()).filter { query.assetId == null || it.id == query.assetId }
                metrics += listOf(ReportMetric("${selected.size}", "Equipos registrados"), ReportMetric("${selected.count { it.state == "Activo" }}", "Activos", "sage"), ReportMetric("${selected.count { it.state in listOf("En taller", "Inactivo temporal") }}", "En taller", "rose"))
                if (selected.isNotEmpty()) sections += ReportSection("Disponibilidad de equipos", chart = selected.groupBy { it.state }.map { ChartValue(it.key, it.value.size.toDouble(), statusTone(it.key)) }, donut = true)
                sections += ReportSection("Estado de la flota", listOf("Equipo", "Tipo", "Estado"), selected.map { listOf(it.code, it.type, it.state) })
            }
        }
        val empty=if(query.type in listOf(ReportType.GENERAL,ReportType.CLOSE))rows.isEmpty() else sections.none{it.rows.isNotEmpty()||it.chart.isNotEmpty()||it.calendar?.days?.isNotEmpty()==true}
        if(empty)sections.add(0,ReportSection("Sin registros para esta consulta",note="No se encontraron datos para estas fechas y filtros. Revisa el período o carga las jornadas que falten."))
        return VisualReport(query, title, period, subtitle, metrics, sections, compact = query.type in listOf(ReportType.TICKET, ReportType.EVENT), banner = banner,isEmpty=empty)
    }
    fun statusTone(status: String): String = when {
        status in listOf("Completada", "Presente", "Abastecido", "Cerrada", "Activo", "Emitido", "Libre disfrutado") -> "sage"
        status.contains("Inactivo") || status.contains("injustificada") || status == "Bajo mínimo" -> "rose"
        status in listOf("Pendiente", "Abierta", "En proceso", "Tardanza", "Borrador", "En taller") -> "sand"
        else -> "selected"
    }
}
