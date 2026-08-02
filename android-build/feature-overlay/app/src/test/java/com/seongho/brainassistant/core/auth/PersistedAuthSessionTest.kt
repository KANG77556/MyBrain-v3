package com.seongho.brainassistant.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedAuthSessionTest {
    @Test
    fun keepsOnlyDisplayIdentity() {
        val session = PersistedAuthSession(email = "teacher@example.com", displayName = "교사")

        assertEquals("teacher@example.com", session.email)
        assertEquals("교사", session.displayName)
    }
}
