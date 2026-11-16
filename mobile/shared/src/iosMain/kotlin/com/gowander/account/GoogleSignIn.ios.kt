package com.gowander.account

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS: Google's SDK is Swift and lives in the app target, so the app implements this small protocol and registers it
 * at launch (ADR-040 §3.5). The token it returns is the same ID token Android gets from Credential Manager; the server
 * checks its signature and audience, so no client secret touches the app.
 */
interface GoogleTokenProvider {
    /**
     * Opens Google's sheet. [onResult] gets the ID token, or null with a message describing the failure; null token
     * with a null message means the person closed the sheet.
     */
    fun signIn(onResult: (token: String?, error: String?) -> Unit)
}

/** Where the app target hands its provider to the shared code. */
object GoogleBridge {
    var provider: GoogleTokenProvider? = null
}

actual object GoogleSignIn {
    actual val available: Boolean get() = GoogleBridge.provider != null

    actual suspend fun signIn(): GoogleResult {
        val provider = GoogleBridge.provider ?: return GoogleResult.Failed("no google provider")
        return suspendCancellableCoroutine { continuation ->
            provider.signIn { token, error ->
                when {
                    token != null -> continuation.resume(GoogleResult.Token(token))
                    error != null -> continuation.resume(GoogleResult.Failed(error))
                    else -> continuation.resume(GoogleResult.Cancelled)
                }
            }
        }
    }
}
