package com.workflow.operaciones.ui

import android.net.Uri
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.data.WorkflowRepository
import com.workflow.operaciones.export.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var actionError by mutableStateOf<String?>(null); private set
    var shareRequest by mutableStateOf<DailyDocument?>(null)
    var shareAfterSave by mutableStateOf(true)
    var hasTransferKey by mutableStateOf(false); private set
    var needsTransferSetup by mutableStateOf(false); private set
    var transferKeyError by mutableStateOf<String?>(null); private set
    var incomingNeedsKey by mutableStateOf(false); private set
    var incomingKeyError by mutableStateOf<String?>(null); private set
    private var pendingIncoming: ByteArray? = null
    private data class Outgoing(val report: DailyDocument, val pdf: Boolean, val whatsapp: Boolean)
    private var pendingOutgoing: Outgoing? = null
    init { refresh() }
    private suspend fun reload() {
        document = repository.document(day)
        val master = repository.masters(); operators = master.first; assets = master.second; items = master.third
        received = repository.received(); history = repository.history()
        hasTransferKey = repository.transferKeys.isConfigured()
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            actionError = null
            try { block() } catch (e: Exception) {
                val detail = if (e is android.database.sqlite.SQLiteConstraintException) "Ese nombre o código ya está en uso. Revisa el registro." else e.message ?: "No se completó la operación"
                message = detail; actionError = detail
            }
            finally { busy = false }
        }
    }
    fun refresh() = action { reload() }
    fun clearActionError() { actionError = null }
    fun chooseDay(value: String) = action { Rules.day(value); day = value; reload() }
    fun save(op: Operation, offerShare: Boolean = true, done: () -> Unit) = action {
        repository.save(op); reload(); done()
        message = repository.fileWarning ?: "Registro guardado. Tu informe del día está actualizado."
        if (shareAfterSave && offerShare) shareRequest = document
    }
    fun addOperator(value: Operator, done: () -> Unit) = action { repository.addOperator(value, day); reload(); done() }
    fun addAsset(value: Asset, done: () -> Unit) = action { repository.addAsset(value, day); reload(); done() }
    fun addItem(value: StockItem, done: () -> Unit) = action { repository.addItem(value, day); reload(); done() }
    fun supervisor(name: String) = action { repository.setSupervisor(name, day); reload(); message = "Responsable actualizado" }
    fun preview(uri: Uri) = action {
        cancelIncoming()
        val bytes = repository.readSharedReport(uri)
        pendingIncoming = bytes
        val password = withContext(Dispatchers.IO) { repository.transferKeys.read() }
        hasTransferKey = repository.transferKeys.isConfigured()
        if (password == null) { incomingNeedsKey = true; return@action }
        try {
            preview = withContext(Dispatchers.IO) { ProtectedReport.decode(bytes, password) }
            pendingIncoming?.fill(0); pendingIncoming = null
        } catch (_: IllegalArgumentException) {
            incomingNeedsKey = true
            incomingKeyError = "Este informe usa otra clave. Pídesela a quien lo compartió."
        } finally { password.fill('\u0000') }
    }
    fun cancelIncoming() {
        pendingIncoming?.fill(0); pendingIncoming = null
        incomingNeedsKey = false; incomingKeyError = null
    }
    fun unlockIncoming(password: String, remember: Boolean) = action {
        val secret = password.toCharArray()
        try {
            val bytes = pendingIncoming ?: return@action
            val document = withContext(Dispatchers.IO) {
                val decoded = ProtectedReport.decode(bytes, secret)
                if (remember) repository.transferKeys.save(secret)
                decoded
            }
            hasTransferKey = repository.transferKeys.isConfigured()
            cancelIncoming(); preview = document
        } catch (e: Exception) {
            incomingKeyError = if (e is IllegalArgumentException) e.message else "No se pudo abrir el informe. Inténtalo de nuevo."
        } finally { secret.fill('\u0000') }
    }
    fun requestTransferSetup() { pendingOutgoing = null; transferKeyError = null; needsTransferSetup = true }
    fun dismissTransferSetup() { pendingOutgoing = null; transferKeyError = null; needsTransferSetup = false }
    private suspend fun send(context: Context, outgoing: Outgoing) {
        val file = withContext(Dispatchers.IO) {
            if (outgoing.pdf) Reports.pdf(context, outgoing.report)
            else {
                val password = repository.transferKeys.read()
                if (password == null) null else try { Reports.protectedReport(context, outgoing.report, password) }
                    finally { password.fill('\u0000') }
            }
        }
        if (file == null) {
            hasTransferKey = false; pendingOutgoing = outgoing; transferKeyError = null
            needsTransferSetup = true; shareRequest = null
            return
        }
        Reports.share(context, file, outgoing.whatsapp)
        pendingOutgoing = null; shareRequest = null
    }
    fun shareReport(context: Context, report: DailyDocument, pdf: Boolean, whatsapp: Boolean) = action {
        send(context, Outgoing(report, pdf, whatsapp))
    }
    fun configureTransferKey(context: Context, password: String) = action {
        val secret = password.toCharArray()
        try {
            withContext(Dispatchers.IO) { repository.transferKeys.save(secret) }
            hasTransferKey = true; needsTransferSetup = false; transferKeyError = null
            val outgoing = pendingOutgoing
            if (outgoing != null) send(context, outgoing) else message = "Clave guardada en este teléfono."
        } catch (_: Exception) {
            if (needsTransferSetup) transferKeyError = "No se pudo guardar la clave. Inténtalo de nuevo."
            else message = "La clave está guardada, pero no se pudo compartir el informe. Inténtalo desde Reportes."
        } finally { secret.fill('\u0000') }
    }
    fun dismissPreview() { preview = null }
    fun acceptPreview() = action {
        val doc = preview ?: return@action
        val result = repository.receive(doc)
        preview = null; reload()
        message = when (result) { ImportDecision.NEW -> "Informe recibido"; ImportDecision.UPDATE -> "Informe actualizado"; ImportDecision.ALREADY_RECEIVED -> "Este informe ya estaba recibido; no se duplicó" }
    }
    override fun onCleared() { pendingIncoming?.fill(0); super.onCleared() }
}
