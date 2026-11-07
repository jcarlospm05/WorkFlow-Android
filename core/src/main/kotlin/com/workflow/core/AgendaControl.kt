package com.workflow.core

import java.time.*

data class UnfinishedDay(val day: String, val closed: Boolean, val tasks: List<Operation>)
object DayControl {
    fun isExecution(op: Operation): Boolean = when (op.module) {
        Module.TASK -> op.status == "Completada"
        Module.PLAN -> op.status == "Libre disfrutado"
        Module.FUEL -> op.status == "Abastecido"
        Module.MEAL -> op.status == "Solicitada"
        Module.TICKET -> op.status == "Emitido"
        Module.CLOSE -> false
        else -> true
    }
    fun started(doc: DailyDocument): Boolean = doc.startedAt.isNotBlank() || doc.operations.any { isExecution(it) || it.module == Module.CLOSE }
    fun closed(doc: DailyDocument): Boolean = doc.operations.any { it.module == Module.CLOSE && it.status == "Cerrada" }
    fun blockers(documents: List<DailyDocument>, target: String): List<UnfinishedDay> {
        Rules.day(target)
        return documents.filter { it.day < target && started(it) }.map {
            UnfinishedDay(it.day, closed(it), it.operations.filter { op -> op.module == Module.TASK && op.status != "Completada" })
        }.filter { !it.closed || it.tasks.isNotEmpty() }.sortedBy { it.day }
    }
    fun requirePreviousFinished(documents: List<DailyDocument>, target: String) {
        val pending = blockers(documents, target)
        require(pending.isEmpty()) { "Antes de iniciar el $target, termina las tareas y cierra estas jornadas: " + pending.joinToString { "${it.day} (${it.tasks.size} tareas pendientes${if (!it.closed) ", día abierto" else ""})" } }
    }
}

object AgendaTime {
    fun valid(value: String): Boolean = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(value)
    fun requireTime(value: String) { require(valid(value)) { "Indica una hora válida para la alarma (HH:mm)" } }
}
data class AgendaAlarm(val key: String, val day: String, val title: String, val at: Instant, val taskId: String? = null)
object AgendaAlarms {
    /** Dates use the phone's zone; the 19:00 check rolls forward until the day is closed. */
    fun plan(documents: List<DailyDocument>, now: Instant, zone: ZoneId): List<AgendaAlarm> {
        val result = mutableListOf<AgendaAlarm>()
        documents.forEach { doc -> doc.operations.filter { it.module == Module.TASK && it.status == "Pendiente" && AgendaTime.valid(it.time) }.forEach { task ->
            result += AgendaAlarm("task|${task.id}|${task.day}|${task.time}", task.day, task.title, LocalDate.parse(task.day).atTime(LocalTime.parse(task.time)).atZone(zone).toInstant(), task.id)
        } }
        val open = documents.filter { DayControl.started(it) && !DayControl.closed(it) }.sortedBy { it.day }
        if (open.isNotEmpty()) {
            val first = open.first()
            val date = maxOf(LocalDate.parse(first.day), now.atZone(zone).toLocalDate())
            listOf(date, date.plusDays(1)).forEach { alarmDate ->
                result += AgendaAlarm("close|$alarmDate", first.day, "Cierre del día pendiente", alarmDate.atTime(19, 0).atZone(zone).toInstant())
            }
        }
        return result
    }
}
