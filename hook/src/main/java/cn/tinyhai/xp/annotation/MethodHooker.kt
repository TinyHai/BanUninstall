package cn.tinyhai.xp.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MethodHooker(
    val methodName: String,
    val hookType: HookType = HookType.BeforeMethod,
    val className: String = "",
    val minSdkInclusive: Int = 0,
    val maxSdkExclusive: Int = Int.MAX_VALUE
)

enum class HookType {
    BeforeMethod,
    ReplaceMethod,
    AfterMethod
}
