package com.workflow.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable enum class Module(val label: String) {
    ATTENDANCE("Asistencia"), MEAL("Comidas"), FUEL("Combustible"), FAULT("Averías"),
    TICKET("Infracciones"), PLAN("Planificación"), TASK("Agenda"), STOCK("Existencias"), POSITION("Posiciones"), CLOSE("Cierre")
}
@Serializable data class Operator(val id: String, val code: String = "", val name: String, val shift: String = "Mañana", val active: Boolean = true)
val Operator.displayLabel: String get() = listOf(name, code).filter { it.isNotBlank() }.joinToString(" · ")
@Serializable data class Asset(val id: String, val code: String, val type: String, val state: String = "Activo")
@Serializable data class StockItem(val id: String, val name: String, val unit: String, val minimum: Double = 0.0)
/** One operation is editable and traceable; corrections retain the same id. No physical deletion. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class Operation(
    val id: String = UUID.randomUUID().toString(), val day: String,
    val module: Module, val title: String, val status: String,
    val operatorId: String? = null, val assetId: String? = null, val itemId: String? = null,
    val operatorIds: List<String> = emptyList(), val shift: String = "Mañana",
    val mealType: String = "Cena", val extended: Boolean = false,
    val quantity: Double = 0.0, val time: String = "", val notes: String = "",
    val updatedAt: String = Instant.now().toString(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sourceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val templateKey: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val resolvedAt: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val severity: String = "Leve",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val expected: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val actual: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val recordedBy: String = ""
) {
    fun naturalKey(): String? = when (module) {
        Module.ATTENDANCE -> "attendance|$operatorId|$shift"
        Module.MEAL -> "meal|$mealType"
        Module.CLOSE -> "close"
        Module.POSITION -> "position|$operatorId|$shift"
        else -> null
    }
}
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class DailyDocument(
    val format: String = "workflow.daily.progress", val schemaVersion: Int = 2,
    val originId: String, val supervisor: String, val day: String, val revision: Long,
    val generatedAt: String = Instant.now().toString(),
    val operators: List<Operator>, val assets: List<Asset>, val items: List<StockItem>,
    val operations: List<Operation>, val stockBalances: Map<String, Double>,
    val checksum: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val journeyId: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val versionVector: Map<String, Long> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val startedAt: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val stockOpeningBalances: Map<String, Double> = emptyMap()
)

object Rules {
    val shifts = listOf("Mañana", "Intermedio", "Tarde", "Noche")
    val statuses = mapOf(
        Module.ATTENDANCE to listOf("Presente", "Tardanza", "Ausencia justificada", "Ausencia injustificada", "Libre"),
        Module.MEAL to listOf("Preparada", "Solicitada"),
        Module.FUEL to listOf("Pendiente", "Abastecido", "No requerido"),
        Module.FAULT to listOf("Abierta", "En proceso", "Cerrada"),
        Module.TICKET to listOf("Borrador", "Emitido", "Anulado"),
        Module.PLAN to listOf("Programado", "Libre previsto", "Libre confirmado", "Libre disfrutado"),
        Module.TASK to listOf("Pendiente", "Completada"),
        Module.STOCK to listOf("Entrada", "Salida"),
        Module.POSITION to listOf("TRH", "Reefer", "Taller"),
        Module.CLOSE to listOf("Cerrada", "Reabierta")
    )
    fun id(value: String) { require(UUID.fromString(value).toString() == value) { "Identificador inválido" } }
    fun text(value: String, max: Int = 200, required: Boolean = true) {
        require((!required || value.isNotBlank()) && value.length <= max && value.none { it.code < 32 && it != '\n' && it != '\t' }) { "Texto vacío, demasiado largo o inválido" }
    }
    fun day(value: String) { require(LocalDate.parse(value).toString() == value) { "Fecha inválida" } }
    fun operator(value: Operator) { id(value.id); text(value.name); text(value.code, 40, required = false); require(value.shift in shifts) { "Selecciona un turno" } }
    fun asset(value: Asset) { id(value.id); text(value.code, 40); text(value.type, 60); require(value.state in listOf("Activo", "En taller", "Inactivo", "Inactivo temporal")) { "Selecciona un estado para el equipo" } }
    fun item(value: StockItem) { id(value.id); text(value.name); text(value.unit, 30); require(value.minimum.isFinite() && value.minimum >= 0) { "El stock mínimo debe ser igual o mayor que cero" } }
    fun operation(value: Operation, operators: List<Operator>, assets: List<Asset>, items: List<StockItem>) {
        id(value.id); day(value.day); text(value.title); text(value.notes, 3000, false)
        require(value.status in statuses.getValue(value.module)) { "Estado inválido" }
        require(value.shift in shifts && value.mealType in listOf("Cena", "Almuerzo"))
        Instant.parse(value.updatedAt)
        value.sourceId?.let(::id)
        text(value.templateKey, 80, false)
        require(value.resolvedAt.isEmpty() || (value.module == Module.FAULT && value.status == "Cerrada")) { "La fecha de resolución solo corresponde a averías cerradas" }
        if (value.resolvedAt.isNotEmpty()) Instant.parse(value.resolvedAt)
        require(value.severity in listOf("Leve", "Moderada", "Grave"))
        text(value.expected, 500, false); text(value.actual, 500, false); text(value.recordedBy, 100, false)
        require(value.time.isEmpty() || Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(value.time)) { "Hora inválida (HH:mm)" }
        require(value.quantity.isFinite() && value.quantity >= 0 && value.quantity <= 1e9) { "Cantidad inválida" }
        require(value.operatorIds.distinct().size == value.operatorIds.size)
        val opIds = operators.map { it.id }.toSet()
        require(value.operatorId == null || value.operatorId in opIds) { "Operador inexistente" }
        require(value.operatorIds.all { it in opIds }) { "Operador de comida inexistente" }
        require(value.assetId == null || assets.any { it.id == value.assetId }) { "Equipo inexistente" }
        require(value.itemId == null || items.any { it.id == value.itemId }) { "Artículo inexistente" }
        if (value.module in listOf(Module.ATTENDANCE, Module.TICKET, Module.PLAN, Module.POSITION)) require(value.operatorId != null) { "Selecciona operador" }
        if (value.module in listOf(Module.FUEL, Module.FAULT)) require(value.assetId != null) { "Selecciona equipo" }
        if (value.module == Module.STOCK) require(value.itemId != null && value.quantity > 0) { "Artículo y cantidad requeridos" }
        if (value.module == Module.MEAL) require(value.operatorIds.isNotEmpty()) { "Selecciona al menos un operador" }
    }
}
