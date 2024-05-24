package cn.tinyhai.ban_uninstall.auth.client

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import cn.tinyhai.ban_uninstall.AuthActivity
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.auth.IAuth
import cn.tinyhai.ban_uninstall.auth.entities.AuthData
import cn.tinyhai.ban_uninstall.auth.entities.OpRecord
import java.security.MessageDigest

class AuthClient(
    private var service: IAuth
) : IAuth {

    override fun asBinder(): IBinder? {
        return service.asBinder()
    }

    override fun hasPwd(): Boolean {
        return service.hasPwd()
    }

    override fun setPwd(newPwd: String) {
        service.setPwd(newPwd.toSha256())
    }

    override fun clearPwd() {
        service.clearPwd()
    }

    override fun authenticate(pwd: String): Boolean {
        return service.authenticate(pwd.toSha256())
    }

    override fun agree(opId: Int) {
        service.agree(opId)
    }

    override fun prevent(opId: Int) {
        service.prevent(opId)
    }

    override fun getAllOpRecord(): List<OpRecord> {
        return service.allOpRecord ?: emptyList()
    }

    override fun clearAllOpRecord() {
        service.clearAllOpRecord()
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

    companion object {
        private const val TAG = "AuthClient"
        private const val KEY_AUTH = "key_auth"

        private const val KEY_AUTH_DATA = "key_auth_data"

        private var client: AuthClient? = null

        private val Dummy = AuthClient(IAuth.Default())

        val AuthClient.isDummy get() = this === Dummy

        operator fun invoke(): AuthClient {
            return client ?: Dummy
        }

        fun inject(intent: Intent) {
            val binder = intent.extras?.getBinder(KEY_AUTH)
            Log.d(TAG, "$binder")
            if (binder != null) {
                client = AuthClient(IAuth.Stub.asInterface(binder))
            }
        }

        fun parseAuthData(intent: Intent): AuthData {
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
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        or Intent.FLAG_ACTIVITY_NO_HISTORY
                )
            }
        }
    }
}