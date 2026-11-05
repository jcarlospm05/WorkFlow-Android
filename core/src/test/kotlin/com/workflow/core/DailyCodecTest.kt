package com.workflow.core

import kotlin.test.*
import kotlinx.serialization.encodeToString

class DailyCodecTest {
    private val origin = "11111111-1111-4111-8111-111111111111"
    private val receiver = "22222222-2222-4222-8222-222222222222"
    private val operator = Operator("33333333-3333-4333-8333-333333333333", "OP-01", "Operador de prueba")
    private fun document(revision: Long = 1) = DailyCodec.seal(DailyDocument(originId = origin, supervisor = "Supervisor de prueba", day = "2026-11-05", revision = revision,
        generatedAt = "2026-11-05T09:00:00Z", operators = listOf(operator), assets = emptyList(), items = emptyList(), operations = emptyList(), stockBalances = emptyMap()))

    @Test fun `JSON round trip preserves Spanish and the complete document`() {
        val d = document().copy(supervisor = "José Muñoz · Operación")
        val parsed = DailyCodec.decode(DailyCodec.encode(d))
        assertEquals(d.copy(checksum = parsed.checksum), parsed)
    }
    @Test fun `changed data without updated checksum is rejected`() {
        val bytes = DailyCodec.json.encodeToString(document()).replace("Supervisor de prueba", "Nombre manipulado").toByteArray()
        assertFailsWith<IllegalArgumentException> { DailyCodec.decode(bytes) }
    }
    @Test fun `unrecognized schema rejected even when checksum is recomputed`() {
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(document().copy(schemaVersion = 99)) }
    }
    @Test fun `unknown field rejected`() {
        val text = DailyCodec.json.encodeToString(document()).dropLast(1) + ",\"hiddenKey\":\"x\"}"
        assertFails { DailyCodec.decode(text.toByteArray()) }
    }
    @Test fun `orphan operator rejected`() {
        val operation = Operation(day = "2026-11-05", module = Module.ATTENDANCE, title = "Asistencia", status = "Presente", operatorId = receiver)
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(document().copy(operations = listOf(operation))) }
    }
    @Test fun `duplicate attendance key rejected`() {
        val operation = Operation(day = "2026-11-05", module = Module.ATTENDANCE, title = "Asistencia", status = "Presente", operatorId = operator.id)
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(document().copy(operations = listOf(operation, operation.copy(id = receiver)))) }
    }
    @Test fun `same person on two shifts counts as one present`() {
        val operation = Operation(day = "2026-11-05", module = Module.ATTENDANCE, title = "Asistencia", status = "Presente", operatorId = operator.id)
        val d = document().copy(operations = listOf(operation, operation.copy(id = receiver, shift = "Noche", status = "Tardanza", extended = true)))
        DailyCodec.validate(d)
        assertEquals(1, DailySummary.present(d))
    }
    @Test fun `reimport is idempotent and older versions rejected`() {
        val old = document(1); val latest = document(2)
        assertEquals(ImportDecision.NEW, ImportPolicy.decide(receiver, old, null))
        assertEquals(ImportDecision.ALREADY_RECEIVED, ImportPolicy.decide(receiver, old, old))
        assertEquals(ImportDecision.UPDATE, ImportPolicy.decide(receiver, latest, old))
        assertFailsWith<IllegalArgumentException> { ImportPolicy.decide(receiver, old, latest) }
    }
    @Test fun `conflicting same revision and own origin rejected`() {
        val d = document()
        val other = DailyCodec.seal(d.copy(supervisor = "Otro responsable"))
        assertFailsWith<IllegalArgumentException> { ImportPolicy.decide(receiver, other, d) }
        assertFailsWith<IllegalArgumentException> { ImportPolicy.decide(origin, d, null) }
    }
    @Test fun `impossible day and malformed hour rejected`() {
        assertFails { DailyCodec.encode(document().copy(day = "2026-02-30")) }
        val op = Operation(day = "2026-11-05", module = Module.TASK, title = "Tarea", status = "Pendiente", time = "25:99")
        assertFails { DailyCodec.encode(document().copy(operations = listOf(op))) }
    }
    @Test fun `large and deeply nested payload rejected`() {
        assertFailsWith<IllegalArgumentException> { DailyCodec.decode(ByteArray(DailyCodec.MAX_BYTES + 1)) }
        assertFailsWith<IllegalArgumentException> { DailyCodec.decode(("[".repeat(100) + "0" + "]".repeat(100)).toByteArray()) }
    }
    @Test fun `negative and nonfinite stock rejected`() {
        val item = StockItem(receiver, "Artículo de prueba", "unidades")
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(document().copy(items = listOf(item), stockBalances = mapOf(receiver to -1.0))) }
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(document().copy(items = listOf(item), stockBalances = mapOf(receiver to Double.NaN))) }
    }
    @Test fun `meal independent of attendance and report includes names`() {
        val meal = Operation(day = "2026-11-05", module = Module.MEAL, title = "Cena", status = "Solicitada", operatorIds = listOf(operator.id))
        val d = DailyCodec.decode(DailyCodec.encode(document().copy(operations = listOf(meal))))
        assertEquals(0, DailySummary.present(d))
        assertTrue(DailySummary.lines(d).any { it.contains(operator.name) })
    }
}
