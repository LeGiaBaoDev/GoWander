package com.gowander.account

/**
 * iOS: the GoogleSignIn SDK lives in the app target (Swift package) and hands the ID token to Kotlin through a small
 * bridge; that arrives in AUTH-2 part 4b. Until then the screens hide the Google button rather than fail.
 */
actual object GoogleSignIn {
    actual val available: Boolean = false
    actual suspend fun signIn(): GoogleResult = GoogleResult.Failed("not wired on iOS yet")
}
