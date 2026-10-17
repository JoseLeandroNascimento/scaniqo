package com.joseleandro.scaniqo.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun generalPasswordRequiresEightUnicodeCharacters() {
        assertFalse(PasswordPolicy.isValidGeneral("1234567"))
        assertTrue(PasswordPolicy.isValidGeneral("12345678"))
        assertFalse(PasswordPolicy.isValidGeneral("🔒".repeat(7)))
        assertTrue(PasswordPolicy.isValidGeneral("🔒".repeat(8)))
    }

    @Test
    fun limitIsMeasuredInUtf8BytesInsteadOfStringLength() {
        assertTrue(PasswordPolicy.isValidGeneral("a".repeat(127)))
        assertFalse(PasswordPolicy.isValidGeneral("a".repeat(128)))
        assertTrue(PasswordPolicy.isValidGeneral("á".repeat(63) + "a"))
        assertFalse(PasswordPolicy.isValidGeneral("á".repeat(64)))
    }

    @Test
    fun originalDocumentPasswordMayBeShortButNeverEmpty() {
        assertTrue(PasswordPolicy.isValidDocument("a"))
        assertTrue(PasswordPolicy.isValidDocument(" "))
        assertFalse(PasswordPolicy.isValidDocument(""))
        assertFalse(PasswordPolicy.isValidDocument("🔒".repeat(32)))
    }

    @Test
    fun passwordIsNotTrimmedOrNormalized() {
        assertTrue(PasswordPolicy.isValidGeneral("       a"))
        assertFalse(PasswordPolicy.isValidGeneral("       "))
        assertTrue(PasswordPolicy.isValidDocument("é".repeat(63)))
        assertFalse(PasswordPolicy.isValidDocument("e\u0301".repeat(63)))
    }
}
