package com.workflow.operaciones.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.*
import com.workflow.core.Module
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, WorkflowDatabase::class.java).build()
    private val repository = WorkflowRepository(context, db)
    private val day = "2026-11-05"
    @After fun close() { db.close() }
    @Test fun fresh_install_has_no_preloaded_business_records() = runBlocking {
        val doc = repository.document(day)
        assertTrue(doc.operators.isEmpty()); assertTrue(doc.assets.isEmpty()); assertTrue(doc.items.isEmpty())
        assertTrue(doc.operations.isEmpty()); assertTrue(doc.stockBalances.isEmpty())
        assertTrue(repository.received().isEmpty())
    }
    @Test fun several_operators_without_employee_codes_can_be_added_and_edited() = runBlocking {
        val first = Operator(UUID.randomUUID().toString(), name = "Operador uno")
        val second = Operator(UUID.randomUUID().toString(), name = "Operador dos")
        repository.addOperator(first, day); repository.addOperator(second, day)
        repository.addOperator(first.copy(name = "Operador corregido"), day)
        val doc = repository.document(day)
        assertEquals(2, doc.operators.size)
        assertTrue(doc.operators.all { it.code.isEmpty() })
        assertEquals("Operador corregido", doc.operators.first { it.id == first.id }.name)
        assertEquals(doc, DailyCodec.decode(DailyCodec.encode(doc)))
    }
    @Test fun assigned_employee_codes_cannot_be_reused() = runBlocking {
        repository.addOperator(Operator(UUID.randomUUID().toString(), "OP-21", "Operador uno"), day)
        try {
            repository.addOperator(Operator(UUID.randomUUID().toString(), "op-21", "Operador dos"), day)
            fail("Debe conservar la unicidad de un código asignado")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, repository.masters().first.size)
    }
    @Test fun corrections_update_json_without_duplicating_attendance() = runBlocking {
        val operator = Operator(UUID.randomUUID().toString(), "TEST", "Operador sintético")
        repository.addOperator(operator, day)
        val before = repository.document(day)
        val op = Operation(day = day, module = Module.ATTENDANCE, title = "Asistencia", status = "Presente", operatorId = operator.id)
        repository.save(op)
        repository.save(op.copy(id = UUID.randomUUID().toString(), status = "Tardanza"))
        val after = repository.document(day)
        assertEquals(1, after.operations.count { it.module == Module.ATTENDANCE })
        assertEquals("Tardanza", after.operations.first { it.module == Module.ATTENDANCE }.status)
        assertTrue(after.revision > before.revision)
        assertEquals(after, DailyCodec.decode(DailyCodec.encode(after)))
    }
    @Test fun stock_insufficiency_rolls_back_operation_and_revision() = runBlocking {
        val item = StockItem(UUID.randomUUID().toString(), "Filtro sintético", "unidades", 1.0)
        repository.addItem(item, day)
        val before = repository.document(day)
        try {
            repository.save(Operation(day = day, module = Module.STOCK, title = "Salida", status = "Salida", itemId = item.id, quantity = 3.0))
            fail("La salida debió rechazarse")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.document(day))
    }
    @Test fun import_never_overwrites_own_operations_and_repeat_is_idempotent() = runBlocking {
        val own = repository.document(day)
        val external = DailyCodec.seal(own.copy(originId = UUID.randomUUID().toString(), supervisor = "Otro supervisor"))
        assertEquals(ImportDecision.NEW, repository.receive(external))
        assertEquals(ImportDecision.ALREADY_RECEIVED, repository.receive(external))
        assertEquals(own, repository.document(day))
        assertEquals(1, repository.received().size)
    }
    @Test fun closed_day_requires_reopening_before_changes() = runBlocking {
        repository.document(day)
        repository.save(Operation(day = day, module = Module.CLOSE, title = "Cierre", status = "Cerrada", notes = "Se conservan los pendientes sintéticos de prueba"))
        try {
            repository.save(Operation(day = day, module = Module.TASK, title = "Cambio", status = "Pendiente"))
            fail("No debe permitir cambios")
        } catch (_: IllegalArgumentException) { }
        repository.save(Operation(day = day, module = Module.CLOSE, title = "Reapertura", status = "Reabierta"))
        repository.save(Operation(day = day, module = Module.TASK, title = "Cambio", status = "Pendiente"))
        assertTrue(repository.document(day).operations.any { it.title == "Cambio" })
    }
}
