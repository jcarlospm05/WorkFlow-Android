package com.workflow.operaciones.ui

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.Asset
import com.workflow.core.Module
import com.workflow.core.Operation
import com.workflow.core.Operator
import com.workflow.core.StockItem
import com.workflow.operaciones.MainActivity
import com.workflow.operaciones.R
import com.workflow.operaciones.WorkflowApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Real Android rendering of unchanged production composables, with synthetic data.
 * Authentication and FLAG_SECURE are bypassed ONLY inside this instrumentation test
 * so the screenshot API can capture pixels. Neither change is in the shipped APK.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class RealScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    private fun id(name: String) = UUID.nameUUIDFromBytes("workflow-screenshot-$name".toByteArray()).toString()
    private val day get() = LocalDate.now().toString()

    @Test fun captureRealScreensAndLogo() {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context,com.workflow.operaciones.data.WorkflowDatabase::class.java).build()
        val repository = com.workflow.operaciones.data.WorkflowRepository(context,database,com.workflow.operaciones.data.TestAgendaAccess())
        runBlocking {
            repository.document(day)
            repository.setSupervisor("Carlos Medina", day)
            val operators = listOf(
                Operator(id("carlos"), "OP-001", "Carlos Medina", "Mañana"),
                Operator(id("jose"), "OP-002", "José Ramírez", "Tarde"),
                Operator(id("luis"), "OP-003", "Luis Pérez", "Noche")
            )
            operators.forEach { repository.addOperator(it, day) }
            val assets = listOf(Asset(id("mula"), "MU-012", "Mula"), Asset(id("grua"), "GR-004", "Grúa"), Asset(id("monta"), "MC-008", "Montacargas", "En taller"))
            assets.forEach { repository.addAsset(it, day) }
            val stock = listOf(StockItem(id("aceite"), "Aceite hidráulico", "litros", 10.0), StockItem(id("guantes"), "Guantes de seguridad", "pares", 20.0), StockItem(id("filtros"), "Filtros de aceite", "unidades", 8.0))
            stock.forEach { repository.addItem(it, day) }
            listOf(12.0, 36.0, 4.0).forEachIndexed { i, amount -> repository.save(Operation(id = id("stock-$i"), day = day, module = Module.STOCK, title = "Existencia inicial · demostración", status = "Entrada", itemId = stock[i].id, quantity = amount, time = "07:00")) }
            repository.save(Operation(id = id("a1"), day = day, module = Module.ATTENDANCE, title = "Asistencia de mañana", status = "Presente", operatorId = operators[0].id, time = "07:00"))
            repository.save(Operation(id = id("a2"), day = day, module = Module.ATTENDANCE, title = "Asistencia de tarde", status = "Tardanza", operatorId = operators[1].id, shift = "Tarde", time = "15:10", extended = true))
            repository.save(Operation(time = "09:00", id = id("a3"), day = day, module = Module.ATTENDANCE, title = "Libre compensatorio", status = "Libre", operatorId = operators[2].id, shift = "Noche"))
            repository.save(Operation(id = id("cena"), day = day, module = Module.MEAL, title = "Solicitud de cena", status = "Solicitada", operatorIds = operators.take(2).map { it.id }, time = "10:45"))
            repository.save(Operation(time = "09:00", id = id("fuel1"), day = day, module = Module.FUEL, title = "Abastecimiento pendiente", status = "Pendiente", assetId = assets[0].id, operatorId = operators[0].id))
            repository.save(Operation(id = id("fuel2"), day = day, module = Module.FUEL, title = "Combustible confirmado", status = "Abastecido", assetId = assets[1].id, quantity = 40.0, time = "08:45"))
            repository.save(Operation(id = id("fault"), day = day, module = Module.FAULT, title = "Fallo de arranque", status = "Abierta", assetId = assets[2].id, time = "09:20", notes = "Equipo en taller. Datos de demostración."))
            repository.save(Operation(id = id("ticket"), day = day, module = Module.TICKET, title = "Imprudencia operativa", status = "Emitido", operatorId = operators[1].id, time = "09:30", notes = "Registro sintético para demostración visual."))
            repository.save(Operation(id = id("plan"), day = day, module = Module.PLAN, title = "Cobertura del turno de tarde", status = "Programado", operatorId = operators[1].id, shift = "Tarde", time = "15:00"))
        }
        lateinit var model: WorkflowViewModel
        compose.activityRule.scenario.onActivity { activity ->
            // Test harness only: render the actual production screen after loading demo data.
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            model = WorkflowViewModel(repository)
            activity.setContent { WorkflowTheme { WorkflowScreen(model) } }
        }
        compose.waitUntil(60_000) { model.document != null && !model.busy }
        // Render the exact vector drawable shipped by the application, not a recreated logo.
        compose.activityRule.scenario.onActivity {
            val logo = requireNotNull(ContextCompat.getDrawable(context, R.drawable.workflow_logo))
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            logo.setBounds(0, 0, 1024, 1024)
            logo.draw(Canvas(bitmap))
            saveImage("logo-original", bitmap)
            bitmap.recycle()
        }
        capture("01-hoy")
        nav("Agenda"); capture("02-agenda")
        nav("Operación"); capture("03-operacion")
        module("Asistencia"); capture("04-asistencia")
        nav("Operación"); module("Comidas"); capture("05-comidas")
        nav("Operación"); module("Existencias"); capture("06-existencias")
        nav("Operación"); module("Combustible"); capture("07-combustible")
        nav("Operación"); module("Averías"); capture("08-averias")
        nav("Operación"); module("Infracciones"); capture("09-infracciones")
        nav("Reportes"); capture("10-reportes")
        nav("Más"); capture("11-configuracion")
        database.close()

    }
    private fun nav(label: String) {
        val index = listOf("Hoy", "Agenda", "Operación", "Reportes", "Más").indexOf(label)
        compose.onNodeWithTag("nav-$index").performClick()
        compose.waitForIdle()
    }
    private fun module(label: String) {
        val entry = Module.entries.first { it.label == label }
        if (compose.onAllNodesWithText("Módulos").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Módulos").performScrollTo().performClick()
        }
        compose.onNodeWithTag("module-${entry.name}").performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun saveImage(name: String, bitmap: Bitmap) {
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Shared media survives AGP's automatic uninstall of the app after instrumentation.
        // This code is test-only; no media/storage permission is added to the app.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WorkFlowScreenshots")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        saveImage(name, bitmap)
        bitmap.recycle()
    }
}
