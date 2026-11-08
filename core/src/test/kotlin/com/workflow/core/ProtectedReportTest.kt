package com.workflow.core

import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.*

class ProtectedReportTest {
    private fun phrase() = "Frase sintética del equipo".toCharArray()
    private fun document(): DailyDocument = DailyCodec.seal(DailyDocument(originId = UUID.randomUUID().toString(), supervisor = "Supervisor de prueba confidencial", day = "2026-11-06", revision = 3,
        operators = listOf(Operator(UUID.randomUUID().toString(), name = "Ana Muñoz"), Operator(UUID.randomUUID().toString(), name = "José Pérez")), assets = emptyList(), items = emptyList(), operations = emptyList(), stockBalances = emptyMap()))
    @Test fun `protected report preserves all data and multiple operators without codes`() {
        val original = document()
        val wire = ProtectedReport.encode(original, phrase())
        assertEquals(original, ProtectedReport.decode(wire, phrase()))
        assertFalse(wire.toString(Charsets.ISO_8859_1).contains("Supervisor"))
        assertFalse(wire.toString(Charsets.ISO_8859_1).contains("operators"))
    }
    @Test fun `each send is randomized even for the same revision and phrase`() {
        val doc = document()
        val first = ProtectedReport.encode(doc, phrase())
        val second = ProtectedReport.encode(doc, phrase())
        assertFalse(first.contentEquals(second))
        assertEquals(ProtectedReport.decode(first, phrase()), ProtectedReport.decode(second, phrase()))
    }
    @Test fun `wrong key cannot reveal any report`() {
        val wire = ProtectedReport.encode(document(), phrase())
        assertFailsWith<IllegalArgumentException> { ProtectedReport.decode(wire, "Otra frase sintética".toCharArray()) }
    }
    @Test fun `changed ciphertext tag and authenticated salt are rejected`() {
        val wire = ProtectedReport.encode(document(), phrase())
        listOf(10, 45, wire.lastIndex).forEach { at ->
            val changed = wire.copyOf(); changed[at] = (changed[at].toInt() xor 1).toByte()
            assertFailsWith<IllegalArgumentException> { ProtectedReport.decode(changed, phrase()) }
        }
    }
    @Test fun `untrusted parameters and oversized input are bounded before decryption`() {
        val wire = ProtectedReport.encode(document(), phrase())
        ByteBuffer.wrap(wire).putInt(5, Int.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> { ProtectedReport.decode(wire, phrase()) }
        assertFailsWith<IllegalArgumentException> { ProtectedReport.validateEnvelope(ByteArray(ProtectedReport.MAX_BYTES + 1)) }
        assertFailsWith<IllegalArgumentException> { ProtectedReport.validateEnvelope(ByteArray(10)) }
    }
    @Test fun `ordinary unencrypted reports do not silently bypass protection`() {
        assertFailsWith<IllegalArgumentException> { ProtectedReport.decode(DailyCodec.encode(document()), phrase()) }
    }
    @Test fun `new or unsupported format and short phrases are rejected`() {
        val wire = ProtectedReport.encode(document(), phrase()); wire[4] = 99
        assertFailsWith<IllegalArgumentException> { ProtectedReport.decode(wire, phrase()) }
        assertFailsWith<IllegalArgumentException> { ProtectedReport.encode(document(), "1234".toCharArray()) }
    }
    @Test fun `provided employee codes remain unique while absent codes are optional`() {
        val doc = document()
        assertEquals(doc, DailyCodec.decode(DailyCodec.encode(doc)))
        assertFailsWith<IllegalArgumentException> { DailyCodec.encode(doc.copy(operators = doc.operators.mapIndexed { index, op -> op.copy(code = if (index == 0) "OP-01" else "op-01") })) }
    }
}
