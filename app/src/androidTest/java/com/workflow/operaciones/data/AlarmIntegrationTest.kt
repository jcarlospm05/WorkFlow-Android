package com.workflow.operaciones.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.*
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.*
import com.workflow.core.Module
import com.workflow.operaciones.WorkflowApp
import com.workflow.operaciones.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.*
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class AlarmIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val app get()=context.applicationContext as WorkflowApp
    private val repo get()=app.repository
    private fun shell(command:String) { instrumentation.uiAutomation.executeShellCommand(command).use{fd->FileInputStream(fd.fileDescriptor).use{it.readBytes()}} }
    private suspend fun clearPending() {
        repo.history().forEach { doc ->
            if(DayControl.closed(doc)&&doc.operations.any{it.module==Module.TASK&&it.status=="Pendiente"})repo.save(Operation(day=doc.day,module=Module.CLOSE,title="Reapertura",status="Reabierta"))
            doc.operations.filter{it.module==Module.TASK&&it.status=="Pendiente"}.forEach{repo.save(it.copy(status="Completada"))}
            val current=repo.document(doc.day)
            if(DayControl.started(current)&&!DayControl.closed(current))repo.save(Operation(day=doc.day,module=Module.CLOSE,title="Cierre",status="Cerrada",notes="Cierre de instrumentación"))
        }
    }
    @Before fun prepare()=runBlocking {
        clearPending()
        AgendaScheduler.createChannel(context)
        AndroidAgendaAccess(context).setDefaultTime("09:00")
        if(Build.VERSION.SDK_INT>=31)shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        if(Build.VERSION.SDK_INT>=33)shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("cmd media_session volume --stream 4 --set 5")
    }
    @After fun finish()=runBlocking {
        clearPending();app.agendaScheduler.reconcile()
        context.startService(Intent(context,AgendaAlarmService::class.java).setAction(AgendaAlarmService.STOP))
    }
    @Test fun exact_alarm_is_registered_and_cancelled_when_task_is_completed()=runBlocking {
        val due=ZonedDateTime.now().plusDays(1).withHour(23).withMinute(50)
        val task=Operation(day=due.toLocalDate().toString(),module=Module.TASK,title="Aviso sintético de agenda",status="Pendiente",time="23:50")
        repo.save(task);app.agendaScheduler.reconcile()
        val next=context.getSystemService(AlarmManager::class.java).nextAlarmClock
        assertNotNull(next)
        assertEquals(due.withSecond(0).withNano(0).toInstant().toEpochMilli(),next!!.triggerTime)
        repo.save(repo.document(task.day).operations.single{it.id==task.id}.copy(status="Completada"))
        repo.save(Operation(day=task.day,module=Module.CLOSE,title="Cierre",status="Cerrada"))
        app.agendaScheduler.reconcile()
        assertNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
    }
    @Test fun overdue_alarm_fires_without_a_fake_record_and_stops_after_resolving_the_day()=runBlocking {
        val due=ZonedDateTime.now().minusMinutes(1)
        val day=due.toLocalDate().toString()
        val current=repo.document(day)
        if(DayControl.closed(current))repo.save(Operation(day=day,module=Module.CLOSE,title="Reapertura",status="Reabierta"))
        val task=Operation(day=day,module=Module.TASK,title="Aviso de instrumentación",status="Pendiente",time=due.format(DateTimeFormatter.ofPattern("HH:mm")))
        repo.save(task);app.agendaScheduler.reconcile()
        val end=SystemClock.uptimeMillis()+20_000
        while(!AgendaAlarmService.running&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100)
        assertTrue("Android debe activar el servicio del aviso",AgendaAlarmService.running)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any{it.id==AgendaAlarmService.NOTICE})
        assertEquals("Pendiente",repo.document(day).operations.first{it.id==task.id}.status)
        repo.save(repo.document(day).operations.first{it.id==task.id}.copy(status="Completada"))
        repo.save(Operation(day=day,module=Module.CLOSE,title="Cierre",status="Cerrada"))
        app.agendaScheduler.reconcile()
        val stopped=SystemClock.uptimeMillis()+10_000
        while(AgendaAlarmService.running&&SystemClock.uptimeMillis()<stopped)SystemClock.sleep(100)
        assertFalse(AgendaAlarmService.running)
    }
}
