package com.workflow.operaciones.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DayContinuationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private class DeviceContext(base:Context,private val key:String):ContextWrapper(base) {
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=super.getSharedPreferences("$key-$name",mode)
        override fun getFilesDir():File=File(super.getCacheDir(),key).apply{mkdirs()}
    }
    private val aContext=DeviceContext(context,"test-a-${UUID.randomUUID()}")
    private val bContext=DeviceContext(context,"test-b-${UUID.randomUUID()}")
    private val aDb=Room.inMemoryDatabaseBuilder(context,WorkflowDatabase::class.java).build()
    private val bDb=Room.inMemoryDatabaseBuilder(context,WorkflowDatabase::class.java).build()
    private val a=WorkflowRepository(aContext,aDb,TestAgendaAccess());private val b=WorkflowRepository(bContext,bDb,TestAgendaAccess())
    private val day="2026-11-06"
    private suspend fun finishPrevious(repository:WorkflowRepository, date:String) {
        repository.document(date).operations.filter{it.module==Module.TASK&&it.status=="Pendiente"}.forEach{repository.save(it.copy(status="Completada"))}
        repository.save(Operation(day=date,module=Module.CLOSE,title="Cierre",status="Cerrada",notes="Seguimiento de taller documentado"))
    }
    @After fun close(){aDb.close();bDb.close();aContext.filesDir.deleteRecursively();bContext.filesDir.deleteRecursively()}
    @Test fun complete_day_is_loaded_into_live_tables_and_can_return_to_original_phone()=runBlocking {
        val operator=Operator(UUID.randomUUID().toString(),name="Persona sintética")
        val asset=Asset(UUID.randomUUID().toString(),"Equipo sintético","Mula")
        a.addOperator(operator,day);a.addAsset(asset,day);a.startDay(day,setOf("dinner","empty-email"))
        a.save(Operation(time = "09:00", day=day,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=operator.id))
        a.save(Operation(time = "09:00", day=day,module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id))
        val outgoing=a.document(day)
        assertEquals(ContinuationDecision.LOAD,b.continueDay(outgoing))
        assertEquals(ContinuationDecision.ALREADY_LOADED,b.continueDay(outgoing))
        val loaded=b.document(day)
        assertEquals(outgoing.operations,loaded.operations)
        assertEquals(outgoing.startedAt,loaded.startedAt)
        assertEquals(operator,b.masters().first.single())
        val fuel=loaded.operations.single{it.module==Module.FUEL}
        b.save(fuel.copy(status="Abastecido",quantity=35.0))
        val returning=b.document(day)
        assertEquals(ContinuationDecision.UPDATE,a.continueDay(returning))
        assertEquals("Abastecido",a.document(day).operations.single{it.id==fuel.id}.status)
        assertEquals("Completada",a.document(day).operations.single{it.sourceId==fuel.id}.status)
        assertEquals(1,DailySummary.present(a.document(day)))
    }
    @Test fun concurrent_edits_and_other_dates_are_preserved_until_explicit_replacement()=runBlocking {
        a.startDay(day,setOf("empty-email"));b.continueDay(a.document(day))
        a.save(Operation(time = "09:00", day=day,module=Module.TASK,title="Cambio A",status="Pendiente"))
        b.save(Operation(time = "09:00", day=day,module=Module.TASK,title="Cambio B",status="Pendiente"))
        val tomorrow="2026-11-07"
        a.save(Operation(time = "09:00", day=tomorrow,module=Module.TASK,title="Mañana",status="Pendiente"))
        val before=a.document(day);val future=a.document(tomorrow);val incoming=b.document(day)
        assertEquals(ContinuationDecision.CONFLICT,a.continuationDecision(incoming))
        try{a.continueDay(incoming);fail("Debe detectar conflicto")}catch(_:IllegalArgumentException){}
        assertEquals(before,a.document(day));assertEquals(future,a.document(tomorrow))
        a.continueDay(incoming,replaceConflict=true)
        assertTrue(a.document(day).operations.any{it.title=="Cambio B"})
        assertTrue(a.document(tomorrow).operations.any{it.title=="Mañana"})
    }
    @Test fun stock_opening_moves_with_the_day_without_double_counting_prior_history()=runBlocking {
        val item=StockItem(UUID.randomUUID().toString(),"Artículo sintético","unidades")
        a.addItem(item,"2026-11-05")
        a.save(Operation(time = "09:00", day="2026-11-05",module=Module.STOCK,title="Entrada",status="Entrada",itemId=item.id,quantity=10.0))
        finishPrevious(a,"2026-11-05")
        a.startDay(day,emptySet());b.continueDay(a.document(day))
        assertEquals(10.0,b.document(day).stockBalances.getValue(item.id),.001)
        b.save(Operation(time = "09:00", day=day,module=Module.STOCK,title="Salida",status="Salida",itemId=item.id,quantity=2.0))
        a.continueDay(b.document(day))
        assertEquals(8.0,a.document(day).stockBalances.getValue(item.id),.001)
        assertEquals(8.0,a.document("2026-11-07").stockBalances.getValue(item.id),.001)
        assertEquals(10.0,a.document("2026-11-05").stockBalances.getValue(item.id),.001)
    }
    @Test fun workshop_marks_asset_temporarily_inactive_and_creates_one_follow_up_task()=runBlocking {
        val asset=Asset(UUID.randomUUID().toString(),"Equipo sintético","Grúa")
        a.addAsset(asset,day)
        val first=Operation(time = "09:00", day=day,module=Module.FAULT,title="Frenos",status="Abierta",assetId=asset.id)
        val second=first.copy(id=UUID.randomUUID().toString(),title="Fuga")
        a.save(first);a.save(second)
        assertEquals("Inactivo temporal",a.masters().second.single().state)
        assertEquals(1,a.document(day).operations.count{it.templateKey=="workshop-review"})
        a.save(first.copy(status="Cerrada"));assertEquals("Inactivo temporal",a.masters().second.single().state)
        a.save(second.copy(status="Cerrada"));assertEquals("Activo",a.masters().second.single().state)
        assertTrue(a.document(day).operations.filter{it.module==Module.FAULT}.all{it.resolvedAt.isNotEmpty()})
    }
    @Test fun starting_twice_never_duplicates_templates_and_default_export_prompts_are_off()=runBlocking {
        assertFalse(a.shareAfterSave)
        a.startDay(day,setOf("dinner","lunch"))
        try{a.startDay(day,setOf("dinner"));fail("La jornada ya está iniciada")}catch(_:IllegalArgumentException){}
        assertEquals(2,a.document(day).operations.size)
    }

    @Test fun a_preselected_fuel_activity_uses_one_task_and_updates_it_when_abastecido()=runBlocking {
        val asset=Asset(UUID.randomUUID().toString(),"Equipo de prueba","Grúa")
        a.addAsset(asset,day);a.startDay(day,setOf("fuel-crane"))
        val activity=Operation(time = "09:00", day=day,module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id)
        a.save(activity)
        val task=a.document(day).operations.single{it.module==Module.TASK}
        assertEquals(activity.id,task.sourceId)
        a.save(activity.copy(status="Abastecido"))
        assertEquals("Completada",a.document(day).operations.single{it.module==Module.TASK}.status)
    }
    @Test fun an_open_fault_from_an_earlier_day_can_be_resolved_after_handoff()=runBlocking {
        val asset=Asset(UUID.randomUUID().toString(),"Equipo de prueba","Mula")
        val yesterday="2026-11-05"
        a.addAsset(asset,yesterday)
        val fault=Operation(time = "09:00", day=yesterday,module=Module.FAULT,title="Avería anterior",status="Abierta",assetId=asset.id)
        a.save(fault);finishPrevious(a,yesterday);a.startDay(day,emptySet())
        val outgoing=a.document(day)
        assertEquals(fault.id,outgoing.carryOverFaults.single().id)
        b.continueDay(outgoing)
        b.save(b.document(day).carryOverFaults.single().copy(status="Cerrada",notes="Resuelta"))
        a.continueDay(b.document(day))
        assertEquals("Activo",a.masters().second.single().state)
        assertEquals("Cerrada",a.document(day).carryOverFaults.single().status)
    }


    @Test fun closing_a_day_lists_and_requires_notes_for_an_earlier_unresolved_fault()=runBlocking {
        val asset=Asset(UUID.randomUUID().toString(),"Equipo de prueba","Mula")
        a.addAsset(asset,"2026-11-05")
        a.save(Operation(time = "09:00", day="2026-11-05",module=Module.FAULT,title="Avería anterior",status="Abierta",assetId=asset.id))
        finishPrevious(a,"2026-11-05")
        a.startDay(day,emptySet())
        val review=a.document(day).operations.single{it.templateKey=="workshop-review"}
        a.save(review.copy(status="Completada"))
        assertEquals(1,DailySummary.pending(a.document(day)).size)
        try { a.save(Operation(time = "09:00", day=day,module=Module.CLOSE,title="Cierre del día",status="Cerrada"));fail("Debe documentar la avería pendiente") }
        catch(_:IllegalArgumentException){}
        assertFalse(a.document(day).operations.any{it.module==Module.CLOSE})
    }


    @Test fun stale_form_cannot_overwrite_a_record_updated_by_a_continued_journey()=runBlocking {
        val original=Operation(time = "09:00", day=day,module=Module.TASK,title="Primera versión",status="Pendiente")
        a.save(original)
        val before=a.document(day).operations.single()
        a.save(before.copy(title="Versión recibida actualizada"))
        val latest=a.document(day)
        try { a.save(before.copy(title="Borrador anterior"),expectedUpdatedAt=before.updatedAt);fail("Debe conservar la versión actual") }
        catch(_:IllegalArgumentException){}
        assertEquals(latest,a.document(day))
    }
}
