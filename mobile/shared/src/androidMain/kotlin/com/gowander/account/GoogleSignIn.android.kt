package com.gowander.account

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Android: Credential Manager shows the Google accounts of the phone and returns an ID token for [webClientId] (the
 * Web OAuth client of the project, which is also the token's audience on the server).
 */
actual object GoogleSignIn {
    private var activity: Activity? = null
    private var webClientId: String = ""

    /** Called once from MainActivity, before setContent (like Prefs.init). */
    fun init(activity: Activity, webClientId: String) {
        this.activity = activity
        this.webClientId = webClientId.trim()
    }

    actual val available: Boolean get() = activity != null && webClientId.isNotBlank()

    actual suspend fun signIn(): GoogleResult {
        val host = activity ?: return GoogleResult.Failed("no activity")
        if (!available) return GoogleResult.Failed("no client id")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // show every Google account on the phone, not only the ones used before
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false) // the person picks: no surprise sign-in
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(host.applicationContext).getCredential(host, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleResult.Failed("unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled // the person closed the sheet: nothing to say
        } catch (e: NoCredentialException) {
            GoogleResult.Failed("no google account on this phone")
        } catch (e: GetCredentialException) {
            GoogleResult.Failed(e.type)
        }
    }
}

/** Kept for callers that only have a Context (tests, previews): same rule as [available]. */
internal fun googleAvailable(context: Context?, webClientId: String): Boolean = context != null && webClientId.isNotBlank()
