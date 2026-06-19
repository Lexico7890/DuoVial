package com.duovial.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthModeTest {

    @Test
    fun `all AuthMode values exist`() {
        val values = AuthMode.entries
        assertEquals(3, values.size)
        assertNotNull(AuthMode.LOGIN)
        assertNotNull(AuthMode.SIGNUP)
        assertNotNull(AuthMode.CONFIRM)
    }

    @Test
    fun `AuthUser default values`() {
        val user = AuthUser()
        assertEquals("", user.email)
        assertEquals("", user.username)
        assertFalse(user.isLoggedIn)
        assertFalse(user.needsConfirmation)
    }

    @Test
    fun `AuthUser with values`() {
        val user = AuthUser(
            email = "test@duovial.com",
            username = "testuser",
            isLoggedIn = true,
            needsConfirmation = false
        )
        assertEquals("test@duovial.com", user.email)
        assertEquals("testuser", user.username)
        assertTrue(user.isLoggedIn)
        assertFalse(user.needsConfirmation)
    }

    @Test
    fun `AuthUser needsConfirmation`() {
        val user = AuthUser(
            email = "new@duovial.com",
            needsConfirmation = true
        )
        assertTrue(user.needsConfirmation)
        assertFalse(user.isLoggedIn)
    }

    @Test
    fun `AuthState default values`() {
        val state = AuthState()
        assertEquals(null, state.user)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
        assertEquals(AuthMode.LOGIN, state.mode)
    }
}
