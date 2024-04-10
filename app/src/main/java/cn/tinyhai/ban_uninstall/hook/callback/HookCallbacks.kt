package cn.tinyhai.ban_uninstall.hook.callback

import android.os.Handler
import de.robv.android.xposed.XC_MethodHook

fun beforeMethod(block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        block(param)
    }
}

private fun getHandler(obj: Any): Handler? {
    val outer = runCatching { obj::class.java.getDeclaredField("this\$0") }
        .onSuccess { it.isAccessible = true }
        .getOrNull()?.get(obj)
    val pms = outer ?: obj
    return runCatching {
        pms::class.java.getDeclaredField("mHandler")
    }.onSuccess {
        it.isAccessible = true
    }.getOrNull()?.get(pms) as? Handler
}

fun beforeMethodWithPost(block: (XC_MethodHook.MethodHookParam, post: (Runnable) -> Unit) -> Unit) =
    object : XC_MethodHook() {
        private var pmHandler: Handler? = null

        override fun beforeHookedMethod(param: MethodHookParam) {
            val handler = pmHandler ?: getHandler(param.thisObject)?.also {
                pmHandler = it
            }

            fun post(runnable: Runnable) {
                handler?.post(runnable)
            }
            block(param, ::post)
        }
    }

fun afterMethod(block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        block(param)
    }
}