package com.workflow.operaciones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.workflow.core.*
import com.workflow.core.Module
import java.util.UUID

@Composable private fun EditorFrame(title: String, busy: Boolean, close: () -> Unit, save: () -> Unit, saveAnother: (() -> Unit)? = null, saveLabel: String = "Guardar", content: @Composable ColumnScope.() -> Unit) {
    SafeFullscreenDialog(onDismissRequest = { if (!busy) close() }) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Column(Modifier.fillMaxWidth().testTag("dialog-actions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saveAnother != null) Button(onClick = saveAnother, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dialog-save-another")) {
                Text(if (busy) "Guardando…" else "Guardar y añadir otro")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = close, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-cancel")) { Text("Cerrar") }
                val buttonModifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog-confirm")
                if (saveAnother != null) OutlinedButton(onClick = save, enabled = !busy, modifier = buttonModifier) { Text("Guardar y cerrar") }
                else Button(onClick = save, enabled = !busy, modifier = buttonModifier) { Text(if (busy) "Guardando…" else saveLabel) }
            }
        }
    }
}
@Composable private fun Choice(label: String, value: String, options: List<Pair<String, String>>, onChoose: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
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
@Composable fun OperationEditor(original: Operation, vm: WorkflowViewModel, addMaster: ((String) -> Unit)? = null, onDismiss: () -> Unit) {
    LaunchedEffect(original.id) { vm.clearActionError() }
    val isNew = rememberSaveable(original.id) { vm.document?.operations?.none { it.id == original.id } != false }
    var draftId by rememberSaveable(original.id) { mutableStateOf(original.id) }
    var savedCount by rememberSaveable(original.id) { mutableStateOf(0) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(savedCount) { if (savedCount > 0) titleFocus.requestFocus() }
    var title by rememberSaveable(original.id) { mutableStateOf(original.title) }
    var status by rememberSaveable(original.id) { mutableStateOf(if (original.module == Module.CLOSE && vm.document?.operations?.any { it.module == Module.CLOSE && it.status == "Cerrada" } == true) "Reabierta" else original.status) }
    var opId by rememberSaveable(original.id) { mutableStateOf(original.operatorId.orEmpty()) }
    var assetId by rememberSaveable(original.id) { mutableStateOf(original.assetId.orEmpty()) }
    var itemId by rememberSaveable(original.id) { mutableStateOf(original.itemId.orEmpty()) }
    var selected by rememberSaveable(original.id) { mutableStateOf(original.operatorIds.toSet()) }
    var shift by rememberSaveable(original.id) { mutableStateOf(original.shift) }
    var mealType by rememberSaveable(original.id) { mutableStateOf(original.mealType) }
    var ext by rememberSaveable(original.id) { mutableStateOf(original.extended) }
    var quantity by rememberSaveable(original.id) { mutableStateOf(if (original.quantity == 0.0) "" else original.quantity.toString()) }
    var time by rememberSaveable(original.id) { mutableStateOf(original.time) }
    var notes by rememberSaveable(original.id) { mutableStateOf(original.notes) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = original.module
    fun persist(another: Boolean) {
        try {
            require(title.isNotBlank()) { "Escribe una descripción para el registro" }
            val number = if (quantity.isBlank()) 0.0 else quantity.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Cantidad inválida")
            val operation = original.copy(id = draftId, title = title.trim(), status = status, operatorId = opId.ifEmpty { null }, assetId = assetId.ifEmpty { null }, itemId = itemId.ifEmpty { null }, operatorIds = selected.toList().sorted(), shift = shift, mealType = mealType, extended = ext, quantity = number, time = time.trim(), notes = notes.trim())
            Rules.operation(operation, vm.operators, vm.assets, vm.items)
            if (kind == Module.CLOSE && DailySummary.pending(vm.document!!).isNotEmpty()) require(notes.isNotBlank()) { "Explica los pendientes del cierre" }
            vm.save(operation, offerShare = !another) {
                if (another) {
                    draftId = UUID.randomUUID().toString()
                    title = if (kind == Module.TASK) "" else kind.label
                    opId = ""; assetId = ""; itemId = ""; selected = emptySet()
                    ext = false; quantity = ""; time = ""; notes = ""; error = null
                    savedCount++
                } else onDismiss()
            }
        } catch (e: Exception) { error = e.message }
    }
    val canAddAnother = isNew && kind !in listOf(Module.MEAL, Module.CLOSE)
    val closeEditor: () -> Unit = {
        onDismiss()
        if (savedCount > 0 && vm.shareAfterSave) vm.shareRequest = vm.document
    }
    EditorFrame("${kind.label} · ${original.day}", vm.busy, closeEditor, save = { persist(false) },
        saveAnother = if (canAddAnother) ({ persist(true) }) else null, saveLabel = if (isNew) "Guardar" else "Guardar cambios") {
        Text(if (savedCount > 0) "Registro guardado. Puedes añadir el siguiente." else "Los cambios se guardan en tu informe del día.", style = MaterialTheme.typography.bodyMedium)
        (error ?: vm.actionError)?.let { Text(it, color = Brand.Red) }
        OutlinedTextField(title, { title = it }, label = { Text(if (kind == Module.TICKET) "Motivo de la infracción" else "Título / descripción breve") }, modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag("operation-title"))
        Options("Estado", status, Rules.statuses.getValue(kind)) { status = it }
        if (kind in listOf(Module.ATTENDANCE, Module.PLAN, Module.TICKET, Module.POSITION, Module.FUEL)) {
            Choice("Operador${if (kind == Module.FUEL) " (opcional)" else ""}", opId,
                (if (kind == Module.FUEL) listOf("" to "Sin asignar") else emptyList()) + vm.operators.map { it.id to it.displayLabel }) { opId = it }
            if (vm.operators.isEmpty()) { Text("Necesitas añadir un operador para continuar."); if (addMaster != null) TextButton(onClick = { addMaster("operator") }) { Text("Añadir operador") } }
        }
        if (kind in listOf(Module.ATTENDANCE, Module.PLAN, Module.POSITION)) Options("Turno real", shift, Rules.shifts) { shift = it }
        if (kind == Module.ATTENDANCE) {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ext, { ext = it }); Text("EXT · Jornada extendida") }
            Text("Tardanza cuenta como presente. Libre no es ausencia. Una persona en varios turnos cuenta una sola vez en presentes únicos.", style = MaterialTheme.typography.bodyMedium)
        }
        if (kind in listOf(Module.FUEL, Module.FAULT)) {
            Choice("Equipo", assetId, vm.assets.map { it.id to "${it.code} · ${it.type} · ${it.state}" }) { assetId = it }
            if (vm.assets.isEmpty()) { Text("Necesitas añadir un equipo para continuar."); if (addMaster != null) TextButton(onClick = { addMaster("asset") }) { Text("Añadir equipo") } }
        }
        if (kind == Module.STOCK) {
            Choice("Artículo", itemId, vm.items.map { it.id to "${it.name} (${it.unit})" }) { itemId = it }
            if (vm.items.isEmpty() && addMaster != null) TextButton(onClick = { addMaster("item") }) { Text("Añadir artículo") }
            Text("Las salidas no pueden dejar saldos negativos. El saldo se conserva entre jornadas.")
        }
        if (kind in listOf(Module.STOCK, Module.FUEL)) OutlinedTextField(quantity, { quantity = it }, label = { Text(if (kind == Module.FUEL) "Litros (opcional)" else "Cantidad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (kind == Module.MEAL) {
            Options("Tipo de comida", mealType, listOf("Cena", "Almuerzo")) { mealType = it }
            Text("${selected.size} operadores seleccionados. La comida se selecciona independientemente de la asistencia.")
            vm.operators.filter { it.active || it.id in selected }.forEach { operator ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(operator.id in selected, { checked -> selected = if (checked) selected + operator.id else selected - operator.id })
                    Text(operator.displayLabel, modifier = Modifier.weight(1f))
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
    LaunchedEffect(kind, operator?.id, asset?.id, item?.id) { vm.clearActionError() }
    val isNew = operator == null && asset == null && item == null
    var draftId by rememberSaveable(operator?.id, asset?.id, item?.id) { mutableStateOf(operator?.id ?: asset?.id ?: item?.id ?: UUID.randomUUID().toString()) }
    var name by rememberSaveable { mutableStateOf(operator?.name ?: item?.name ?: "") }
    var code by rememberSaveable { mutableStateOf(operator?.code ?: asset?.code ?: "") }
    var detail by rememberSaveable { mutableStateOf(asset?.type ?: item?.unit ?: if (kind == "item") "unidades" else "") }
    var minimum by rememberSaveable { mutableStateOf(item?.minimum?.toString() ?: "0") }
    var shift by rememberSaveable { mutableStateOf(operator?.shift ?: "Mañana") }
    var active by rememberSaveable { mutableStateOf(operator?.active ?: true) }
    var assetState by rememberSaveable { mutableStateOf(asset?.state ?: "Activo") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var savedCount by rememberSaveable { mutableStateOf(0) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(savedCount) { if (savedCount > 0) nameFocus.requestFocus() }
    fun persist(another: Boolean) {
        try {
            error = null
            val done: () -> Unit = {
                if (another) {
                    draftId = UUID.randomUUID().toString()
                    name = ""; code = ""; minimum = "0"; active = true; savedCount++
                } else onDismiss()
            }
            when (kind) {
                "operator" -> {
                    require(name.isNotBlank()) { "Escribe el nombre del operador" }
                    require(code.isBlank() || vm.operators.none { it.id != draftId && it.code.equals(code.trim(), true) }) { "Ese código ya está asignado a otro operador" }
                    Operator(draftId, code.trim(), name.trim(), shift, active).also { Rules.operator(it); vm.addOperator(it, done) }
                }
                "asset" -> {
                    require(code.isNotBlank()) { "Escribe un nombre o identificación para el equipo" }
                    require(detail.isNotBlank()) { "Selecciona el tipo de equipo" }
                    Asset(draftId, code.trim(), detail.trim(), assetState).also { Rules.asset(it); vm.addAsset(it, done) }
                }
                else -> {
                    require(name.isNotBlank()) { "Escribe el nombre del artículo" }
                    val number = minimum.ifBlank { "0" }.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Revisa el stock mínimo")
                    StockItem(draftId, name.trim(), detail.trim().ifBlank { "unidades" }, number).also { Rules.item(it); vm.addItem(it, done) }
                }
            }
        } catch (e: Exception) { error = e.message ?: "Revisa los campos" }
    }
    val label = when (kind) { "operator" -> "operador"; "asset" -> "equipo"; else -> "artículo" }
    EditorFrame("${if (isNew) "Añadir" else "Editar"} $label", vm.busy, onDismiss, save = { persist(false) },
        saveAnother = if (isNew) ({ persist(true) }) else null, saveLabel = "Guardar cambios") {
        if (savedCount > 0) Text("$label guardado. Puedes añadir otro.".replaceFirstChar { it.uppercase() }, color = Brand.Teal, modifier = Modifier.testTag("saved-another"))
        (error ?: vm.actionError)?.let { Text(it, color = Brand.Red) }
        if (kind != "asset") OutlinedTextField(name, { name = it; error = null }, label = { Text(if (kind == "operator") "Nombre completo" else "Nombre del artículo") }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("master-name"), singleLine = true)
        if (kind != "item") OutlinedTextField(code, { code = it; error = null }, label = { Text(if (kind == "operator") "Código de empleado (opcional)" else "Nombre o identificación del equipo") },
            modifier = Modifier.fillMaxWidth().then(if (kind == "asset") Modifier.focusRequester(nameFocus) else Modifier).testTag("master-code"), singleLine = true)
        if (kind == "operator") {
            Options("Turno habitual", shift, Rules.shifts) { shift = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(active, { active = it }); Text("Operador activo") }
            Text("Puedes guardar al operador solo con su nombre. Desactivarlo conserva su historial.")
        }
        if (kind == "asset") {
            Options("Tipo de equipo", detail, listOf("Mula", "Grúa", "Montacargas", "Otro")) { detail = it }
            Options("Estado del equipo", assetState, listOf("Activo", "En taller", "Inactivo")) { assetState = it }
        }
        if (kind == "item") {
            OutlinedTextField(detail, { detail = it }, label = { Text("Unidad (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(minimum, { minimum = it }, label = { Text("Stock mínimo (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Puedes añadir existencias desde el módulo de Existencias.")
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
