package com.workflow.operaciones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.workflow.core.*
import com.workflow.core.Module
import java.util.UUID

@Composable private fun EditorFrame(title: String, busy: Boolean, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SafeFullscreenDialog(onDismissRequest = { if (!busy) close() }) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Row(Modifier.fillMaxWidth().testTag("dialog-actions"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = close, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-cancel")) { Text("Cancelar") }
            Button(onClick = save, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-confirm")) { Text(if (busy) "Guardando…" else "Guardar") }
        }
    }
}
@Composable private fun Choice(label: String, value: String, options: List<Pair<String, String>>, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(options.firstOrNull { it.first == value }?.second ?: "Seleccionar") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onChoose(key); open = false }) }
            }
        }
    }
}
@Composable private fun Options(label: String, value: String, choices: List<String>, set: (String) -> Unit) = Choice(label, value, choices.map { it to it }, set)
@Composable fun OperationEditor(original: Operation, vm: WorkflowViewModel, onDismiss: () -> Unit) {
    var title by remember(original.id) { mutableStateOf(original.title) }
    var status by remember(original.id) { mutableStateOf(if (original.module == Module.CLOSE && vm.document?.operations?.any { it.module == Module.CLOSE && it.status == "Cerrada" } == true) "Reabierta" else original.status) }
    var opId by remember(original.id) { mutableStateOf(original.operatorId.orEmpty()) }
    var assetId by remember(original.id) { mutableStateOf(original.assetId.orEmpty()) }
    var itemId by remember(original.id) { mutableStateOf(original.itemId.orEmpty()) }
    var selected by remember(original.id) { mutableStateOf(original.operatorIds.toSet()) }
    var shift by remember(original.id) { mutableStateOf(original.shift) }
    var mealType by remember(original.id) { mutableStateOf(original.mealType) }
    var ext by remember(original.id) { mutableStateOf(original.extended) }
    var quantity by remember(original.id) { mutableStateOf(if (original.quantity == 0.0) "" else original.quantity.toString()) }
    var time by remember(original.id) { mutableStateOf(original.time) }
    var notes by remember(original.id) { mutableStateOf(original.notes) }
    var error by remember { mutableStateOf<String?>(null) }
    val kind = original.module
    EditorFrame("${kind.label} · ${original.day}", vm.busy, onDismiss, save = {
        try {
            val number = if (quantity.isBlank()) 0.0 else quantity.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Cantidad inválida")
            val operation = original.copy(title = title.trim(), status = status, operatorId = opId.ifEmpty { null }, assetId = assetId.ifEmpty { null }, itemId = itemId.ifEmpty { null }, operatorIds = selected.toList().sorted(), shift = shift, mealType = mealType, extended = ext, quantity = number, time = time.trim(), notes = notes.trim())
            Rules.operation(operation, vm.operators, vm.assets, vm.items)
            if (kind == Module.CLOSE && DailySummary.pending(vm.document!!).isNotEmpty()) require(notes.isNotBlank()) { "Explica los pendientes del cierre" }
            vm.save(operation, onDismiss)
        } catch (e: Exception) { error = e.message }
    }) {
        Text("Se actualiza el JSON diario al guardar. Los cambios quedan en el historial.", style = MaterialTheme.typography.bodyMedium)
        error?.let { Text(it, color = Brand.Red) }
        OutlinedTextField(title, { title = it }, label = { Text(if (kind == Module.TICKET) "Motivo de la infracción" else "Título / descripción breve") }, modifier = Modifier.fillMaxWidth())
        Options("Estado", status, Rules.statuses.getValue(kind)) { status = it }
        if (kind in listOf(Module.ATTENDANCE, Module.PLAN, Module.TICKET, Module.POSITION, Module.FUEL)) {
            Choice("Operador${if (kind == Module.FUEL) " (opcional)" else ""}", opId,
                (if (kind == Module.FUEL) listOf("" to "Sin asignar") else emptyList()) + vm.operators.map { it.id to "${it.code} · ${it.name}" }) { opId = it }
            if (vm.operators.isEmpty()) Text("Añade personal en Más → Personal.")
        }
        if (kind in listOf(Module.ATTENDANCE, Module.PLAN, Module.POSITION)) Options("Turno real", shift, Rules.shifts) { shift = it }
        if (kind == Module.ATTENDANCE) {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ext, { ext = it }); Text("EXT · Jornada extendida") }
            Text("Tardanza cuenta como presente. Libre no es ausencia. Una persona en varios turnos cuenta una sola vez en presentes únicos.", style = MaterialTheme.typography.bodyMedium)
        }
        if (kind in listOf(Module.FUEL, Module.FAULT)) {
            Choice("Equipo", assetId, vm.assets.map { it.id to "${it.code} · ${it.type} · ${it.state}" }) { assetId = it }
            if (vm.assets.isEmpty()) Text("Añade equipos en Más → Equipos.")
        }
        if (kind == Module.STOCK) {
            Choice("Artículo", itemId, vm.items.map { it.id to "${it.name} (${it.unit})" }) { itemId = it }
            Text("Las salidas no pueden dejar saldos negativos. El saldo se conserva entre jornadas.")
        }
        if (kind in listOf(Module.STOCK, Module.FUEL)) OutlinedTextField(quantity, { quantity = it }, label = { Text(if (kind == Module.FUEL) "Litros (opcional)" else "Cantidad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (kind == Module.MEAL) {
            Options("Tipo de comida", mealType, listOf("Cena", "Almuerzo")) { mealType = it }
            Text("${selected.size} operadores seleccionados. La comida se selecciona independientemente de la asistencia.")
            vm.operators.filter { it.active || it.id in selected }.forEach { operator ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(operator.id in selected, { checked -> selected = if (checked) selected + operator.id else selected - operator.id })
                    Text("${operator.name} · ${operator.code}", modifier = Modifier.weight(1f))
                }
            }
        }
        OutlinedTextField(time, { time = it }, label = { Text("Hora aproximada HH:mm (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text(if (kind == Module.CLOSE) "Pendientes y excepciones del cierre" else "Detalles / seguimiento / resolución") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("operation-notes"))
        if (kind == Module.TICKET) Text("Responsable: ${vm.document?.supervisor}. El ticket no acredita por sí solo una sanción oficial.")
        if (kind == Module.CLOSE) Text("${vm.document?.let { DailySummary.pending(it).size } ?: 0} pendientes. Cerrar no los elimina. Reabre la jornada para corregir registros.")
    }
}

@Composable fun MasterEditor(kind: String, vm: WorkflowViewModel, onDismiss: () -> Unit, operator: Operator? = null, asset: Asset? = null, item: StockItem? = null) {
    var name by remember { mutableStateOf(operator?.name ?: item?.name ?: "") }
    var code by remember { mutableStateOf(operator?.code ?: asset?.code ?: "") }
    var detail by remember { mutableStateOf(asset?.type ?: item?.unit ?: "") }
    var minimum by remember { mutableStateOf(item?.minimum?.toString() ?: "0") }
    var shift by remember { mutableStateOf(operator?.shift ?: "Mañana") }
    var active by remember { mutableStateOf(operator?.active ?: true) }
    var assetState by remember { mutableStateOf(asset?.state ?: "Activo") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorFrame(when (kind) { "operator" -> "Ficha de operador"; "asset" -> "Ficha de equipo"; else -> "Ficha de artículo" }, vm.busy, onDismiss, save = {
        try {
            val id = operator?.id ?: asset?.id ?: item?.id ?: UUID.randomUUID().toString()
            when (kind) {
                "operator" -> Operator(id, code.trim(), name.trim(), shift, active).also { Rules.operator(it); vm.addOperator(it, onDismiss) }
                "asset" -> Asset(id, code.trim(), detail.trim(), assetState).also { Rules.asset(it); vm.addAsset(it, onDismiss) }
                else -> StockItem(id, name.trim(), detail.trim(), minimum.replace(',', '.').toDouble()).also { Rules.item(it); vm.addItem(it, onDismiss) }
            }
        } catch (e: Exception) { error = e.message ?: "Revisa los campos" }
    }) {
        error?.let { Text(it, color = Brand.Red) }
        if (kind != "asset") OutlinedTextField(name, { name = it }, label = { Text(if (kind == "operator") "Nombre completo" else "Nombre del artículo") }, modifier = Modifier.fillMaxWidth())
        if (kind != "item") OutlinedTextField(code, { code = it }, label = { Text("Ficha / código único") }, modifier = Modifier.fillMaxWidth())
        if (kind == "operator") {
            Options("Turno habitual", shift, Rules.shifts) { shift = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(active, { active = it }); Text("Operador activo") }
            Text("Desactivar conserva el historial del operador.")
        }
        if (kind == "asset") {
            Options("Tipo de equipo", detail, listOf("Mula", "Grúa", "Montacargas", "Otro")) { detail = it }
            Options("Estado del equipo", assetState, listOf("Activo", "En taller", "Inactivo")) { assetState = it }
        }
        if (kind == "item") {
            OutlinedTextField(detail, { detail = it }, label = { Text("Unidad (litros, pares, unidades…)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minimum, { minimum = it }, label = { Text("Stock mínimo") }, modifier = Modifier.fillMaxWidth())
            Text("Registra el stock inicial como una Entrada en Existencias para conservar su trazabilidad.")
        }
    }
}

@Composable fun ReportViewer(doc: DailyDocument, onDismiss: () -> Unit, share: () -> Unit) {
    SafeFullscreenDialog(onDismissRequest = onDismiss) {
        BrandHeader()
        Text("Estatus del ${doc.day}", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DailySummary.lines(doc).forEach { line -> Text(line) }
        }
        Row(Modifier.fillMaxWidth().testTag("dialog-actions"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-cancel")) { Text("Cerrar") }
            Button(onClick = share, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-confirm")) { Text("Compartir") }
        }
    }
}
