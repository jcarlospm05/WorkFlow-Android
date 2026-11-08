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
    @Test fun unconfigured_alarms_allow_start_and_operations_with_tasks_without_hours()=runBlocking {
        val optional=WorkflowRepository(context,db,TestAgendaAccess(""))
        val person=Operator(UUID.randomUUID().toString(),name="Persona sintética de agenda")
        optional.addOperator(person,first)
        val manual=Operation(day=first,module=Module.TASK,title="Tarea sin alarma",status="Pendiente")
        optional.save(manual)
        optional.startDay(first,setOf("dinner"))
        optional.save(Operation(day=first,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=person.id))
        val doc=optional.document(first)
        assertTrue(doc.startedAt.isNotEmpty())
        assertTrue(doc.operations.filter{it.module==Module.TASK}.all{it.time.isEmpty()})
        assertEquals(2,doc.operations.count{it.module==Module.TASK})
        assertTrue(doc.operations.any{it.module==Module.ATTENDANCE})
    }
    @Test fun a_task_can_be_saved_and_edited_without_an_hour_but_invalid_hours_are_rejected()=runBlocking {
        val task=Operation(day=first,module=Module.TASK,title="Sin hora",status="Pendiente")
        repository.save(task)
        repository.save(task.copy(title="Tarea editada sin alarma"))
        assertEquals("",repository.document(first).operations.single().time)
        try{repository.save(task.copy(time="25:90"));fail("La hora opcional debe ser válida si se escribe")}catch(_:IllegalArgumentException){}
        assertEquals("Tarea editada sin alarma",repository.document(first).operations.single().title)
    }
    @Test fun incoming_started_day_can_be_continued_without_alarm_configuration()=runBlocking {
        val optional=WorkflowRepository(context,db,TestAgendaAccess(""))
        val incoming=DailyCodec.seal(optional.document(first).copy(originId=UUID.randomUUID().toString(),journeyId=UUID.randomUUID().toString(),versionVector=emptyMap(),startedAt="2026-11-03T07:00:00Z",operations=listOf(Operation(day=first,module=Module.TASK,title="Continuar sin alarma",status="Pendiente"))))
        assertEquals(ContinuationDecision.LOAD,optional.continueDay(incoming))
        val task=optional.document(first).operations.single()
        assertEquals("",task.time)
        optional.save(task.copy(status="Completada"))
        assertEquals("Completada",optional.document(first).operations.single().status)
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
