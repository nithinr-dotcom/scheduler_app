package com.wac.worktrack.ui

import android.app.KeyguardManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock using the phone's own fingerprint / face / screen lock (no separate app PIN to
 * forget). The app locks again after it has been in the background for [RELOCK_AFTER_MILLIS].
 */
object AppLock {
    const val RELOCK_AFTER_MILLIS = 30_000L

    /** Fingerprint/face, falling back to the screen lock PIN/pattern — the one combination supported on every Android version. */
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock (or a biometric) to check against. */
    fun canLock(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS ||
            activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit, onFailed: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailed(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock WorkTrack")
                .setSubtitle("Your tasks, journal and money stay private")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
