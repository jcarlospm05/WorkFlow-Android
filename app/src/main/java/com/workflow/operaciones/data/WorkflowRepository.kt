package com.workflow.operaciones.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import com.workflow.core.*
import com.workflow.core.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Instant
import java.util.UUID

class WorkflowRepository(private val context: Context, private val db: WorkflowDatabase) {
    val transferKeys = TransferKeyStore(context)
    private val dao = db.dao()
    private val mutex = Mutex()
    @Volatile var fileWarning: String? = null
        private set
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
    val origin: String = prefs.getString("origin", null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("origin", it).commit()) }
    val supervisor: String get() = prefs.getString("supervisor", "Supervisor").orEmpty()
    var shareAfterSave: Boolean
        get() = prefs.getBoolean("offer_share", false)
        set(value) { check(prefs.edit().putBoolean("offer_share", value).commit()) }
    private val json get() = DailyCodec.json
    suspend fun setSupervisor(name: String, day: String) = withContext(Dispatchers.IO) {
        Rules.text(name, 100)
        mutex.withLock {
            check(prefs.edit().putString("supervisor", name.trim()).commit())
            val doc = db.withTransaction { regenerate(day, supervisorOverride = name.trim()) }
            materialize(doc)
        }
    }
    private fun decodeDocument(row: DocumentRow) = json.decodeFromString<DailyDocument>(row.payload)
    suspend fun document(day: String): DailyDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            Rules.day(day)
            val doc = dao.document(day)?.let(::decodeDocument) ?: db.withTransaction {
                regenerate(day)
            }
            materialize(doc)
            doc
        }
    }
    suspend fun masters(): Triple<List<Operator>, List<Asset>, List<StockItem>> = withContext(Dispatchers.IO) { loadMasters() }
    private suspend fun loadMasters(): Triple<List<Operator>, List<Asset>, List<StockItem>> {
        val rows = dao.masters()
        return Triple(rows.filter { it.kind == "operator" }.map { json.decodeFromString<Operator>(it.payload) }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) },
            rows.filter { it.kind == "asset" }.map { json.decodeFromString<Asset>(it.payload) },
            rows.filter { it.kind == "item" }.map { json.decodeFromString<StockItem>(it.payload) })
    }
    suspend fun addOperator(value: Operator, day: String) {
        Rules.operator(value)
        // Keep the existing unique index and installed databases. An absent employee code uses
        // an internal key longer than any accepted employee code; it is never shown to people.
        val uniqueKey = value.code.lowercase(java.util.Locale.ROOT).ifBlank { "workflow:auto:${value.id}" }
        addMaster(MasterRow(value.id, "operator", uniqueKey, json.encodeToString(value)), day)
    }
    suspend fun addAsset(value: Asset, day: String) { Rules.asset(value); addMaster(MasterRow(value.id, "asset", value.code.lowercase(), json.encodeToString(value)), day) }
    suspend fun addItem(value: StockItem, day: String) { Rules.item(value); addMaster(MasterRow(value.id, "item", value.name.lowercase(), json.encodeToString(value)), day) }
    private suspend fun addMaster(row: MasterRow, day: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val doc = db.withTransaction {
                val old = dao.masters().firstOrNull { it.id == row.id }
                require(old == null || old.kind == row.kind)
                if (old == null) dao.insertMaster(row) else dao.updateMaster(row)
                if (row.kind == "asset") {
                    updateWorkshopAssets()
                    ensureWorkshopTask(day)
                }
                dao.audit(AuditRow(at = Instant.now().toString(), action = if (old == null) "CREATE_MASTER" else "UPDATE_MASTER", recordId = row.id, before = old?.payload, after = row.payload))
                regenerate(day)
            }
            materialize(doc)
        }
    }
    suspend fun save(value: Operation) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val docs = db.withTransaction {
                val (ops, assets, items) = loadMasters()
                Rules.operation(value, ops, assets, items)
                val old = dao.operation(value.id)
                require(old == null || old.day == value.day) { "No se puede mover un registro a otra fecha" }
                val sameDay = dao.operations(value.day).map { json.decodeFromString<Operation>(it.payload) }
                val closed = sameDay.any { it.module == Module.CLOSE && it.status == "Cerrada" }
                val resolvingEarlierFault = value.module == Module.FAULT && value.status == "Cerrada" && old != null && value.day < java.time.LocalDate.now().toString()
                require(!closed || resolvingEarlierFault || (value.module == Module.CLOSE && value.status == "Reabierta")) { "Reabre la jornada antes de corregirla" }
                if (value.module == Module.CLOSE && value.status == "Cerrada" && sameDay.any { it.status in listOf("Pendiente", "Abierta", "En proceso", "Borrador") }) {
                    require(value.notes.isNotBlank()) { "Explica los pendientes y excepciones del cierre" }
                }
                val key = value.naturalKey()
                val match = if (key == null) null else sameDay.firstOrNull { it.naturalKey() == key }
                require(old == null || match == null || match.id == value.id) { "Ya existe otro registro para ese operador, turno o comida" }
                val normalized = value.copy(id = match?.id ?: value.id, updatedAt = Instant.now().toString())
                val previous = dao.operation(normalized.id)
                val payload = json.encodeToString(normalized)
                dao.putOperation(OperationRow(normalized.id, normalized.day, normalized.module.name, key, payload))
                val task = LinkedTasks.forOperation(normalized, sameDay, assets.firstOrNull { it.id == normalized.assetId })
                if (task != null) putAutomaticTask(task)
                updateTemplateProgress(normalized.day)
                if (normalized.module == Module.FAULT) {
                    val resolved = if (normalized.status == "Cerrada") normalized.copy(resolvedAt = normalized.resolvedAt.ifBlank { Instant.now().toString() }) else normalized.copy(resolvedAt = "")
                    dao.putOperation(OperationRow(resolved.id, resolved.day, resolved.module.name, key, json.encodeToString(resolved)))
                    updateWorkshopAssets()
                    ensureWorkshopTask(normalized.day, reopen = normalized.status != "Cerrada")
                }
                // Stock ledger is global: editing an earlier day must not make any later balance negative.
                validateStockHistory(items)
                dao.audit(AuditRow(at = normalized.updatedAt, action = if (previous == null) "CREATE" else "UPDATE", recordId = normalized.id, before = previous?.payload, after = payload))
                val affectedDays = if (value.module in listOf(Module.STOCK, Module.FAULT)) (dao.documents().filter { it.day >= value.day }.map { it.day } + value.day).distinct() else listOf(value.day)
                affectedDays.map { if (value.module == Module.FAULT) ensureWorkshopTask(it, reopen = value.status != "Cerrada"); regenerate(it) }
            }
            docs.forEach(::materialize)
        }
    }
    private suspend fun putAutomaticTask(task: Operation) {
        val before = dao.operation(task.id)
        val payload = json.encodeToString(task)
        dao.putOperation(OperationRow(task.id, task.day, task.module.name, task.naturalKey(), payload))
        dao.audit(AuditRow(at = Instant.now().toString(), action = "LINKED_TASK", recordId = task.id, before = before?.payload, after = payload))
    }
    private suspend fun updateTemplateProgress(day: String) {
        val operations = dao.operations(day).map { json.decodeFromString<Operation>(it.payload) }
        val assets = loadMasters().second.associateBy { it.id }
        operations.filter { it.module == Module.TASK && it.sourceId == null && it.templateKey in listOf("dinner", "lunch", "fuel-crane", "fuel-mule") }.forEach { task ->
            val activities = operations.filter { op -> when (task.templateKey) {
                "dinner" -> op.module == Module.MEAL && op.mealType == "Cena"
                "lunch" -> op.module == Module.MEAL && op.mealType == "Almuerzo"
                "fuel-crane" -> op.module == Module.FUEL && assets[op.assetId]?.type == "Grúa"
                else -> op.module == Module.FUEL && assets[op.assetId]?.type == "Mula"
            } }
            if (activities.isNotEmpty()) {
                val complete = activities.all { it.status in listOf("Solicitada", "Abastecido", "No requerido") }
                val status = if (complete) "Completada" else "Pendiente"
                if (task.status != status) putAutomaticTask(task.copy(status = status, updatedAt = Instant.now().toString()))
            }
        }
    }
    private suspend fun ensureWorkshopTask(day: String, reopen: Boolean = false) {
        val assets = loadMasters().second
        val inWorkshop = assets.any { it.state in listOf("En taller", "Inactivo temporal") }
        val dayOperations = dao.operations(day).map { json.decodeFromString<Operation>(it.payload) }
        if (dayOperations.any { it.module == Module.CLOSE && it.status == "Cerrada" }) return
        val previous = dayOperations.firstOrNull { it.templateKey == "workshop-review" }
        if (!inWorkshop && previous == null) return
        val task = previous ?: Operation(day = day, module = Module.TASK, title = "Revisar equipos en taller", status = "Pendiente", templateKey = "workshop-review")
        putAutomaticTask(task.copy(status = if (!inWorkshop) "Completada" else if (reopen) "Pendiente" else task.status, updatedAt = Instant.now().toString()))
    }
    private suspend fun updateWorkshopAssets() {
        val faults = dao.allOperations().map { json.decodeFromString<Operation>(it.payload) }.filter { it.module == Module.FAULT }
        loadMasters().second.forEach { asset ->
            val related = faults.filter { it.assetId == asset.id }
            val state = when {
                related.any { it.status != "Cerrada" } -> "Inactivo temporal"
                related.isNotEmpty() && asset.state in listOf("Inactivo temporal", "En taller") -> "Activo"
                else -> asset.state
            }
            if (state != asset.state) {
                val payload = json.encodeToString(asset.copy(state = state))
                dao.updateMaster(MasterRow(asset.id, "asset", asset.code.lowercase(), payload))
                dao.audit(AuditRow(at = Instant.now().toString(), action = "WORKSHOP_STATE", recordId = asset.id, before = json.encodeToString(asset), after = payload))
            }
        }
    }
    suspend fun startDay(day: String, selected: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val doc = db.withTransaction {
                val previous = dao.document(day)?.let(::decodeDocument)
                require(previous?.startedAt.isNullOrEmpty()) { "La jornada ya está iniciada" }
                val existing = dao.operations(day).map { json.decodeFromString<Operation>(it.payload) }
                require(existing.none { it.module == Module.CLOSE && it.status == "Cerrada" }) { "Reabre el día antes de iniciar sus actividades" }
                DayTemplates.operations(day, selected).filter { task -> existing.none { it.templateKey == task.templateKey } }.forEach { putAutomaticTask(it) }
                ensureWorkshopTask(day)
                regenerate(day, startedAt = Instant.now().toString())
            }
            materialize(doc)
        }
    }
    private suspend fun validateStockHistory(items: List<StockItem>) {
        val stock = dao.allOperations().map { json.decodeFromString<Operation>(it.payload) }.filter { it.module == Module.STOCK }
        val openings = dao.openings()
        items.forEach { item ->
            var balance = 0.0
            val rowsByDay = stock.filter { it.itemId == item.id }.groupBy { it.day }
            val anchors = openings.filter { it.itemId == item.id }.associate { it.day to it.quantity }
            (rowsByDay.keys + anchors.keys).sorted().forEach { date ->
                anchors[date]?.let { balance = it }
                val rows = rowsByDay[date].orEmpty()
                balance += rows.sumOf { if (it.status == "Entrada") it.quantity else -it.quantity }
                require(balance >= -0.000001) { "La salida supera las existencias de ${item.name}" }
            }
        }
    }
    private suspend fun balances(day: String, items: List<StockItem>, all: List<Operation>, before: Boolean = false): Map<String, Double> {
        val anchors = dao.openings().filter { it.day <= day }
        return items.associate { item ->
            val anchor = anchors.lastOrNull { it.itemId == item.id }
            item.id to ((anchor?.quantity ?: 0.0) + all.filter { it.module == Module.STOCK && it.itemId == item.id && (it.day < day || (!before && it.day == day)) && (anchor == null || it.day >= anchor.day) }
                .sumOf { if (it.status == "Entrada") it.quantity else -it.quantity }).coerceAtLeast(0.0)
        }
    }
    private suspend fun regenerate(day: String, supervisorOverride: String? = null, startedAt: String? = null, imported: DailyDocument? = null): DailyDocument {
        Rules.day(day)
        val (operators, assets, items) = loadMasters()
        val all = dao.allOperations().map { json.decodeFromString<Operation>(it.payload) }
        val previous = dao.document(day)?.let(::decodeDocument)
        val source = imported ?: previous
        val faults = all.filter { it.module == Module.FAULT }
        val dayAssets = assets.map { asset ->
            val related = faults.filter { it.assetId == asset.id }
            val activeAtDay = related.any { it.day <= day && (it.status != "Cerrada" || (it.resolvedAt.isNotBlank() && resolutionDay(it) > day)) }
            asset.copy(state = if (activeAtDay) "Inactivo temporal" else if (asset.state == "Inactivo temporal" && related.isNotEmpty()) "Activo" else asset.state)
        }
        var doc = DailyCodec.seal(DailyDocument(originId = origin, supervisor = supervisorOverride ?: source?.supervisor ?: supervisor, day = day,
            revision = maxOf(previous?.revision ?: 0, imported?.revision ?: 0) + 1, operators = operators, assets = dayAssets, items = items,
            operations = all.filter { it.day == day }, stockBalances = balances(day, items, all),
            journeyId = source?.let(ContinuationPolicy::identity) ?: UUID.randomUUID().toString(),
            versionVector = if (imported != null) ContinuationPolicy.vector(imported) else ContinuationPolicy.nextVector(previous, origin),
            startedAt = startedAt ?: source?.startedAt.orEmpty(), stockOpeningBalances = balances(day, items, all, before = true),
            carryOverFaults = faults.filter { it.day < day && (it.status != "Cerrada" || (it.resolvedAt.isNotBlank() && resolutionDay(it) >= day)) }))
        if (imported != null && !ContinuationPolicy.sameContent(doc, imported)) {
            doc = DailyCodec.seal(doc.copy(versionVector = ContinuationPolicy.nextVector(imported, origin)))
        }
        val bytes = DailyCodec.encode(doc) // Fail/rollback before commit if the package exceeds its limit.
        dao.putDocument(DocumentRow(day, doc.revision, bytes.toString(Charsets.UTF_8)))
        return doc
    }
    /** DB is authoritative; a failed file write is retried, without suggesting that the saved operation was lost. */
    private fun materialize(doc: DailyDocument) {
        try {
            val dir = File(context.filesDir, "daily").apply { mkdirs() }
            val file = AtomicFile(File(dir, "WorkFlow_${doc.day}.json"))
            val output = file.startWrite()
            try { output.write(DailyCodec.encode(doc)); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
            fileWarning = null
        } catch (_: java.io.IOException) {
            fileWarning = "El registro está guardado. Libera espacio para actualizar su copia de respaldo; no necesitas registrarlo otra vez."
        }
    }
    suspend fun readSharedReport(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Selecciona un archivo mediante Android" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var count = stream.read(chunk)
            while (count != -1) {
                require(buffer.size() + count <= ProtectedReport.MAX_BYTES) { "El informe es demasiado grande" }
                buffer.write(chunk, 0, count); count = stream.read(chunk)
            }
            buffer.toByteArray()
        } ?: error("No se pudo leer el archivo")
        ProtectedReport.validateEnvelope(bytes)
        bytes
    }
    suspend fun receive(doc: DailyDocument): ImportDecision = withContext(Dispatchers.IO) {
        mutex.withLock {
            DailyCodec.decode(json.encodeToString(doc).toByteArray(Charsets.UTF_8))
            db.withTransaction {
                val old = dao.received(doc.originId, doc.day)?.let { json.decodeFromString<DailyDocument>(it.payload) }
                val decision = ImportPolicy.decide(origin, doc, old)
                if (decision != ImportDecision.ALREADY_RECEIVED) {
                    val payload = json.encodeToString(doc)
                    dao.putReceived(ReceivedRow(doc.originId, doc.day, doc.revision, payload, Instant.now().toString()))
                    dao.audit(AuditRow(at = Instant.now().toString(), action = "RECEIVE_$decision", recordId = "${doc.originId}|${doc.day}", before = old?.checksum, after = doc.checksum))
                }
                decision
            }
        }
    }
    suspend fun continuationDecision(doc: DailyDocument): ContinuationDecision = withContext(Dispatchers.IO) {
        val local = dao.document(doc.day)?.let(::decodeDocument)
        if (local == null) ContinuationDecision.LOAD else ContinuationPolicy.decide(local, doc)
    }
    /** Complete, transactional handoff. Keep previous content in the audit before any replacement. */
    suspend fun continueDay(doc: DailyDocument, replaceConflict: Boolean = false): ContinuationDecision = withContext(Dispatchers.IO) {
        mutex.withLock {
            DailyCodec.decode(json.encodeToString(doc).toByteArray(Charsets.UTF_8))
            var decision = ContinuationDecision.LOAD
            val result = db.withTransaction {
                val old = dao.document(doc.day)?.let(::decodeDocument)
                decision = old?.let { ContinuationPolicy.decide(it, doc) } ?: ContinuationDecision.LOAD
                if (decision == ContinuationDecision.ALREADY_LOADED) return@withTransaction null
                require(replaceConflict || decision !in listOf(ContinuationDecision.CONFLICT, ContinuationDecision.OLDER)) { "La jornada tiene cambios distintos o más recientes. Revisa ambas versiones antes de sustituirla." }
                val payload = json.encodeToString(doc)
                dao.audit(AuditRow(at = Instant.now().toString(), action = "CONTINUE_DAY", recordId = doc.day, before = old?.let { json.encodeToString(it) }, after = payload))
                doc.operators.forEach { value -> importMaster(MasterRow(value.id, "operator", value.code.lowercase(java.util.Locale.ROOT).ifBlank { "workflow:auto:${value.id}" }, json.encodeToString(value))) }
                doc.assets.forEach { value -> importMaster(MasterRow(value.id, "asset", value.code.lowercase(), json.encodeToString(value))) }
                doc.items.forEach { value -> importMaster(MasterRow(value.id, "item", value.name.lowercase(), json.encodeToString(value))) }
                val otherIds = dao.allOperations().filter { it.day != doc.day }.map { it.id }.toSet()
                require(doc.operations.none { it.id in otherIds }) { "Un registro recibido pertenece a otra fecha en este teléfono" }
                doc.carryOverFaults.forEach { op ->
                    val existing = dao.operation(op.id)
                    require(existing == null || (existing.day == op.day && existing.module == Module.FAULT.name)) { "Una avería recibida pertenece a otro registro" }
                    dao.putOperation(OperationRow(op.id, op.day, op.module.name, op.naturalKey(), json.encodeToString(op)))
                }
                dao.clearDay(doc.day)
                doc.operations.forEach { op -> dao.putOperation(OperationRow(op.id, op.day, op.module.name, op.naturalKey(), json.encodeToString(op))) }
                doc.items.forEach { item ->
                    val change = doc.operations.filter { it.module == Module.STOCK && it.itemId == item.id }.sumOf { if (it.status == "Entrada") it.quantity else -it.quantity }
                    val opening = doc.stockOpeningBalances[item.id] ?: ((doc.stockBalances[item.id] ?: 0.0) - change)
                    require(opening >= -0.000001 && kotlin.math.abs(opening + change - doc.stockBalances.getValue(item.id)) < 0.00001) { "Las existencias recibidas no coinciden con sus movimientos" }
                    dao.putOpening(StockOpeningRow(doc.day, item.id, opening.coerceAtLeast(0.0)))
                }
                updateWorkshopAssets()
                validateStockHistory(loadMasters().third)
                dao.putReceived(ReceivedRow(doc.originId, doc.day, doc.revision, payload, Instant.now().toString()))
                val current = regenerate(doc.day, imported = doc)
                // Later days must reflect any imported stock movements as well.
                val refreshed = dao.documents().filter { it.day > doc.day || doc.carryOverFaults.any { fault -> fault.day == it.day } }.map { regenerate(it.day) }
                listOf(current) + refreshed
            }
            result?.forEach(::materialize)
            decision
        }
    }
    private suspend fun importMaster(row: MasterRow) {
        val all = dao.masters()
        require(all.none { it.id != row.id && it.kind == row.kind && it.code == row.code }) { "Un nombre o código recibido ya está asignado a otro registro del catálogo. Revisa ese registro antes de cargar la jornada." }
        val old = all.firstOrNull { it.id == row.id }
        require(old == null || old.kind == row.kind) { "Un identificador recibido pertenece a otro catálogo" }
        if (old == null) dao.insertMaster(row) else dao.updateMaster(row)
    }
    private fun resolutionDay(op: Operation): String = Instant.parse(op.resolvedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    suspend fun received(): List<DailyDocument> = withContext(Dispatchers.IO) { dao.allReceived().map { json.decodeFromString<DailyDocument>(it.payload) } }
    suspend fun history(): List<DailyDocument> = withContext(Dispatchers.IO) { dao.documents().map(::decodeDocument) }
}
