package com.workflow.operaciones.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.data.WorkflowRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class WorkflowViewModel(val repository: WorkflowRepository) : ViewModel() {
    var day by mutableStateOf(LocalDate.now().toString()); private set
    var document by mutableStateOf<DailyDocument?>(null); private set
    var received by mutableStateOf<List<DailyDocument>>(emptyList()); private set
    var history by mutableStateOf<List<DailyDocument>>(emptyList()); private set
    var operators by mutableStateOf<List<Operator>>(emptyList()); private set
    var assets by mutableStateOf<List<Asset>>(emptyList()); private set
    var items by mutableStateOf<List<StockItem>>(emptyList()); private set
    var preview by mutableStateOf<DailyDocument?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var shareRequest by mutableStateOf<DailyDocument?>(null)
    var shareAfterSave by mutableStateOf(true)
    init { refresh() }
    private suspend fun reload() {
        document = repository.document(day)
        val master = repository.masters(); operators = master.first; assets = master.second; items = master.third
        received = repository.received(); history = repository.history()
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try { block() } catch (e: Exception) { message = if (e is android.database.sqlite.SQLiteConstraintException) "Esa ficha o código ya existe. Usa un código único." else e.message ?: "No se completó la operación" }
            finally { busy = false }
        }
    }
    fun refresh() = action { reload() }
    fun chooseDay(value: String) = action { Rules.day(value); day = value; reload() }
    fun save(op: Operation, done: () -> Unit) = action {
        repository.save(op); reload(); done()
        message = repository.fileWarning ?: "Guardado. JSON diario actualizado a revisión ${document?.revision}"
        if (shareAfterSave) shareRequest = document
    }
    fun addOperator(value: Operator, done: () -> Unit) = action { repository.addOperator(value, day); reload(); done() }
    fun addAsset(value: Asset, done: () -> Unit) = action { repository.addAsset(value, day); reload(); done() }
    fun addItem(value: StockItem, done: () -> Unit) = action { repository.addItem(value, day); reload(); done() }
    fun supervisor(name: String) = action { repository.setSupervisor(name, day); reload(); message = "Responsable actualizado" }
    fun preview(uri: Uri) = action { preview = repository.preview(uri) }
    fun dismissPreview() { preview = null }
    fun acceptPreview() = action {
        val doc = preview ?: return@action
        val result = repository.receive(doc)
        preview = null; reload()
        message = when (result) { ImportDecision.NEW -> "Informe recibido"; ImportDecision.UPDATE -> "Informe actualizado"; ImportDecision.ALREADY_RECEIVED -> "Este informe ya estaba recibido; no se duplicó" }
    }
}
