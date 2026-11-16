package com.gowander.account

/**
 * The native Google sheet of this platform (ADR-040 §3.5). It returns a Google **ID token**; the server checks its
 * signature and audience, so no client secret exists on the device or in the app.
 *
 * Android: Credential Manager (`androidx.credentials`). iOS: the GoogleSignIn SDK, wired in AUTH-2 part 4b.
 */
expect object GoogleSignIn {
    /** True when this build can open Google's sheet; the screens hide the button otherwise. */
    val available: Boolean

    /** Opens the sheet. Cancelled and Failed are different to the person: one is silence, the other a sentence. */
    suspend fun signIn(): GoogleResult
}

/** What came out of Google's sheet. */
sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult

    /** The person closed the sheet: no message, no state change. */
    data object Cancelled : GoogleResult

    /** No Google account on the phone, Play services unavailable, or another failure we cannot fix for them. */
    data class Failed(val reason: String? = null) : GoogleResult
}
