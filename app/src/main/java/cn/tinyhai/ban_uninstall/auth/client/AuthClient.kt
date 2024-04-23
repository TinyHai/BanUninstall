package cn.tinyhai.ban_uninstall.auth.client

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.IBinder.DeathRecipient
import android.util.Log
import cn.tinyhai.ban_uninstall.AuthActivity
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.entites.AuthData
import java.security.MessageDigest

class AuthClient(
    private var remote: IAuth
) : DeathRecipient {

    val isAlive get() = remote.asBinder()?.isBinderAlive == true

    init {
        remote.asBinder()?.linkToDeath(this, 0)
    }

    val hasPwd: Boolean
        get() = remote.hasPwd()

    fun setPwd(newPwd: String) {
        remote.setPwd(newPwd.toSha256())
    }

    fun clearPwd() {
        remote.clearPwd()
    }

    fun authenticate(pwd: String): Boolean {
        return remote.authenticate(pwd.toSha256())
    }

    fun agree(opId: Int) {
        remote.agree(opId)
    }

    fun prevent(opId: Int) {
        remote.prevent(opId)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.toSha256(): String {
        if (isBlank()) {
            return ""
        }
        val msgDigest = MessageDigest.getInstance("SHA-256")
        return runCatching {
            msgDigest.update(toByteArray())
            msgDigest.digest().toHexString()
        }.getOrElse { "" }
    }

    override fun binderDied() {
        remote.asBinder()?.unlinkToDeath(this, 0)
        remote = IAuth.Default()
    }

    companion object {
        private const val TAG = "AuthClient"
        private const val KEY_AUTH = "key_auth"

        private const val KEY_AUTH_DATA = "key_auth_data"

        fun parseFromIntent(intent: Intent): AuthClient {
            val binder = intent.extras?.getBinder(KEY_AUTH)
            Log.d(TAG, "$binder")
            val remote = if (binder?.isBinderAlive == true) {
                IAuth.Stub.asInterface(binder)
            } else {
                IAuth.Default()
            }
            return AuthClient(remote)
        }

        fun getAuthData(intent: Intent): AuthData {
            return intent.getParcelableExtra(KEY_AUTH_DATA) ?: AuthData.Empty
        }

        fun buildAuthIntent(remote: IAuth.Stub, authData: AuthData): Intent {
            val component = ComponentName(BuildConfig.APPLICATION_ID, AuthActivity::class.java.name)
            val bundle = Bundle().apply {
                putBinder(KEY_AUTH, remote.asBinder())
                putParcelable(KEY_AUTH_DATA, authData)
            }
            return Intent().apply {
                setComponent(component)
                putExtras(bundle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        }
    }
}