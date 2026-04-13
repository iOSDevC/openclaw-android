package com.openclaw.android

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class BiometricHelper(
    private val activity: AppCompatActivity,
) {
    companion object {
        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    fun canAuthenticate(): Int = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)

    fun authenticateForAdvancedMode(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val result = canAuthenticate()
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure(errorMessageFor(result))
            return
        }

        val promptInfo =
            BiometricPrompt
                .PromptInfo
                .Builder()
                .setTitle("Enable Advanced Mode")
                .setSubtitle("Authenticate to temporarily unlock privileged features")
                .setDescription("Advanced Mode enables temporary command execution and maintenance actions.")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        onFailure(errString.toString())
                    }
                },
            )

        prompt.authenticate(promptInfo)
    }

    private fun errorMessageFor(result: Int): String =
        when (result) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set up a biometric or device credential before enabling Advanced Mode."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device does not support biometric or device credential authentication."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Authentication hardware is currently unavailable."
            else -> "Authentication is unavailable on this device."
        }
}
