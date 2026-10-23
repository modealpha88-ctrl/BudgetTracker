package com.example.mibbudget

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted, authenticated backup; the password never leaves this device. */
object SecureBackup {
    private const val PREFIX = "MIB-ENC-V1:"
    private const val ITERATIONS = 210_000
    fun encrypt(json: String, password: String): String {
        require(password.length >= 12) { "Use a password of at least 12 characters" }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key=derive(password,salt)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,nonce))
        val encrypted=cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return PREFIX+Base64.getEncoder().encodeToString(salt+nonce+encrypted)
    }
    fun decrypt(input: String, password: String): String {
        require(input.startsWith(PREFIX)) { "Not an encrypted MIB backup" }
        val bytes=Base64.getDecoder().decode(input.removePrefix(PREFIX).trim())
        require(bytes.size in 45..30_000_000) { "Invalid backup size" }
        val salt=bytes.copyOfRange(0,16)
        val nonce=bytes.copyOfRange(16,28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce))
        return String(cipher.doFinal(bytes.copyOfRange(28,bytes.size)),Charsets.UTF_8)
    }
    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec=PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        } finally { spec.clearPassword() }
    }
}
