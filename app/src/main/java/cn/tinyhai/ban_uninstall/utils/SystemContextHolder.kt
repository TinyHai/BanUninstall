package cn.tinyhai.ban_uninstall.utils

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object SystemContextHolder {
    private val callbacks = ArrayList<Callback>()

    private var systemContext: Context? = null
    val context: Context get() = systemContext ?: throw IllegalStateException()

    inline fun <T> withSystemContext(block: Context.() -> T): T {
        return context.block()
    }

    fun onSystemContext(context: Context) {
        this.systemContext = context
        val callbacks = callbacks.toList().also { callbacks.clear() }
        callbacks.forEach {
            it.onSystemContext(context)
        }
    }

    fun registerCallback(callback: Callback) {
        if (systemContext == null) {
            callbacks.add(callback)
        } else {
            callback.onSystemContext(context)
        }
    }

    fun interface Callback {
        fun onSystemContext(context: Context)
    }
}