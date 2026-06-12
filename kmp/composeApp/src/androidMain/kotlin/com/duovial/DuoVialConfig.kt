package com.duovial

data class DuoVialConfig(
    val cognitoUserPoolId: String = "",
    val cognitoClientId: String = "",
    val cognitoRegion: String = "us-east-1"
) {
    val isAuthConfigured: Boolean get() = cognitoUserPoolId.isNotBlank() && cognitoClientId.isNotBlank()
}
