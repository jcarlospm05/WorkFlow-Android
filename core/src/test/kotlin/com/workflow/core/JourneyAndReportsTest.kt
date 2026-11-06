package com.workflow.core

import kotlin.test.*
import java.util.UUID
import kotlinx.serialization.encodeToString

class JourneyAndReportsTest {
    private val a=UUID.randomUUID().toString();private val b=UUID.randomUUID().toString();private val journey=UUID.randomUUID().toString()
    private val operator=Operator(UUID.randomUUID().toString(),name="Persona de prueba")
    private fun document(day:String="2026-11-06",vector:Map<String,Long> = mapOf(a to 1L),ops:List<Operation> = listOf(Operation(day=day,module=Module.TASK,title="Pendiente",status="Pendiente")),assets:List<Asset> = emptyList()) = DailyCodec.seal(DailyDocument(
        originId=a,supervisor="Responsable de prueba",day=day,revision=1,operators=listOf(operator),assets=assets,items=emptyList(),operations=ops,stockBalances=emptyMap(),journeyId=journey,versionVector=vector,startedAt="2026-11-06T07:00:00Z"))
    @Test fun handoff_can_return_to_the_original_phone_with_all_tasks() {
        val first=document();val onB=first.copy(originId=b,versionVector=mapOf(a to 1L,b to 1L),revision=2)
        assertEquals(ContinuationDecision.UPDATE,ContinuationPolicy.decide(first,onB))
        val returned=DailyCodec.decode(DailyCodec.encode(onB))
        assertEquals(first.operations,returned.operations)
        assertEquals(ContinuationDecision.ALREADY_LOADED,ContinuationPolicy.decide(onB,returned))
    }
    @Test fun concurrent_and_older_edits_are_detected_without_using_clock_time() {
        val local=document(vector=mapOf(a to 2L));val remote=document(vector=mapOf(a to 1L,b to 1L)).copy(generatedAt="2099-01-01T00:00:00Z")
        assertEquals(ContinuationDecision.CONFLICT,ContinuationPolicy.decide(local,remote))
        assertEquals(ContinuationDecision.OLDER,ContinuationPolicy.decide(local,document()))
        assertEquals(ContinuationDecision.CONFLICT,ContinuationPolicy.decide(local,remote.copy(journeyId=UUID.randomUUID().toString())))
    }
    @Test fun only_selected_start_of_day_templates_generate_tasks() {
        val selected=DayTemplates.operations("2026-11-06",setOf("dinner","empty-email"))
        assertEquals(listOf("dinner","empty-email"),selected.map{it.templateKey})
        assertTrue(selected.all{it.module==Module.TASK&&it.status=="Pendiente"})
        assertTrue(DayTemplates.operations("2026-11-06",emptySet()).isEmpty())
        assertFails { DayTemplates.operations("2026-11-06",setOf("unknown")) }
    }
    @Test fun linked_fuel_task_keeps_its_id_and_follows_the_activity() {
        val asset=Asset(UUID.randomUUID().toString(),"Equipo","Mula")
        val op=Operation(day="2026-11-06",module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id)
        val first=LinkedTasks.forOperation(op,emptyList(),asset)!!
        val completed=LinkedTasks.forOperation(op.copy(status="Abastecido"),listOf(first),asset)!!
        assertEquals(first.id,completed.id);assertEquals(op.id,completed.sourceId);assertEquals("Completada",completed.status)
    }
    @Test fun old_document_checksum_remains_compatible_with_added_optional_fields() {
        val legacy=document().copy(schemaVersion=1,journeyId="",versionVector=emptyMap(),startedAt="",stockOpeningBalances=emptyMap())
        val sealed=DailyCodec.seal(legacy)
        val text=DailyCodec.json.encodeToString(sealed)
        assertFalse(text.contains("journeyId"));assertFalse(text.contains("versionVector"));assertFalse(text.contains("severity"))
        assertEquals(sealed,DailyCodec.decode(text.toByteArray()))
    }
    @Test fun date_operator_and_equipment_filters_are_applied_to_reports_and_text() {
        val asset=Asset(UUID.randomUUID().toString(),"MU-01","Mula")
        val op=Operation(day="2026-11-06",module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id,operatorId=operator.id,quantity=30.0)
        val d=document(ops=listOf(op),assets=listOf(asset))
        val q=ReportQuery(ReportType.FUEL,"2026-11-06",operatorId=operator.id,assetId=asset.id,status="Pendiente")
        val result=ReportBuilder.build(q,listOf(d))
        assertContains(result.text(),"MU-01");assertContains(result.text(),operator.name);assertContains(result.text(),"30")
        assertFalse(ReportBuilder.build(q.copy(from="2026-11-07",to="2026-11-08"),listOf(d)).text().contains("30 L"))
        assertEquals("0",ReportBuilder.build(q.copy(assetId=UUID.randomUUID().toString()),listOf(d)).metrics.first().value)
        assertFails { q.copy(from="2026-11-08").validate() }
    }
    @Test fun weekend_meals_preserve_names_dates_and_service_count() {
        val sat=Operation(day="2026-11-07",module=Module.MEAL,title="Almuerzo sábado",status="Solicitada",mealType="Almuerzo",operatorIds=listOf(operator.id))
        val sun=sat.copy(id=UUID.randomUUID().toString(),day="2026-11-08",title="Almuerzo domingo")
        val result=ReportBuilder.build(ReportQuery(ReportType.LUNCH,sat.day,sun.day),listOf(document(sat.day,ops=listOf(sat)),document(sun.day,ops=listOf(sun))))
        assertEquals("2",result.metrics[0].value);assertEquals("1",result.metrics[1].value)
        assertContains(result.text(),"07 nov");assertContains(result.text(),"08 nov");assertContains(result.text(),operator.name)
    }
    @Test fun sunday_attendance_counts_distinct_days_instead_of_duplicate_shifts() {
        val sunday=Operation(day="2026-11-08",module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=operator.id)
        val result=ReportBuilder.build(ReportQuery(ReportType.SUNDAY,sunday.day),listOf(document(sunday.day,ops=listOf(sunday,sunday.copy(id=UUID.randomUUID().toString(),shift="Noche")))))
        assertEquals("1",result.sections.last().rows.single()[1])
    }
    @Test fun a_long_individual_description_is_preserved_in_report_data_and_messages() {
        val notes="Descripción con acentos. ".repeat(100)
        val op=Operation(day="2026-11-06",module=Module.TICKET,title="Motivo",status="Emitido",operatorId=operator.id,notes=notes,severity="Grave",expected="Proceso completo",actual="Paso omitido")
        val result=ReportBuilder.build(ReportQuery(ReportType.TICKET,op.day,operationId=op.id),listOf(document(ops=listOf(op))))
        assertContains(result.text(),notes);assertContains(result.text(),"Grave");assertContains(result.text(),"Proceso completo")
        assertTrue(result.compact)
    }
    @Test fun empty_install_reports_never_create_business_records() {
        val d=document(ops=emptyList()).copy(operators=emptyList(),startedAt="")
        ReportType.entries.forEach { type ->
            val report=ReportBuilder.build(ReportQuery(type,d.day),listOf(d))
            assertTrue(report.title.isNotBlank());assertFalse(report.text().contains("Persona de prueba"))
        }
        assertTrue(d.operations.isEmpty());assertTrue(d.operators.isEmpty())
    }
}
