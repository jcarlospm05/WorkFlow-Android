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

enum class ShareKind { PROGRESS, REPORT, CLOSE }

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
    var shareAfterSave by mutableStateOf(repository.shareAfterSave)
    fun setOfferShare(value: Boolean) { repository.shareAfterSave = value; shareAfterSave = value }
    var incomingDecision by mutableStateOf<ContinuationDecision?>(null); private set
    var loadedDayEvent by mutableIntStateOf(0); private set
    var agendaNavigationEvent by mutableIntStateOf(0); private set
    fun openAgendaDay(value: String) = action { Rules.day(value);day=value;reload();agendaNavigationEvent++ }
    var hasTransferKey by mutableStateOf(false); private set
    var needsTransferSetup by mutableStateOf(false); private set
    var transferKeyError by mutableStateOf<String?>(null); private set
    var incomingNeedsKey by mutableStateOf(false); private set
    var incomingKeyError by mutableStateOf<String?>(null); private set
    private var pendingIncoming: ByteArray? = null
    private data class Outgoing(val report: DailyDocument, val kind: ShareKind, val whatsapp: Boolean)
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
                val detail = when (e) {
                    is android.database.sqlite.SQLiteConstraintException -> "Ese nombre o código ya está en uso. Revisa el registro."
                    is android.database.sqlite.SQLiteException -> "No se pudo guardar el cambio. Revisa el espacio disponible en el teléfono e inténtalo de nuevo."
                    is java.time.DateTimeException -> "Revisa la fecha y vuelve a intentarlo."
                    is SecurityException -> "No se pudo abrir el archivo. Selecciónalo de nuevo desde tus documentos."
                    is java.io.IOException -> "No se pudo acceder al archivo. Revisa que esté disponible y que haya espacio en el teléfono."
                    else -> e.message ?: "No se completó la operación"
                }
                message = detail; actionError = detail
            }
            finally { busy = false }
        }
    }
    fun refresh() = action { reload() }
    fun clearActionError() { actionError = null }
    fun chooseDay(value: String) = action { Rules.day(value); day = value; reload() }
    fun startDay(selected: Set<String>, times: Map<String,String> = emptyMap(), done: () -> Unit) = action { repository.startDay(day, selected, times); reload(); message = "Jornada iniciada. Tus actividades están en la agenda."; done() }
    fun save(op: Operation, offerShare: Boolean = true, expectedUpdatedAt: String? = null, done: () -> Unit) = action {
        repository.save(op, expectedUpdatedAt); reload(); done()
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
            val decoded = withContext(Dispatchers.IO) { ProtectedReport.decode(bytes, password) }
            incomingDecision = repository.continuationDecision(decoded); preview = decoded
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
            cancelIncoming(); incomingDecision = repository.continuationDecision(document); preview = document
        } catch (e: Exception) {
            incomingKeyError = if (e is IllegalArgumentException) e.message else "No se pudo abrir el informe. Inténtalo de nuevo."
        } finally { secret.fill('\u0000') }
    }
    fun requestTransferSetup() { pendingOutgoing = null; transferKeyError = null; needsTransferSetup = true }
    fun dismissTransferSetup() { pendingOutgoing = null; transferKeyError = null; needsTransferSetup = false }
    private suspend fun send(context: Context, outgoing: Outgoing) {
        val files: List<java.io.File>? = withContext(Dispatchers.IO) {
            val protected: java.io.File? = if (outgoing.kind != ShareKind.REPORT) {
                val password = repository.transferKeys.read()
                if(password==null) null else try { Reports.protectedReport(context,outgoing.report,password) } finally { password.fill('\u0000') }
            } else null
            if(outgoing.kind!=ShareKind.REPORT && protected==null) null else buildList<java.io.File> {
                if(outgoing.kind!=ShareKind.PROGRESS) {
                    val query=ReportQuery(if(outgoing.kind==ShareKind.CLOSE)ReportType.CLOSE else ReportType.GENERAL,outgoing.report.day)
                    add(Reports.pdf(context,ReportBuilder.build(query,listOf(outgoing.report))))
                    addAll(Reports.csv(context,query,listOf(outgoing.report)))
                }
                protected?.let { add(it) }
            }
        }
        if(files==null) {
            hasTransferKey=false;pendingOutgoing=outgoing;transferKeyError=null;needsTransferSetup=true;shareRequest=null;return
        }
        Reports.share(context,files,outgoing.whatsapp)
        pendingOutgoing=null;shareRequest=null
    }
    fun shareDay(context: Context, report: DailyDocument, kind: ShareKind, whatsapp: Boolean = true) = action { send(context,Outgoing(report,kind,whatsapp)) }
    fun shareReport(context: Context, report: DailyDocument, pdf: Boolean, whatsapp: Boolean) = shareDay(context,report,if(pdf)ShareKind.REPORT else ShareKind.PROGRESS,whatsapp)
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
    fun dismissPreview() { preview = null; incomingDecision = null }
    fun acceptPreview(replaceConflict: Boolean = false) = action {
        val doc = preview ?: return@action
        val result = repository.continueDay(doc, replaceConflict)
        day = doc.day; preview = null; incomingDecision = null; reload(); loadedDayEvent++
        message = if (result == ContinuationDecision.ALREADY_LOADED) "Esta versión ya estaba cargada. Puedes continuar la jornada." else "Jornada del ${doc.day} cargada. Puedes continuar la operación."
    }
    override fun onCleared() { pendingIncoming?.fill(0); super.onCleared() }
}
