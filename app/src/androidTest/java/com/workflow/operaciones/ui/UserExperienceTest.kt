package com.workflow.operaciones.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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
    private fun prepare() {
        db = Room.inMemoryDatabaseBuilder(context, WorkflowDatabase::class.java).build()
        compose.activityRule.scenario.onActivity { model = WorkflowViewModel(WorkflowRepository(context, db)); model.shareAfterSave = false }
        compose.waitUntil(60_000) { !model.busy && model.document != null }
    }
    private fun show(content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) // Synthetic test evidence only.
            activity.setContent { WorkflowTheme(content) }
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
            compose.onNodeWithTag(nameTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
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
        val draft = Operation(day = model.day, module = Module.TASK, title = "Primera actividad", status = "Pendiente")
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
        dashboard("dashboard-assets")
        compose.onNodeWithTag("catalog-title").assertTextEquals("Equipos")
        tap("nav-0"); dashboard("dashboard-stock")
        compose.onNodeWithTag("screen-title").assertTextEquals("Existencias")
        tap("nav-0"); dashboard("dashboard-pending")
        compose.onNodeWithTag("screen-title").assertTextEquals("Pendientes del día")
        tap("nav-0"); dashboard("dashboard-attendance")
        compose.onNodeWithTag("screen-title").assertTextEquals("Asistencia")
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        compose.runOnIdle { visible.value = true }; compose.waitForIdle()
        compose.onNodeWithTag("screen-title").assertTextEquals("Asistencia")
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
    @Test fun protected_incoming_report_opens_with_the_agreed_phrase_without_overwriting_own_data() {
        prepare()
        model.repository.transferKeys.clear()
        val own = model.document!!
        val incoming = DailyCodec.seal(own.copy(originId = UUID.randomUUID().toString(), supervisor = "Supervisora de prueba"))
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
        compose.onNodeWithText("Guardar informe").performClick()
        compose.waitUntil(60_000) { !model.busy && model.received.isNotEmpty() }
        assertEquals(incoming, model.received.single())
        assertEquals(own, model.document)
        assertTrue(model.hasTransferKey)
    }
}
