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
class AgendaPolicyTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val db=Room.inMemoryDatabaseBuilder(context,WorkflowDatabase::class.java).build()
    private val repository=WorkflowRepository(context,db,TestAgendaAccess())
    private val first="2026-11-03"
    private val next="2026-11-06"
    @After fun close(){db.close()}
    @Test fun missing_permissions_prevent_start_without_creating_tasks_or_erasing_data()=runBlocking {
        val locked=WorkflowRepository(context,db,TestAgendaAccess(false))
        val before=locked.document(first)
        try{locked.startDay(first,setOf("dinner"));fail("Debe exigir permisos")}catch(_:IllegalArgumentException){}
        assertEquals(before,locked.document(first))
        assertTrue(locked.document(first).operations.isEmpty())
    }
    @Test fun a_pending_task_requires_a_valid_alarm_hour()=runBlocking {
        val before=repository.document(first)
        try{repository.save(Operation(day=first,module=Module.TASK,title="Sin hora",status="Pendiente"));fail("Debe exigir hora")}catch(_:IllegalArgumentException){}
        assertEquals(before,repository.document(first))
    }
    @Test fun previous_day_must_be_closed_and_its_tasks_completed_but_days_off_are_skipped()=runBlocking {
        repository.startDay(first,setOf("dinner"))
        repository.save(Operation(day=first,module=Module.CLOSE,title="Cierre",status="Cerrada",notes="Tarea pendiente documentada"))
        try{repository.startDay(next,emptySet());fail("La tarea anterior está pendiente")}catch(_:IllegalArgumentException){}
        repository.save(Operation(day=first,module=Module.CLOSE,title="Reapertura",status="Reabierta"))
        val task=repository.document(first).operations.single{it.module==Module.TASK}
        repository.save(task.copy(status="Completada"))
        repository.save(Operation(day=first,module=Module.CLOSE,title="Cierre",status="Cerrada"))
        repository.startDay(next,emptySet())
        assertTrue(repository.document(next).startedAt.isNotEmpty())
        assertEquals(setOf(first,next),repository.history().map{it.day}.toSet())
    }
    @Test fun future_preparation_is_allowed_but_execution_and_import_cannot_bypass_previous_day()=runBlocking {
        repository.startDay(first,emptySet())
        repository.save(Operation(day=next,module=Module.TASK,title="Preparar mañana",status="Pendiente",time="10:30"))
        assertTrue(repository.document(next).startedAt.isEmpty())
        val person=Operator(UUID.randomUUID().toString(),name="Persona sintética de agenda")
        repository.addOperator(person,next)
        try{repository.save(Operation(day=next,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=person.id));fail("No debe saltar el cierre anterior")}catch(_:IllegalArgumentException){}
        val before=repository.document(next)
        val incoming=DailyCodec.seal(before.copy(originId=UUID.randomUUID().toString(),startedAt="2026-11-06T07:00:00Z"))
        try{repository.continueDay(incoming,replaceConflict=true);fail("Ni sustituir debe saltar el cierre")}catch(_:IllegalArgumentException){}
        assertEquals(before,repository.document(next))
    }
}
