package com.workflow.operaciones.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TransferProtectionTest {
    @Test fun agreed_phrase_is_remembered_encrypted_and_can_be_recovered_by_a_new_instance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "workflow.test.${UUID.randomUUID()}"
        val vault = TransferKeyStore(context, scope)
        val secret = "Clave sintética para el equipo".toCharArray()
        try {
            vault.save(secret)
            val first = context.getSharedPreferences(scope, 0).getString("phrase", null)
            assertNotNull(first)
            assertFalse(first!!.contains(String(secret)))
            val recovered = TransferKeyStore(context, scope).read()
            assertArrayEquals(secret, recovered)
            recovered?.fill('\u0000')
            vault.save(secret)
            assertNotEquals(first, context.getSharedPreferences(scope, 0).getString("phrase", null))
            vault.clear()
            assertNull(vault.read())
        } finally { secret.fill('\u0000'); vault.clear() }
    }
}
