package com.duovial.auth

interface AuthService {
    suspend fun login(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun confirmSignUp(email: String, code: String)
    suspend fun resendConfirmationCode(email: String)
    suspend fun logout()
    suspend fun getCurrentUser(): AuthUser?
}
