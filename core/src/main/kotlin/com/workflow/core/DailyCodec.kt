package com.workflow.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant

/** Strict, bounded exchange format. SHA-256 detects corruption, NOT sender authenticity. */
object DailyCodec {
    const val MAX_BYTES = 4 * 1024 * 1024
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; allowSpecialFloatingPointValues = false }
    private fun hash(value: DailyDocument): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(value.copy(checksum = "")).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun seal(document: DailyDocument): DailyDocument { validate(document); return document.copy(checksum = hash(document)) }
    fun encode(document: DailyDocument): ByteArray {
        val sealed = seal(document)
        return json.encodeToString(sealed).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "Informe supera 4 MB" } }
    }
    fun decode(bytes: ByteArray): DailyDocument {
        require(bytes.size in 1..MAX_BYTES) { "Informe vacío o mayor de 4 MB" }
        // Bound nesting before the recursive JSON parser touches untrusted data.
        var depth = 0
        var quoted = false
        var escaped = false
        for (byte in bytes) {
            val ch = byte.toInt().toChar()
            if (quoted) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "El informe es demasiado complejo" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Informe inválido" } }
            }
        }
        require(depth == 0 && !quoted) { "Informe incompleto" }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val document = json.decodeFromString<DailyDocument>(text)
        validate(document)
        require(document.checksum.matches(Regex("[0-9a-f]{64}")) && document.checksum == hash(document)) { "Informe alterado o incompleto" }
        return document
    }
    fun validate(d: DailyDocument) {
        require(d.format == "workflow.daily.progress" && d.schemaVersion in 1..2) { "Formato o versión no compatible" }
        Rules.id(d.originId); Rules.day(d.day); Rules.text(d.supervisor, 100); Instant.parse(d.generatedAt)
        require(d.revision > 0 && d.revision < Long.MAX_VALUE)
        if (d.journeyId.isNotEmpty()) Rules.id(d.journeyId)
        if (d.startedAt.isNotEmpty()) Instant.parse(d.startedAt)
        require(d.versionVector.size <= 100) { "Demasiados teléfonos en la jornada" }
        d.versionVector.forEach { (id, revision) -> Rules.id(id); require(revision in 1 until Long.MAX_VALUE) }
        require(d.operators.size <= 2000 && d.assets.size <= 2000 && d.items.size <= 2000 && d.operations.size <= 3000) { "Demasiados registros" }
        d.operators.forEach(Rules::operator); d.assets.forEach(Rules::asset); d.items.forEach(Rules::item)
        require(d.operators.distinctBy { it.id }.size == d.operators.size)
        val assignedCodes = d.operators.filter { it.code.isNotBlank() }.map { it.code.lowercase(java.util.Locale.ROOT) }
        require(assignedCodes.distinct().size == assignedCodes.size) { "Hay códigos de empleado repetidos" }
        require(d.assets.distinctBy { it.id }.size == d.assets.size && d.assets.distinctBy { it.code.lowercase() }.size == d.assets.size)
        require(d.items.distinctBy { it.id }.size == d.items.size)
        require(d.operations.distinctBy { it.id }.size == d.operations.size) { "Operaciones duplicadas" }
        val natural = d.operations.mapNotNull { it.naturalKey() }
        require(natural.distinct().size == natural.size) { "Asistencia, comida o cierre duplicado" }
        d.operations.forEach { require(it.day == d.day); Rules.operation(it, d.operators, d.assets, d.items) }
        d.operations.filter { it.sourceId != null }.forEach { task ->
            require(task.module == Module.TASK && d.operations.any { it.id == task.sourceId && it.module != Module.TASK }) { "Tarea vinculada sin actividad de origen" }
        }
        require(d.operations.filter { it.templateKey.isNotBlank() }.distinctBy { it.templateKey }.size == d.operations.count { it.templateKey.isNotBlank() }) { "Tareas automáticas duplicadas" }
        require(d.stockBalances.keys == d.items.map { it.id }.toSet()) { "Existencias incompletas" }
        require(d.stockBalances.values.all { it.isFinite() && it >= 0 }) { "Saldo inválido" }
        require(d.stockOpeningBalances.keys.all { id -> d.items.any { it.id == id } } && d.stockOpeningBalances.values.all { it.isFinite() && it >= 0 }) { "Existencias iniciales inválidas" }
        require(d.carryOverFaults.size <= 3000 && (d.operations + d.carryOverFaults).distinctBy { it.id }.size == d.operations.size + d.carryOverFaults.size) { "Averías de seguimiento duplicadas" }
        d.carryOverFaults.forEach { fault ->
            require(fault.day < d.day && fault.module == Module.FAULT && fault.sourceId == null && fault.templateKey.isEmpty()) { "Avería de seguimiento inválida" }
            Rules.operation(fault, d.operators, d.assets, d.items)
        }
    }
}

enum class ImportDecision { NEW, UPDATE, ALREADY_RECEIVED }
object ImportPolicy {
    fun decide(localOrigin: String, incoming: DailyDocument, existing: DailyDocument?): ImportDecision {
        require(localOrigin != incoming.originId) { "Este informe pertenece a este teléfono; no se importa sobre datos propios" }
        if (existing == null) return ImportDecision.NEW
        require(existing.originId == incoming.originId && existing.day == incoming.day)
        require(incoming.revision >= existing.revision) { "Versión anterior a la ya recibida" }
        if (incoming.revision == existing.revision) {
            require(incoming.checksum == existing.checksum) { "Conflicto: misma revisión con contenido diferente" }
            return ImportDecision.ALREADY_RECEIVED
        }
        return ImportDecision.UPDATE
    }
}

object DailySummary {
    fun present(d: DailyDocument): Int = d.operations.filter { it.module == Module.ATTENDANCE && it.status in listOf("Presente", "Tardanza") }.mapNotNull { it.operatorId }.distinct().size
    fun pending(d: DailyDocument): List<Operation> {
        val records = d.operations + d.carryOverFaults
        val pending = records.filter { it.status in listOf("Pendiente", "Abierta", "En proceso", "Borrador") }
        val represented = pending.filter { it.module == Module.TASK }.mapNotNull { it.sourceId }.toSet()
        return pending.filter { it.id !in represented }.distinctBy { it.id }
    }
    fun lines(d: DailyDocument): List<String> = buildList {
        add("Supervisor: ${d.supervisor} · Fecha: ${d.day}")
        add("Actualización ${d.revision} · Actualizado ${d.generatedAt}")
        add("Presentes únicos: ${present(d)} · Pendientes: ${pending(d).size}")
        Module.entries.forEach { module ->
            val rows = d.operations.filter { it.module == module }
            if (rows.isNotEmpty()) {
                add(""); add(module.label.uppercase())
                rows.forEach { op ->
                    val who = d.operators.firstOrNull { it.id == op.operatorId }?.name.orEmpty()
                    val asset = d.assets.firstOrNull { it.id == op.assetId }?.code.orEmpty()
                    val stock = d.items.firstOrNull { it.id == op.itemId }
                    add(listOf(op.time, op.title, who, asset, op.status).filter { it.isNotBlank() }.joinToString(" · "))
                    if (op.module == Module.ATTENDANCE) add("Turno: ${op.shift}${if (op.extended) " · Jornada extendida" else ""}")
                    if (op.module == Module.MEAL) { add("${op.mealType}: ${op.operatorIds.size} personas"); op.operatorIds.forEach { id -> add("  ${d.operators.first { it.id == id }.name}") } }
                    if (op.quantity > 0) add("Cantidad: ${op.quantity} ${stock?.unit ?: if (op.module == Module.FUEL) "litros" else ""}")
                    if (op.notes.isNotEmpty()) add(op.notes)
                }
            }
        }
        if (d.items.isNotEmpty()) { add(""); add("EXISTENCIAS AL CIERRE DE LA FECHA"); d.items.forEach { add("${it.name}: ${d.stockBalances[it.id]} ${it.unit} · Mínimo ${it.minimum}") } }
    }
}
