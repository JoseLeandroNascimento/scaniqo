package com.joseleandro.scaniqo.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordSessionTest {
    private var now = 0L
    private val session = PasswordSession(now = { now }, timeoutMillis = 100)

    @Test
    fun newSessionIsLocked() {
        assertFalse(session.isUnlocked())
        assertThrows(PasswordVaultLockedException::class.java) { session.copyKey() }
    }

    @Test
    fun sessionExpiresAtTheExactInactivityDeadline() {
        session.unlock(byteArrayOf(1, 2, 3))
        now = 99
        assertTrue(session.isUnlocked())
        now = 100
        assertFalse(session.isUnlocked())
        assertThrows(PasswordVaultLockedException::class.java) { session.copyKey() }
    }

    @Test
    fun usingTheKeyRefreshesTheInactivityDeadline() {
        session.unlock(byteArrayOf(1, 2, 3))
        now = 90
        session.copyKey().fill(0)
        now = 189
        assertTrue(session.isUnlocked())
        now = 190
        assertFalse(session.isUnlocked())
    }

    @Test
    fun externalBuffersCannotMutateTheSessionKey() {
        val original = byteArrayOf(1, 2, 3)
        session.unlock(original)
        original.fill(0)
        session.copyKey().fill(0)

        assertArrayEquals(byteArrayOf(1, 2, 3), session.copyKey())
    }

    @Test
    fun explicitLockAndNewUnlockDiscardThePreviousSession() {
        session.unlock(byteArrayOf(1))
        session.lock()
        assertFalse(session.isUnlocked())
        session.unlock(byteArrayOf(2))

        assertArrayEquals(byteArrayOf(2), session.copyKey())
    }
}
