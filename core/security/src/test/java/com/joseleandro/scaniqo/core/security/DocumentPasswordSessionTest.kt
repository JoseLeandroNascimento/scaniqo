package com.joseleandro.scaniqo.core.security

import org.junit.Assert.*
import org.junit.Test

class DocumentPasswordSessionTest {
    private var now = 0L
    private val session = DocumentPasswordSession({ now }, timeoutMillis = 100)

    @Test fun passwordsAreIndependentAndOpaqueIdentifiersContainNoSecret() {
        val first = session.remember("first-password")
        val second = session.remember("second-password")
        assertNotEquals(first, second)
        assertTrue(isDocumentPasswordKey(first))
        assertFalse(first.contains("first-password"))
        assertEquals("first-password", session.copyPassword(first)!!.toString(Charsets.UTF_8))
        assertEquals("second-password", session.copyPassword(second)!!.toString(Charsets.UTF_8))
        assertNull(session.copyPassword("unknown"))
    }

    @Test fun lockAndExpiryReleaseSecretsButAllowVerificationOfUnsavedDraft() {
        val key = session.remember("document-password")
        session.lock()
        assertTrue(session.availableKeys().isEmpty())
        assertNull(session.copyPassword(key))
        assertEquals(false, session.matches(key, "incorrect"))
        assertEquals(true, session.matches(key, "document-password"))
        session.remember("document-password", key)
        now = 100
        assertNull(session.copyPassword(key))
        assertTrue(session.availableKeys().isEmpty())
        session.forget(key)
        assertNull(session.matches(key, "document-password"))
    }

    @Test fun aFreshSessionCannotRecoverAPasswordFromTheStoredIdentifier() {
        val key = session.remember("must-be-kept-by-user")
        val restarted = DocumentPasswordSession({ now })
        assertNull(restarted.copyPassword(key))
        assertNull(restarted.matches(key, "must-be-kept-by-user"))
    }

    @Test fun returnedBuffersCannotAlterTheSessionAndReadingRefreshesTimeout() {
        val key = session.remember("secret")
        now = 90
        session.copyPassword(key)!!.fill(0)
        now = 100
        assertEquals("secret", session.copyPassword(key)!!.toString(Charsets.UTF_8))
        now = 200
        assertNull(session.copyPassword(key))
    }
}
