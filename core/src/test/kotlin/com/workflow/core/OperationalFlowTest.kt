package com.workflow.core

import kotlin.test.*
import java.util.UUID

class OperationalFlowTest {
    private val day="2026-11-08"
    private val person=Operator(UUID.randomUUID().toString(),name="Validación CSV")
    private val asset=Asset(UUID.randomUUID().toString(),"EQ-CSV","Mula")
    private fun doc(rows:List<Operation>)=DailyDocument(originId=UUID.randomUUID().toString(),supervisor="Validación",day=day,revision=1,operators=listOf(person),assets=listOf(asset),items=emptyList(),stockBalances=emptyMap(),operations=rows)
    @Test fun every_template_has_a_module_and_inventory_is_not_available() {
        val tasks=DayTemplates.operations(day,DayTemplates.all.map{it.key}.toSet())
        assertEquals(listOf(Module.MEAL,Module.MEAL,Module.FUEL,Module.FUEL,Module.TASK,Module.TASK),tasks.map{TaskRouting.destination(it)})
        assertEquals("Almuerzo",tasks.single{it.templateKey=="lunch"}.mealType)
        assertFalse(Module.STOCK in TaskRouting.modules)
        assertFalse(ReportGuide.topics.any{it.type==ReportType.STOCK})
    }
    @Test fun pending_badges_follow_module_ownership_and_do_not_count_linked_events_twice() {
        val fuel=Operation(day=day,module=Module.FUEL,title="Combustible",status="Pendiente",assetId=asset.id)
        val linked=LinkedTasks.forOperation(fuel,emptyList(),asset)!!
        val manual=Operation(day=day,module=Module.TASK,title="Verificar",status="Pendiente",targetModule=Module.ATTENDANCE)
        val document=doc(listOf(fuel,linked,manual))
        assertEquals(1,TaskRouting.pending(document,Module.FUEL).size)
        assertEquals(1,TaskRouting.pending(document,Module.ATTENDANCE).size)
        assertEquals(Module.FUEL,TaskRouting.destination(linked))
        assertTrue(TaskRouting.pending(document.copy(operations=listOf(fuel.copy(status="Abastecido"),linked.copy(status="Completada"))),Module.FUEL).isEmpty())
    }
    @Test fun new_task_ownership_survives_protected_day_continuation() {
        val task=Operation(day=day,module=Module.TASK,title="Verificar equipos",status="Pendiente",targetModule=Module.FAULT)
        val sealed=DailyCodec.seal(doc(listOf(task)))
        assertEquals(Module.FAULT,DailyCodec.decode(DailyCodec.encode(sealed)).operations.single().targetModule)
    }
    @Test fun attendance_corrections_replace_the_decision_even_in_legacy_multiple_shifts() {
        val first=Operation(day=day,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=person.id,updatedAt="2026-11-08T09:00:00Z")
        val corrected=first.copy(id=UUID.randomUUID().toString(),shift="Noche",status="Ausencia justificada",updatedAt="2026-11-08T10:00:00Z")
        val document=doc(listOf(first,corrected))
        assertEquals(listOf(corrected),AttendanceList.latest(document.operations))
        assertEquals(0,DailySummary.present(document))
        assertEquals(1,ReportCsv.build(ReportQuery(ReportType.ATTENDANCE,day),listOf(document)).single().rows.size)
    }
    @Test fun closure_exports_separate_module_tables_and_one_meal_row_per_person() {
        val other=person.copy(id=UUID.randomUUID().toString(),name="Segunda persona")
        val meal=Operation(day=day,module=Module.MEAL,title="Cena",status="Solicitada",operatorIds=listOf(person.id,other.id))
        val fuel=Operation(day=day,module=Module.FUEL,title="Fuel",status="Abastecido",assetId=asset.id,quantity=12.5)
        val document=doc(listOf(meal,fuel)).copy(operators=listOf(person,other))
        val tables=ReportCsv.build(ReportQuery(ReportType.CLOSE,day),listOf(document))
        assertEquals(setOf("MEAL","FUEL"),tables.map{it.name}.toSet())
        assertEquals(2,tables.single{it.name=="MEAL"}.rows.size)
        assertEquals("12.5",tables.single{it.name=="FUEL"}.rows.single()[11])
        assertTrue(tables.all{t->t.rows.all{it.size==t.headers.size}})
    }
    @Test fun csv_quotes_multiline_text_and_neutralizes_spreadsheet_formulas() {
        val table=CsvTable("Seguro",listOf("Nombre","Detalle"),listOf(listOf(" =HYPERLINK(\"x\")","uno, dos\n\"tres\"")))
        val text=table.encode().toString(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF"))
        assertContains(text,"\"' =HYPERLINK(\"\"x\"\")\"")
        assertContains(text,"\"uno, dos\n\"\"tres\"\"\"")
        assertTrue(text.endsWith("\r\n"))
    }
    @Test fun csv_filters_dates_status_people_and_sunday_queries() {
        val today=Operation(day=day,module=Module.ATTENDANCE,title="Asistencia",status="Presente",operatorId=person.id)
        val yesterday=today.copy(id=UUID.randomUUID().toString(),day="2026-11-07")
        val document=doc(listOf(today,yesterday))
        val table=ReportCsv.build(ReportQuery(ReportType.SUNDAY,"2026-11-01","2026-11-30",operatorId=person.id),listOf(document)).single()
        assertEquals(listOf(day),table.rows.map{it[1]})
        assertEquals("Sin_registros",ReportCsv.build(ReportQuery(ReportType.ATTENDANCE,day,status="Libre"),listOf(document)).single().name)
    }
    @Test fun protected_transfer_still_rejects_tampering_with_module_tasks() {
        val password="Frase exclusivamente de prueba".toCharArray()
        val bytes=ProtectedReport.encode(DailyCodec.seal(doc(listOf(Operation(day=day,module=Module.TASK,title="Gestión",status="Pendiente",targetModule=Module.PLAN)))),password)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException>{ProtectedReport.decode(bytes,password)}
        password.fill('\u0000')
    }
}
