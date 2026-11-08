package com.workflow.operaciones.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.DatePicker
import android.widget.Button
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.MainActivity
import com.workflow.operaciones.data.WorkflowDatabase
import com.workflow.operaciones.data.WorkflowRepository
import com.workflow.operaciones.export.Reports
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class UserExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WorkflowDatabase
    private lateinit var model: WorkflowViewModel
    private fun prepare(defaultTime: String = "09:00") {
        db = Room.inMemoryDatabaseBuilder(context, WorkflowDatabase::class.java).build()
        compose.activityRule.scenario.onActivity { model = WorkflowViewModel(WorkflowRepository(context, db, com.workflow.operaciones.data.TestAgendaAccess(defaultTime))); model.shareAfterSave = false }
        compose.waitUntil(60_000) { !model.busy && model.document != null }
    }
    private fun show(content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) // Synthetic test evidence only.
            activity.setContent { WorkflowTheme {
                // Production editors are hosted inside the activity, with bottom navigation.
                CompositionLocalProvider(LocalInlineEditor provides true) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { content() }
                }
            } }
        }
        compose.waitForIdle()
    }
    private fun tap(tag: String) {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled(); true }.getOrDefault(false) }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun field(tag: String, value: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performClick().performTextReplacement(value)
    }
    private fun dashboard(tag: String) {
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag(tag))
        tap(tag)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WorkFlowDialogProof")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @After fun close() {
        if (::model.isInitialized) model.repository.transferKeys.clear()
        if (::db.isInitialized) db.close()
    }
    @Test fun task_form_saves_without_an_alarm_hour() {
        prepare()
        val open=mutableStateOf(true)
        val task=Operation(day=model.day,module=Module.TASK,title="Tarea sin alarma",status="Pendiente")
        show { if(open.value)OperationEditor(task,model){open.value=false} else Text("Tarea guardada") }
        compose.onNodeWithTag("operation-time").assertDoesNotExist()
        compose.onNodeWithTag("operation-more").performScrollTo().performClick()
        field("operation-time","")
        tap("dialog-confirm")
        compose.waitUntil(30_000){!model.busy&&!open.value}
        assertEquals("",model.document!!.operations.single{it.id==task.id}.time)
        assertEquals("Pendiente",model.document!!.operations.single{it.id==task.id}.status)
    }
    @Test fun a_task_saves_using_only_its_essential_fields() {
        prepare("")
        val open=mutableStateOf(true)
        val task=Operation(day=model.day,module=Module.TASK,title="Actividad sin detalles",status="Pendiente")
        show { if(open.value)OperationEditor(task,model){open.value=false} else Text("Actividad guardada") }
        compose.onNodeWithTag("operation-notes").assertDoesNotExist()
        compose.onNodeWithTag("operation-time").assertDoesNotExist()
        tap("dialog-confirm")
        compose.waitUntil(30_000){!model.busy&&!open.value}
        assertEquals("",model.document!!.operations.single{it.id==task.id}.time)
        assertEquals("",model.document!!.operations.single{it.id==task.id}.notes)
    }
    private fun nativeCalendar(view:View):DatePicker? {
        if(view is DatePicker)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)nativeCalendar(view.getChildAt(i))?.let{return it}
        return null
    }
    @Test fun a_date_is_chosen_from_the_native_calendar_without_typing() {
        prepare()
        val selected=mutableStateOf("2026-11-07")
        show { WorkDateField("Desde",selected.value,"calendar-field"){selected.value=it} }
        tap("calendar-field")
        compose.waitUntil(10_000){compose.runOnIdle{WindowInspector.getGlobalWindowViews().any{nativeCalendar(it)!=null}}}
        compose.runOnIdle {
            val roots=WindowInspector.getGlobalWindowViews()
            val picker=requireNotNull(roots.firstNotNullOfOrNull{nativeCalendar(it)})
            picker.updateDate(2026,10,8)
            requireNotNull(roots.firstNotNullOfOrNull{it.findViewById<Button>(android.R.id.button1)}).performClick()
        }
        compose.waitUntil(10_000){selected.value=="2026-11-08"}
        compose.onNodeWithTag("calendar-field").assertIsDisplayed()
    }
    @Test fun report_filters_are_optional_and_week_preset_works_without_manual_dates() {
        prepare()
        val open=mutableStateOf(true)
        var query:ReportQuery?=null
        show { if(open.value)ReportOptionsDialog(ReportQuery(ReportType.GENERAL,model.day),model,{open.value=false},{query=it}) else Text("Consulta preparada") }
        compose.onNodeWithText("Todos los operadores").assertDoesNotExist()
        tap("report-filters")
        compose.onNodeWithText("Todos los operadores").assertIsDisplayed()
        tap("report-filters")
        compose.onNodeWithText("Todos los operadores").assertDoesNotExist()
        compose.onNodeWithText("Semana").performClick()
        tap("report-generate")
        compose.waitUntil(10_000){!open.value}
        val start=java.time.LocalDate.parse(model.day).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        assertEquals(start.toString(),query!!.from)
        assertEquals(start.plusDays(6).toString(),query!!.to)
        assertNull(query!!.operatorId)
    }
    @Test fun a_report_is_found_by_its_purpose_and_read_without_first_generating_a_document() {
        prepare()
        val shared=java.io.File(context.cacheDir,"shared")
        val before=shared.listFiles().orEmpty().filter{it.extension in listOf("pdf","jpg")}.map{it.name}.toSet()
        show { WorkflowScreen(model) }
        tap("nav-3")
        field("report-search","domingos")
        compose.onNodeWithTag("report-topic-SUNDAY").performScrollTo().performClick()
        tap("report-generate")
        compose.waitUntil(20_000){runCatching{compose.onNodeWithTag("report-result-list").assertExists();true}.getOrDefault(false)}
        compose.onNodeWithText("Leer resultado").assertIsDisplayed()
        compose.onNodeWithText("No hay registros para esta consulta").assertIsDisplayed()
        assertEquals(before,shared.listFiles().orEmpty().filter{it.extension in listOf("pdf","jpg")}.map{it.name}.toSet())
        compose.onNodeWithText("Cambiar fechas o filtros").performClick()
        compose.onNodeWithTag("report-generate").assertExists()
    }
    @Test fun remaining_report_topics_are_available_without_a_long_model_dropdown() {
        prepare()
        show { WorkflowScreen(model) }
        tap("nav-3")
        compose.onNodeWithTag("report-topic-GENERAL").assertExists()
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag("report-more-topics"))
        compose.onNodeWithTag("report-more-topics").performClick()
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag("report-topic-DAYS_OFF"))
        compose.onNodeWithTag("report-topic-DAYS_OFF").assertIsDisplayed()
    }
    @Test fun multiple_catalog_entries_stay_in_the_form_and_employee_codes_are_optional() {
        prepare()
        listOf("operator", "asset", "item").forEach { kind ->
            val open = mutableStateOf(true)
            show { if (open.value) MasterEditor(kind, model, onDismiss = { open.value = false }) else Text("Lista anterior") }
            val nameTag = if (kind == "asset") "master-code" else "master-name"
            field(nameTag, "Primero $kind")
            if (kind == "asset") {
                compose.onNodeWithText("Seleccionar").performScrollTo().performClick()
                compose.onNodeWithText("Otro").performClick()
            }
            tap("dialog-save-another")
            compose.waitUntil(30_000) { !model.busy && when (kind) { "operator" -> model.operators.size == 1; "asset" -> model.assets.size == 1; else -> model.items.size == 1 } }
            assertTrue(open.value)
            compose.onNodeWithTag(nameTag).assert(SemanticsMatcher("el campo del siguiente registro está vacío") {
                it.config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text?.isEmpty() == true
            })
            if (kind == "operator") {
                assertEquals("", model.operators.single().code)
                capture("ux-personal-continuar")
            }
            field(nameTag, "Segundo $kind")
            tap("dialog-confirm")
            compose.waitUntil(30_000) { !model.busy && !open.value }
            compose.waitForIdle()
            when (kind) {
                "operator" -> { assertEquals(2, model.operators.size); assertTrue(model.operators.all { it.code.isBlank() }) }
                "asset" -> assertEquals(2, model.assets.size)
                else -> assertEquals(2, model.items.size)
            }
        }
        val existing = model.operators.first()
        val editing = mutableStateOf(true)
        show { if (editing.value) MasterEditor("operator", model, onDismiss = { editing.value = false }, operator = existing) }
        compose.onNodeWithTag("dialog-save-another").assertDoesNotExist()
        field("master-name", "Nombre corregido")
        tap("dialog-confirm")
        compose.waitUntil(30_000) { !model.busy && !editing.value }
        assertEquals(2, model.operators.size)
        assertEquals("Nombre corregido", model.operators.first { it.id == existing.id }.name)
    }
    @Test fun consecutive_operations_keep_the_editor_open_and_do_not_interrupt_with_sharing() {
        prepare()
        compose.runOnIdle { model.shareAfterSave = true }
        val open = mutableStateOf(true)
        val draft = Operation(time = "09:00", day = model.day, module = Module.TASK, title = "Primera actividad", status = "Pendiente")
        show { if (open.value) OperationEditor(draft, model) { open.value = false } }
        tap("dialog-save-another")
        compose.waitUntil(30_000) { !model.busy && model.document!!.operations.any { it.title == "Primera actividad" } }
        assertTrue(open.value)
        assertNull(model.shareRequest)
        field("operation-title", "Segunda actividad")
        tap("dialog-confirm")
        compose.waitUntil(30_000) { !model.busy && !open.value }
        assertEquals(2, model.document!!.operations.count { it.title in listOf("Primera actividad", "Segunda actividad") })
        assertNotNull(model.shareRequest)
    }
    @Test fun dashboard_links_open_the_right_modules_and_locking_preserves_the_current_form() {
        prepare()
        val visible = mutableStateOf(true)
        show {
            val holder = rememberSaveableStateHolder()
            if (visible.value) holder.SaveableStateProvider("workflow") { WorkflowScreen(model) } else Text("Bloqueo de prueba")
        }
        capture("ux-inicio-sin-registros")
        dashboard("dashboard-assets")
        compose.onNodeWithTag("catalog-title").assertTextEquals("Equipos")
        tap("nav-0"); dashboard("dashboard-workshop")
        compose.onNodeWithTag("screen-title", useUnmergedTree = true).assertTextEquals("Averías")
        tap("nav-0"); dashboard("dashboard-pending")
        compose.onNodeWithTag("screen-title", useUnmergedTree = true).assertTextEquals("Pendientes del día")
        tap("nav-0"); dashboard("dashboard-attendance")
        compose.onNodeWithTag("screen-title", useUnmergedTree = true).assertTextEquals("Asistencia")
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        compose.runOnIdle { visible.value = true }; compose.waitForIdle()
        compose.onNodeWithTag("screen-title", useUnmergedTree = true).assertTextEquals("Asistencia")
        tap("nav-4")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag("catalog-open-operator"))
        tap("catalog-open-operator"); tap("catalog-add")
        field("master-name", "Borrador que se conserva")
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        compose.runOnIdle { visible.value = true }; compose.waitForIdle()
        compose.onNodeWithTag("master-name").assertTextContains("Borrador que se conserva")
        tap("dialog-cancel")
        compose.onNodeWithTag("catalog-title").assertTextEquals("Personal")
        capture("ux-catalogo-personal")
    }
    @Test fun protected_incoming_day_loads_into_the_dashboard_with_the_agreed_phrase() {
        prepare()
        model.repository.transferKeys.clear()
        val own = model.document!!
        val sender = UUID.randomUUID().toString()
        val incoming = DailyCodec.seal(own.copy(originId = sender, journeyId = UUID.randomUUID().toString(), versionVector = mapOf(sender to 1L), supervisor = "Supervisora de prueba"))
        val phrase = "Frase del equipo para pruebas"
        val secret = phrase.toCharArray()
        val file = try { Reports.protectedReport(context, incoming, secret) } finally { secret.fill('\u0000') }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        show { WorkflowScreen(model) }
        compose.runOnIdle { model.preview(uri) }
        compose.waitUntil(60_000) { !model.busy && model.incomingNeedsKey }
        field("report-key", phrase)
        tap("report-key-submit")
        compose.waitUntil(90_000) { !model.busy && model.preview != null }
        capture("ux-informe-recibido-protegido")
        compose.onNodeWithText("Cargar jornada").performClick()
        compose.waitUntil(60_000) { !model.busy && model.received.isNotEmpty() }
        assertEquals(incoming, model.received.single())
        assertEquals(incoming.day, model.day)
        assertEquals(incoming.operations, model.document!!.operations)
        assertEquals(incoming.supervisor, model.document!!.supervisor)
        assertTrue(model.hasTransferKey)
    }
    @Test fun bottom_tabs_remain_available_inside_a_form_and_its_draft_can_be_resumed() {
        prepare();show { WorkflowScreen(model) }
        tap("nav-4")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag("catalog-open-operator"))
        tap("catalog-open-operator");tap("catalog-add")
        field("master-name","Borrador entre módulos")
        tap("nav-2")
        compose.onNodeWithTag("screen-title",useUnmergedTree=true).assertTextEquals("Operación")
        compose.onNodeWithTag("resume-draft").performScrollTo().performClick()
        compose.onNodeWithTag("master-name").assertTextContains("Borrador entre módulos")
        tap("dialog-confirm")
        compose.waitUntil(30_000){!model.busy&&model.operators.size==1}
        assertEquals("Borrador entre módulos",model.operators.single().name)
    }

    @Test fun an_open_editor_keeps_newer_received_record_data_intact() {
        prepare()
        compose.runOnIdle { model.save(Operation(time = "09:00", day=model.day,module=Module.TASK,title="Primera versión",status="Pendiente"),offerShare=false){} }
        compose.waitUntil(30_000){!model.busy&&model.document!!.operations.isNotEmpty()}
        val original=model.document!!.operations.single()
        val open=mutableStateOf(true)
        show { if(open.value)OperationEditor(original,model){open.value=false} }
        field("operation-title","Mi borrador anterior")
        compose.runOnIdle { model.save(original.copy(title="Versión recibida"),offerShare=false){} }
        compose.waitUntil(30_000){!model.busy&&model.document!!.operations.single().title=="Versión recibida"}
        tap("dialog-confirm")
        compose.waitUntil(30_000){!model.busy&&model.actionError!=null}
        assertTrue(open.value)
        assertEquals("Versión recibida",model.document!!.operations.single().title)
    }
    @Test fun attendance_decisions_move_out_of_the_unmarked_list_and_can_be_corrected() {
        prepare()
        val person=Operator(UUID.randomUUID().toString(),name="Operador de validación")
        kotlinx.coroutines.runBlocking { model.repository.addOperator(person,model.day) }
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(30_000){!model.busy&&model.operators.size==1}
        show { WorkflowScreen(model) }
        tap("nav-2");dashboard("module-ATTENDANCE")
        dashboard("mark-present-${person.id}")
        compose.waitUntil(30_000){!model.busy&&model.document!!.operations.any{it.module==Module.ATTENDANCE}}
        compose.onNodeWithTag("attendance-unmarked-${person.id}").assertDoesNotExist()
        dashboard("attendance-marked-${person.id}")
        compose.onNodeWithContentDescription("Cambiar asistencia").performClick()
        compose.onNodeWithText("Ausencia justificada").performClick()
        compose.waitUntil(30_000){!model.busy&&model.document!!.operations.single{it.module==Module.ATTENDANCE}.status=="Ausencia justificada"}
        assertEquals(0,DailySummary.present(model.document!!))
        assertEquals(1,model.document!!.operations.count{it.module==Module.ATTENDANCE})
        compose.onNodeWithTag("attendance-unmarked-${person.id}").assertDoesNotExist()
    }
    @Test fun saved_meals_open_as_names_and_tasks_route_to_their_operational_module() {
        prepare()
        val person=Operator(UUID.randomUUID().toString(),name="Persona de solicitud")
        kotlinx.coroutines.runBlocking {
            model.repository.addOperator(person,model.day)
            model.repository.save(Operation(day=model.day,module=Module.MEAL,title="Cena",status="Solicitada",operatorIds=listOf(person.id)))
            model.repository.save(Operation(day=model.day,module=Module.TASK,title="Revisión de asistencia",status="Pendiente",targetModule=Module.ATTENDANCE))
        }
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(30_000){!model.busy&&model.operators.size==1}
        show { WorkflowScreen(model) }
        tap("nav-2");dashboard("module-MEAL")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasText("Modificar lista"))
        compose.onNodeWithText("Modificar lista").assertIsDisplayed()
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasText(person.name))
        compose.onNodeWithText(person.name).assertIsDisplayed()
        tap("nav-2")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasTestTag("module-ATTENDANCE"))
        compose.onNodeWithTag("pending-ATTENDANCE",useUnmergedTree=true).assertIsDisplayed().assertTextContains("1")
        tap("nav-1")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasText("Revisión de asistencia"))
        compose.onNodeWithText("Revisión de asistencia").performClick()
        compose.onNodeWithTag("screen-title",useUnmergedTree=true).assertTextEquals("Asistencia")
        compose.onNodeWithText("Mañana").assertDoesNotExist()
        tap("nav-1")
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasText("Completadas"))
        compose.onNodeWithText("Completadas").performClick()
        compose.onNodeWithTag("workflow-list").performScrollToNode(hasText("Solicitar cena"))
        compose.onNodeWithText("Solicitar cena").performClick()
        compose.onNodeWithTag("operation-title").assertTextContains("Solicitar cena")
        compose.onNodeWithText("Módulo: Comidas").assertExists()
    }

}
