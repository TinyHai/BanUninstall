package cn.tinyhai.ban_uninstall.hooker

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.xp.annotation.HookScope
import cn.tinyhai.xp.annotation.HookType
import cn.tinyhai.xp.annotation.MethodHooker
import de.robv.android.xposed.XC_MethodHook

@HookScope(
    targetPackageName = PKG_ANDROID,
    targetProcessName = PKG_ANDROID
)
class HookInjectSelf {

    @MethodHooker(
        className = "android.app.servertransaction.LaunchActivityItem",
        methodName = "",
        hookType = HookType.BeforeMethod,
        minSdkInclusive = Build.VERSION_CODES.BAKLAVA
    )
    fun beforeConstructors(param: XC_MethodHook.MethodHookParam) {
        handleLaunchActivity(param)
    }

    @MethodHooker(
        className = "android.app.servertransaction.LaunchActivityItem",
        methodName = "obtain",
        hookType = HookType.BeforeMethod,
        minSdkInclusive = Build.VERSION_CODES.P,
        maxSdkExclusive = Build.VERSION_CODES.BAKLAVA
    )
    fun beforeObtain(param: XC_MethodHook.MethodHookParam) {
        handleLaunchActivity(param)
    }

    @MethodHooker(
        className = "android.app.IApplicationThread\$Stub\$Proxy",
        methodName = "scheduleLaunchActivity",
        hookType = HookType.BeforeMethod,
        minSdkInclusive = Build.VERSION_CODES.O,
        maxSdkExclusive = Build.VERSION_CODES.P
    )
    fun beforeScheduleLaunchActivity0(param: XC_MethodHook.MethodHookParam) {
        handleLaunchActivity(param)
    }

    @MethodHooker(
        className = "android.app.ApplicationThreadNative",
        methodName = "scheduleLaunchActivity",
        hookType = HookType.BeforeMethod,
        maxSdkExclusive = Build.VERSION_CODES.O
    )
    fun beforeScheduleLaunchActivity1(param: XC_MethodHook.MethodHookParam) {
        handleLaunchActivity(param)
    }

    private fun handleLaunchActivity(param: XC_MethodHook.MethodHookParam) {
        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
        val aInfo = param.args.firstOrNull { it is ActivityInfo } as? ActivityInfo ?: return
        val userId = aInfo.applicationInfo.uid / 100_000
        TransactClient.injectBinderIfNeeded(TransactService, intent, userId)
    }
}