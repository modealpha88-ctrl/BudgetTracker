package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test

class SecureBackupTest {
    private val password = "correct horse battery staple"

    @Test fun encryptedBackupRoundTripPreservesPayload() {
        val payload = """{"format":"mib-budget-backup","transactions":[{"amount_laari":12345,"description":"MVR test"}]}"""
        val encrypted = SecureBackup.encrypt(payload,password)
        assertTrue(encrypted.startsWith("MIB-ENC-V1:"))
        assertFalse(encrypted.contains("MVR test"))
        assertEquals(payload,SecureBackup.decrypt(encrypted,password))
    }

    @Test fun wrongPasswordCannotRestore() {
        val encrypted=SecureBackup.encrypt("private financial data",password)
        try {
            SecureBackup.decrypt(encrypted,"another secure password")
            fail("Wrong password must be rejected")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun tamperedCiphertextCannotRestore() {
        val encrypted=SecureBackup.encrypt("private financial data",password)
        val encoded=encrypted.removePrefix("MIB-ENC-V1:")
        val bytes=java.util.Base64.getDecoder().decode(encoded)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        val tampered="MIB-ENC-V1:"+java.util.Base64.getEncoder().encodeToString(bytes)
        try {
            SecureBackup.decrypt(tampered,password)
            fail("Tampered backup must be rejected")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }
}
