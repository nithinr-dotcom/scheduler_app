package com.wac.worktrack.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Google Sign-In through Firebase. Everything here is a no-op until google-services.json is added. */
object Auth {

    fun firebaseReady(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun user(context: Context): FirebaseUser? =
        if (firebaseReady(context)) FirebaseAuth.getInstance().currentUser else null

    /**
     * OAuth web client id generated from google-services.json by the Google Services plugin.
     * Looked up by name because the resource doesn't exist until that file is added.
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    suspend fun signIn(activity: Activity): Result<FirebaseUser> = runCatching {
        val clientId = webClientId(activity) ?: error("Firebase is not configured (no web client id).")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (_: NoCredentialException) {
            error("No Google account found on this phone. Add one in system Settings → Accounts.")
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("Sign-in returned no user")
    }

    suspend fun signOut(context: Context) {
        if (!firebaseReady(context)) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
