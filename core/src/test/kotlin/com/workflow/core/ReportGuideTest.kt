package com.workflow.core

import kotlin.test.*
import java.util.UUID

class ReportGuideTest {
    private val day="2026-11-08"
    private fun document(ops:List<Operation>)=DailyDocument(originId=UUID.randomUUID().toString(),supervisor="Control de reporte",day=day,revision=1,operators=emptyList(),assets=emptyList(),items=emptyList(),operations=ops,stockBalances=emptyMap())
    @Test fun every_non_individual_report_has_one_guided_topic() {
        assertEquals(ReportType.entries.filter{it !in listOf(ReportType.TICKET,ReportType.EVENT,ReportType.STOCK)}.toSet(),ReportGuide.topics.map{it.type}.toSet())
        assertEquals(19,ReportGuide.topics.size)
    }
    @Test fun search_accepts_accents_and_multiple_words() {
        assertTrue(ReportGuide.matches(ReportGuide.topic(ReportType.WORKSHOP),"averias taller"))
        assertTrue(ReportGuide.matches(ReportGuide.topic(ReportType.SUNDAY),"DOMINGOS"))
        assertFalse(ReportGuide.matches(ReportGuide.topic(ReportType.STOCK),"domingos"))
    }
    @Test fun periods_use_explicit_reference_and_real_today_for_today_and_yesterday() {
        assertEquals("2026-11-02" to day,ReportGuide.bounds(ReportPeriod.WEEK,day,day))
        assertEquals("2024-02-01" to "2024-02-29",ReportGuide.bounds(ReportPeriod.MONTH,"2024-02-15",day))
        assertEquals(day to day,ReportGuide.bounds(ReportPeriod.TODAY,"2024-01-01",day))
        assertEquals("2026-11-07" to "2026-11-07",ReportGuide.bounds(ReportPeriod.YESTERDAY,"2024-01-01",day))
    }
    @Test fun weekly_and_monthly_topics_start_with_their_actual_period() {
        assertEquals("2026-11-02",ReportGuide.initial(ReportType.WEEK,day).from)
        assertEquals("2026-11-30",ReportGuide.initial(ReportType.MONTH,day).to)
        assertEquals("2026-11-01",ReportGuide.initial(ReportType.SUNDAY,day).from)
    }
    @Test fun reopening_a_query_preserves_a_custom_period_instead_of_claiming_it_is_a_month() {
        val custom=ReportQuery(ReportType.SUNDAY,"2026-11-06","2026-11-08")
        assertEquals(ReportPeriod.CUSTOM,ReportGuide.selectedPeriod(custom,day,day))
        assertEquals(ReportPeriod.MONTH,ReportGuide.selectedPeriod(ReportGuide.initial(ReportType.SUNDAY,day),day,day))
        assertEquals(ReportPeriod.TODAY,ReportGuide.selectedPeriod(ReportQuery(ReportType.WEEK,day),day,day))
        assertEquals("2026-11-06",custom.from)
    }
    @Test fun operational_balance_counts_confirmed_fuel_and_issued_tickets_only() {
        val ops=listOf(Operation(day=day,module=Module.FUEL,title="Realizado",status="Abastecido",quantity=40.0),Operation(day=day,module=Module.FUEL,title="Previsto",status="Pendiente",quantity=90.0),Operation(day=day,module=Module.TICKET,title="Emitido",status="Emitido"),Operation(day=day,module=Module.TICKET,title="Borrador",status="Borrador"))
        val report=ReportBuilder.build(ReportQuery(ReportType.GENERAL,day),listOf(document(ops)))
        val balance=report.sections.single{it.title=="Balance operativo"}.rows.associate{it[0] to it[1]}
        assertEquals("40",balance["Litros confirmados"])
        assertEquals("1",balance["Tickets emitidos"])
        assertEquals("1",report.sections.single{it.title=="Pendientes que requieren atención"}.rows.single{it[0]=="Abastecimientos pendientes"}[1])
        assertFalse(report.isEmpty)
    }
    @Test fun empty_reports_state_the_absence_of_data_instead_of_implying_completed_work() {
        val doc=document(emptyList())
        listOf(ReportType.GENERAL,ReportType.ATTENDANCE,ReportType.MONTH).forEach { type->
            val result=ReportBuilder.build(ReportQuery(type,day),listOf(doc))
            assertTrue(result.isEmpty)
            assertContains(result.text(),"Sin registros para esta consulta")
        }
        assertTrue(doc.operations.isEmpty())
    }
}
