package cn.tinyhai.ban_uninstall.hooker

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.transact.server.TransactService
import cn.tinyhai.xp.annotation.HookScope
import cn.tinyhai.xp.annotation.HookType
import cn.tinyhai.xp.annotation.MethodHooker
import de.robv.android.xposed.XC_MethodHook.MethodHookParam

@HookScope(
    targetPackageName = "android",
    targetProcessName = "android",
    autoRegister = false,
)
class HookLaunchItemForInjectClient {

    @MethodHooker(
        className = "android.app.servertransaction.LaunchActivityItem",
        methodName = "obtain",
        hookType = HookType.BeforeMethod,
        minSdkInclusive = Build.VERSION_CODES.P
    )
    fun beforeObtain(param: MethodHookParam) {
        val intent = param.args[0] as Intent
        val aInfo = param.args[2] as ActivityInfo
        val userId = aInfo.applicationInfo.uid / 100_000
        TransactClient.injectBinderIfNeeded(TransactService, intent, userId)
    }

    @MethodHooker(
        className = "android.app.IApplicationThread\$Stub\$Proxy",
        methodName = "scheduleLaunchActivity",
        hookType = HookType.BeforeMethod,
        minSdkInclusive = Build.VERSION_CODES.O,
        maxSdkExclusive = Build.VERSION_CODES.P
    )
    fun beforeScheduleLaunchActivity0(param: MethodHookParam) {
        beforeScheduleLaunchActivity(param)
    }

    @MethodHooker(
        className = "android.app.ApplicationThreadNative",
        methodName = "scheduleLaunchActivity",
        hookType = HookType.BeforeMethod,
        maxSdkExclusive = Build.VERSION_CODES.O
    )
    fun beforeScheduleLaunchActivity1(param: MethodHookParam) {
        beforeScheduleLaunchActivity(param)
    }

    private fun beforeScheduleLaunchActivity(param: MethodHookParam) {
        val intent = param.args[0] as Intent
        val aInfo = param.args[3] as ActivityInfo
        val userId = aInfo.applicationInfo.uid / 100_000
        TransactClient.injectBinderIfNeeded(TransactService, intent, userId)
    }
}