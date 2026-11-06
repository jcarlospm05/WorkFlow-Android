package com.workflow.core

import java.util.UUID

enum class ContinuationDecision { LOAD, UPDATE, ALREADY_LOADED, OLDER, CONFLICT }

/** Compare causal versions, never a device's wall clock. Imports do not count as edits. */
object ContinuationPolicy {
    fun identity(d: DailyDocument): String = d.journeyId.ifBlank {
        UUID.nameUUIDFromBytes("${d.originId}|${d.day}".toByteArray(Charsets.UTF_8)).toString()
    }
    fun vector(d: DailyDocument): Map<String, Long> = d.versionVector.ifEmpty { mapOf(d.originId to d.revision) }
    fun decide(local: DailyDocument, incoming: DailyDocument): ContinuationDecision {
        require(local.day == incoming.day)
        if (local.operations.isEmpty() && local.startedAt.isBlank()) return ContinuationDecision.LOAD
        if (identity(local) != identity(incoming)) return ContinuationDecision.CONFLICT
        val a = vector(local); val b = vector(incoming)
        val ids = a.keys + b.keys
        val localAhead = ids.any { a.getOrDefault(it, 0) > b.getOrDefault(it, 0) }
        val incomingAhead = ids.any { b.getOrDefault(it, 0) > a.getOrDefault(it, 0) }
        return when {
            localAhead && incomingAhead -> ContinuationDecision.CONFLICT
            localAhead -> ContinuationDecision.OLDER
            incomingAhead -> ContinuationDecision.UPDATE
            else -> ContinuationDecision.ALREADY_LOADED
        }
    }
    fun nextVector(previous: DailyDocument?, origin: String): Map<String, Long> {
        val vector = previous?.let(::vector).orEmpty().toMutableMap()
        vector[origin] = Math.addExact(vector.getOrDefault(origin, 0), 1)
        return vector
    }
}

data class DayTemplate(val key: String, val title: String)
object DayTemplates {
    val all = listOf(
        DayTemplate("dinner", "Solicitar cena"),
        DayTemplate("lunch", "Solicitar almuerzo"),
        DayTemplate("fuel-crane", "Abastecer combustible a la grúa"),
        DayTemplate("fuel-mule", "Abastecer combustible a las mulas"),
        DayTemplate("empty-email", "Enviar correo de vacíos"),
        DayTemplate("rent-mules", "Rentar mulas para evacuación de vacíos")
    )
    fun operations(day: String, keys: Set<String>): List<Operation> {
        Rules.day(day)
        require(keys.all { key -> all.any { it.key == key } })
        return all.filter { it.key in keys }.map {
            Operation(day = day, module = Module.TASK, title = it.title, status = "Pendiente", templateKey = it.key)
        }
    }
}

object LinkedTasks {
    fun forOperation(op: Operation, existing: List<Operation>, asset: Asset?): Operation? {
        if (op.module !in listOf(Module.FUEL, Module.MEAL, Module.PLAN)) return null
        val previous = existing.firstOrNull { it.module == Module.TASK && it.sourceId == op.id }
        val title = when (op.module) {
            Module.FUEL -> "Combustible · ${asset?.code ?: op.title}"
            Module.MEAL -> "Solicitar ${op.mealType.lowercase()}"
            else -> "Programación · ${op.title}"
        }
        val completed = when (op.module) {
            Module.FUEL -> op.status in listOf("Abastecido", "No requerido")
            Module.MEAL -> op.status == "Solicitada"
            else -> op.status in listOf("Libre confirmado", "Libre disfrutado")
        }
        return (previous ?: Operation(day = op.day, module = Module.TASK, title = title, status = "Pendiente", sourceId = op.id))
            .copy(title = title, status = if (completed) "Completada" else "Pendiente", time = op.time, assetId = op.assetId, operatorId = op.operatorId, updatedAt = op.updatedAt)
    }
}
