package cn.tinyhai.ban_uninstall.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.compose.ui.util.fastReduce
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BiometricUtils {

    private fun createAuthCallback(cont: CancellableContinuation<Result<Boolean>>) =
        object : BiometricPrompt.AuthenticationCallback() {

            private var count = 5

            override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                cont.resume(Result.success(true))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == ERROR_USER_CANCELED && count <= 0) {
                    cont.resume(Result.success(false))
                    return
                }
                cont.resume(Result.success(false))
            }

            override fun onAuthenticationFailed() {
                count -= 1
                if (count <= 0) {
                    biometricPrompt?.cancelAuthentication()
                }
            }
        }

    private var biometricPrompt: BiometricPrompt? = null

    private val auths = intArrayOf(Authenticators.BIOMETRIC_WEAK, Authenticators.DEVICE_CREDENTIAL)

    suspend fun auth(context: Context, title: String, description: String): Result<Boolean> {
        val bm = BiometricManager.from(context)
        val canAuths = auths.filter { bm.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS }
        if (canAuths.isEmpty()) {
            // no auths default return true
            return Result.success(true)
        }
        return suspendCancellableCoroutine {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setAllowedAuthenticators(canAuths.fastReduce { acc, i -> acc or i })
                .setTitle(title)
                .setDescription(description)
                .build()
            biometricPrompt =
                BiometricPrompt(context as FragmentActivity, createAuthCallback(it)).also {
                    it.authenticate(info)
                }
        }
    }

    private fun createCryptoObject(): BiometricPrompt.CryptoObject {
        TODO()
    }
}