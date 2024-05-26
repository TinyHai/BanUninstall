package cn.tinyhai.ban_uninstall.vm

import androidx.lifecycle.ViewModel
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.entities.AuthData

class AuthViewModel : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private var handled: Boolean = false

    private lateinit var authClient: AuthClient

    lateinit var authData: AuthData
        private set

    val hasPwd get() = authClient.hasPwd()

    fun setup(authClient: AuthClient, authData: AuthData) {
        this.authClient = authClient
        this.authData = authData
    }

    fun onAgree() {
        if (handled) {
            return
        }
        authClient.agree(authData.opId)
        handled = true
    }

    fun onPrevent() {
        if (handled) {
            return
        }
        authClient.prevent(authData.opId)
        handled = true
    }

    fun isValid(): Boolean {
        return authClient.isValid(authData.opId)
    }

    fun authenticate(pwd: String): Boolean {
        return authClient.authenticate(pwd)
    }

    override fun onCleared() {
        super.onCleared()
        if (!handled) {
            onPrevent()
            handled = true
        }
    }
}