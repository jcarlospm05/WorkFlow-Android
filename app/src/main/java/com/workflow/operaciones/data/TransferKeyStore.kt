package com.workflow.operaciones.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.workflow.core.ProtectedReport
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the agreed transfer phrase encrypted with a non-exportable Android Keystore key. */
class TransferKeyStore(context: Context, private val scope: String = "workflow.transfer.v1") {
    private val prefs = context.getSharedPreferences(scope, Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.$scope"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey = (store().getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        generateKey()
    }
    fun isConfigured(): Boolean = prefs.contains("phrase")
    @Synchronized fun save(password: CharArray) {
        ProtectedReport.validatePassword(password)
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val clear = ByteArray(buffer.remaining()).also { buffer.get(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray()) }
            val sealed = cipher.iv + cipher.doFinal(clear)
            check(prefs.edit().putString("phrase", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) { "No se pudo guardar la clave en este teléfono" }
        } finally { clear.fill(0); if (buffer.hasArray()) buffer.array().fill(0) }
    }
    @Synchronized fun read(): CharArray? {
        val encoded = prefs.getString("phrase", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size >= 28)
            val existing = store().getKey(alias, null) as? SecretKey ?: error("Clave local no disponible")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(alias.toByteArray())
            }
            val clear = cipher.doFinal(bytes, 12, bytes.size - 12)
            try {
                val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(clear))
                CharArray(buffer.remaining()).also { buffer.get(it); if (buffer.hasArray()) buffer.array().fill('\u0000') }
            } finally { clear.fill(0) }
        } catch (_: Exception) {
            runCatching { clear() }
            null // Ask the person to enter the agreed phrase again; never fall back to a shared built-in key.
        }
    }
    @Synchronized fun clear() { prefs.edit().clear().commit(); store().deleteEntry(alias) }
}
