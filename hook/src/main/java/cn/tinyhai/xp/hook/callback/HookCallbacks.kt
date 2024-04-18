package cn.tinyhai.xp.hook.callback

import de.robv.android.xposed.XC_MethodHook

fun beforeMethod(block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        block(param)
    }
}

fun afterMethod(block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        block(param)
    }
}