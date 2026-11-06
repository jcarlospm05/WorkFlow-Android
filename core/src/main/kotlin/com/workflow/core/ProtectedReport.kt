package com.workflow.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated transport. The header is AAD; no identity or report data is plaintext. */
object ProtectedReport {
    private val magic = byteArrayOf(0x57, 0x46, 0x52, 0x50) // WFRP
    private const val VERSION = 1
    private const val ROUNDS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
    const val MAX_BYTES = DailyCodec.MAX_BYTES + HEADER_BYTES + TAG_BYTES
    const val MIME_TYPE = "application/vnd.workflow.report"
    const val EXTENSION = "workflow"
    private val random = SecureRandom()

    fun validatePassword(password: CharArray) {
        require(password.size in 12..128 && password.any { !it.isWhitespace() }) { "Usa una clave o frase de 12 a 128 caracteres" }
    }
    fun validateEnvelope(bytes: ByteArray) {
        require(bytes.size in (HEADER_BYTES + TAG_BYTES)..MAX_BYTES) { "El archivo está incompleto o es demasiado grande" }
        require(bytes.copyOfRange(0, 4).contentEquals(magic)) { "Vuelve a compartir este informe desde la versión actual de WorkFlow" }
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(4)
        require(buffer.get().toInt() == VERSION) { "Actualiza WorkFlow para abrir este informe" }
        // Check before invoking the KDF: an untrusted file cannot request unbounded CPU work.
        require(buffer.int == ROUNDS) { "No se reconoce la protección de este informe" }
    }
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        val keyBytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv)) }
        } finally { keyBytes.fill(0) }
    }
    fun encode(document: DailyDocument, password: CharArray): ByteArray {
        validatePassword(password)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).put(VERSION.toByte()).putInt(ROUNDS).put(salt).put(iv).array()
        val clear = DailyCodec.encode(document)
        return try {
            val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv)
            cipher.updateAAD(header)
            header + cipher.doFinal(clear)
        } finally { clear.fill(0) }
    }
    fun decode(bytes: ByteArray, password: CharArray): DailyDocument {
        validateEnvelope(bytes)
        validatePassword(password)
        val salt = bytes.copyOfRange(9, 9 + SALT_BYTES)
        val iv = bytes.copyOfRange(9 + SALT_BYTES, HEADER_BYTES)
        val clear = try {
            val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv)
            cipher.updateAAD(bytes, 0, HEADER_BYTES)
            cipher.doFinal(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES)
        } catch (_: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("No se pudo abrir el informe. Revisa la clave o pide que te lo compartan de nuevo.")
        }
        return try { DailyCodec.decode(clear) } finally { clear.fill(0) }
    }
}
