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
import java.time.LocalDate
import java.util.UUID

class WorkflowRepository(private val context: Context, private val db: WorkflowDatabase) {
    private val dao = db.dao()
    private val mutex = Mutex()
    @Volatile var fileWarning: String? = null
        private set
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
    val origin: String = prefs.getString("origin", null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("origin", it).commit()) }
    val supervisor: String get() = prefs.getString("supervisor", "Supervisor").orEmpty()
    private val json get() = DailyCodec.json
    suspend fun setSupervisor(name: String, day: String) = withContext(Dispatchers.IO) {
        Rules.text(name, 100)
        mutex.withLock {
            check(prefs.edit().putString("supervisor", name.trim()).commit())
            val doc = db.withTransaction { regenerate(day) }
            materialize(doc)
        }
    }
    private fun decodeDocument(row: DocumentRow) = json.decodeFromString<DailyDocument>(row.payload)
    suspend fun document(day: String): DailyDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            Rules.day(day)
            val doc = dao.document(day)?.let(::decodeDocument) ?: db.withTransaction {
                seedTasks(day)
                regenerate(day)
            }
            materialize(doc)
            doc
        }
    }
    suspend fun masters(): Triple<List<Operator>, List<Asset>, List<StockItem>> = withContext(Dispatchers.IO) { loadMasters() }
    private suspend fun loadMasters(): Triple<List<Operator>, List<Asset>, List<StockItem>> {
        val rows = dao.masters()
        return Triple(rows.filter { it.kind == "operator" }.map { json.decodeFromString<Operator>(it.payload) },
            rows.filter { it.kind == "asset" }.map { json.decodeFromString<Asset>(it.payload) },
            rows.filter { it.kind == "item" }.map { json.decodeFromString<StockItem>(it.payload) })
    }
    suspend fun addOperator(value: Operator, day: String) { Rules.operator(value); addMaster(MasterRow(value.id, "operator", value.code.lowercase(), json.encodeToString(value)), day) }
    suspend fun addAsset(value: Asset, day: String) { Rules.asset(value); addMaster(MasterRow(value.id, "asset", value.code.lowercase(), json.encodeToString(value)), day) }
    suspend fun addItem(value: StockItem, day: String) { Rules.item(value); addMaster(MasterRow(value.id, "item", value.name.lowercase(), json.encodeToString(value)), day) }
    private suspend fun addMaster(row: MasterRow, day: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val doc = db.withTransaction {
                val old = dao.masters().firstOrNull { it.id == row.id }
                require(old == null || old.kind == row.kind)
                if (old == null) dao.insertMaster(row) else dao.updateMaster(row)
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
                require(!closed || (value.module == Module.CLOSE && value.status == "Reabierta")) { "Reabre la jornada antes de corregirla" }
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
                // Stock ledger is global: editing an earlier day must not make any later balance negative.
                validateStockHistory(items)
                dao.audit(AuditRow(at = normalized.updatedAt, action = if (previous == null) "CREATE" else "UPDATE", recordId = normalized.id, before = previous?.payload, after = payload))
                val affectedDays = if (value.module == Module.STOCK) (dao.documents().filter { it.day >= value.day }.map { it.day } + value.day).distinct() else listOf(value.day)
                affectedDays.map { regenerate(it) }
            }
            docs.forEach(::materialize)
        }
    }
    private suspend fun seedTasks(day: String) {
        val tasks = mutableListOf(
            "07:00" to "Registrar asistencia de mañana",
            "09:00" to "Revisar existencias y equipos",
            "10:30" to "Confirmar si corresponde combustible",
            "11:00" to "Preparar solicitud de cenas",
            "15:00" to "Registrar asistencia de tarde / noche",
            "17:00" to "Revisar pendientes y cierre"
        )
        if (LocalDate.parse(day).dayOfWeek.value == 5) {
            tasks += "11:30" to "Solicitar almuerzos del sábado y domingo"
            tasks += "12:00" to "Programar fin de semana y libres compensatorios"
        }
        tasks.forEach { (time, title) ->
            val id = UUID.nameUUIDFromBytes("$origin|$day|$title".toByteArray()).toString()
            if (dao.operation(id) == null) {
                val op = Operation(id = id, day = day, module = Module.TASK, title = title, status = "Pendiente", time = time)
                val payload = json.encodeToString(op)
                dao.putOperation(OperationRow(id, day, Module.TASK.name, null, payload))
                dao.audit(AuditRow(at = op.updatedAt, action = "CREATE_PLANNER_TASK", recordId = id, before = null, after = payload))
            }
        }
    }
    private suspend fun validateStockHistory(items: List<StockItem>) {
        val stock = dao.allOperations().map { json.decodeFromString<Operation>(it.payload) }.filter { it.module == Module.STOCK }
        items.forEach { item ->
            var balance = 0.0
            stock.filter { it.itemId == item.id }.groupBy { it.day }.toSortedMap().forEach { (_, rows) ->
                balance += rows.sumOf { if (it.status == "Entrada") it.quantity else -it.quantity }
                require(balance >= -0.000001) { "La salida supera las existencias de ${item.name}" }
            }
        }
    }
    private suspend fun regenerate(day: String): DailyDocument {
        Rules.day(day)
        val (operators, assets, items) = loadMasters()
        val all = dao.allOperations().map { json.decodeFromString<Operation>(it.payload) }
        val balances = items.associate { item -> item.id to all.filter { it.day <= day && it.module == Module.STOCK && it.itemId == item.id }
            .sumOf { if (it.status == "Entrada") it.quantity else -it.quantity }.coerceAtLeast(0.0) }
        val doc = DailyCodec.seal(DailyDocument(originId = origin, supervisor = supervisor, day = day,
            revision = (dao.document(day)?.revision ?: 0) + 1, operators = operators, assets = assets, items = items,
            operations = all.filter { it.day == day }, stockBalances = balances))
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
            fileWarning = "Datos guardados en SQLite; falta espacio o acceso para actualizar el archivo JSON privado. Se reintentará al abrir el informe."
        }
    }
    suspend fun preview(uri: Uri): DailyDocument = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Selecciona un archivo mediante Android" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var count = stream.read(chunk)
            while (count != -1) {
                require(buffer.size() + count <= DailyCodec.MAX_BYTES) { "Informe mayor de 4 MB" }
                buffer.write(chunk, 0, count); count = stream.read(chunk)
            }
            buffer.toByteArray()
        } ?: error("No se pudo leer el archivo")
        DailyCodec.decode(bytes)
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
    suspend fun received(): List<DailyDocument> = withContext(Dispatchers.IO) { dao.allReceived().map { json.decodeFromString<DailyDocument>(it.payload) } }
    suspend fun history(): List<DailyDocument> = withContext(Dispatchers.IO) { dao.documents().map(::decodeDocument) }
}
