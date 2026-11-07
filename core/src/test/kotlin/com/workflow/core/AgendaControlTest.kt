package com.workflow.core

import kotlin.test.*
import java.time.*
import java.util.UUID

class AgendaControlTest {
    private fun doc(day:String,started:Boolean=true,closed:Boolean=false,pending:Boolean=false)=DailyDocument(
        originId=UUID.randomUUID().toString(),supervisor="Control de jornada",day=day,revision=1,operators=emptyList(),assets=emptyList(),items=emptyList(),stockBalances=emptyMap(),
        startedAt=if(started)"2026-11-06T08:00:00Z"else "",operations=buildList {
            if(pending)add(Operation(day=day,module=Module.TASK,title="Actividad pendiente",status="Pendiente",time="10:30"))
            if(closed)add(Operation(day=day,module=Module.CLOSE,title="Cierre del día",status="Cerrada"))
        })
    @Test fun open_previous_day_blocks_next_day_even_without_tasks() {
        assertFailsWith<IllegalArgumentException>{DayControl.requirePreviousFinished(listOf(doc("2026-11-05")),"2026-11-06")}
    }
    @Test fun closed_day_with_pending_tasks_still_blocks_next_day() {
        assertEquals(1,DayControl.blockers(listOf(doc("2026-11-05",closed=true,pending=true)),"2026-11-06").size)
    }
    @Test fun completed_and_closed_days_allow_a_new_day_and_days_off_do_not_block() {
        DayControl.requirePreviousFinished(listOf(doc("2026-11-03",closed=true),doc("2026-11-04",started=false)),"2026-11-06")
    }
    @Test fun preparing_future_tasks_is_not_the_same_as_starting_work() {
        assertFalse(DayControl.started(doc("2026-11-08",started=false,pending=true)))
    }
    @Test fun all_old_unfinished_days_are_checked_in_date_order() {
        assertEquals(listOf("2026-11-03","2026-11-05"),DayControl.blockers(listOf(doc("2026-11-05"),doc("2026-11-03")),"2026-11-06").map{it.day})
    }
    @Test fun closure_alarm_is_at_19_local_time_and_rolls_to_tomorrow() {
        val d=doc("2026-11-06");val now=Instant.parse("2026-11-06T20:00:00Z")
        val events=AgendaAlarms.plan(listOf(d),now,ZoneId.of("America/Santo_Domingo"))
        assertEquals(Instant.parse("2026-11-06T23:00:00Z"),events.first().at)
        assertEquals(Instant.parse("2026-11-07T23:00:00Z"),events.last().at)
    }
    @Test fun no_closure_alarm_for_unused_or_closed_dates() {
        assertTrue(AgendaAlarms.plan(listOf(doc("2026-11-06",started=false),doc("2026-11-05",closed=true)),Instant.now(),ZoneOffset.UTC).isEmpty())
    }
    @Test fun completing_or_rescheduling_task_removes_old_alarm_identity() {
        val d=doc("2026-11-06",started=false,pending=true);val now=Instant.parse("2026-11-06T08:00:00Z")
        val event=AgendaAlarms.plan(listOf(d),now,ZoneOffset.UTC).single()
        val updated=d.copy(operations=listOf(d.operations.single().copy(time="11:00")))
        assertNotEquals(event.key,AgendaAlarms.plan(listOf(updated),now,ZoneOffset.UTC).single().key)
        assertTrue(AgendaAlarms.plan(listOf(d.copy(operations=listOf(d.operations.single().copy(status="Completada")))),now,ZoneOffset.UTC).none{it.taskId!=null})
    }
    @Test fun invalid_and_missing_hours_are_rejected_without_changing_legacy_decode() {
        listOf("","25:00","7:00","19:75").forEach { assertFails{AgendaTime.requireTime(it)} }
        AgendaTime.requireTime("19:00")
    }
}
