package com.duovial.auth

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthStateManagerTest {

    @Test
    fun `initial auth state has no user`() = runTest {
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertNull(state.user)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(AuthMode.LOGIN, state.mode)
        }
    }

    @Test
    fun `setLoading activates loading state`() = runTest {
        AuthStateManager.setLoading(true)
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `setLoading clears error`() = runTest {
        AuthStateManager.setError("some error")
        AuthStateManager.setLoading(true)
        AuthStateManager.authState.test {
            assertEquals(true, awaitItem().isLoading)
        }
    }

    @Test
    fun `setError sets message and clears loading`() = runTest {
        AuthStateManager.setLoading(true)
        AuthStateManager.setError("Invalid credentials")
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertEquals("Invalid credentials", state.error)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `setUser sets logged in user`() = runTest {
        val user = AuthUser(email = "test@test.com", isLoggedIn = true)
        AuthStateManager.setUser(user)
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertEquals("test@test.com", state.user?.email)
            assertTrue(state.user?.isLoggedIn == true)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `setLoggedOut clears user`() = runTest {
        AuthStateManager.setUser(AuthUser(email = "test@test.com", isLoggedIn = true))
        AuthStateManager.setLoggedOut()
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertNull(state.user)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `setNeedsConfirmation transitions to CONFIRM mode`() = runTest {
        AuthStateManager.setNeedsConfirmation("confirm@test.com")
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertEquals("confirm@test.com", state.user?.email)
            assertTrue(state.user?.needsConfirmation == true)
            assertEquals(AuthMode.CONFIRM, state.mode)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `setMode changes auth mode`() = runTest {
        AuthStateManager.setMode(AuthMode.SIGNUP)
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertEquals(AuthMode.SIGNUP, state.mode)
        }
    }

    @Test
    fun `setMode clears error`() = runTest {
        AuthStateManager.setError("previous error")
        AuthStateManager.setMode(AuthMode.LOGIN)
        AuthStateManager.authState.test {
            assertNull(awaitItem().error)
        }
    }

    @Test
    fun `clearError removes error message`() = runTest {
        AuthStateManager.setError("some error")
        AuthStateManager.clearError()
        AuthStateManager.authState.test {
            assertNull(awaitItem().error)
        }
    }

    @Test
    fun `setMode preserves user`() = runTest {
        AuthStateManager.setUser(AuthUser(email = "keep@test.com", isLoggedIn = true))
        AuthStateManager.setMode(AuthMode.CONFIRM)
        AuthStateManager.authState.test {
            val state = awaitItem()
            assertEquals("keep@test.com", state.user?.email)
            assertEquals(AuthMode.CONFIRM, state.mode)
        }
    }
}
