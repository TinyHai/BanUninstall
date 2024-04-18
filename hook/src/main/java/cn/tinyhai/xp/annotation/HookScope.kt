package cn.tinyhai.xp.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class HookScope(
    val targetPackageName: String,
    val targetProcessName: String = "",
    val scopeName: String = "",
    val targetClassName: String = "",
    val autoRegister: Boolean = true,
    val isUnhookable: Boolean = false
)