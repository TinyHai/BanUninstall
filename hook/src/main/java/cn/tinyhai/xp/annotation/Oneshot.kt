package cn.tinyhai.xp.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Oneshot(
    val unhookable: Boolean = false
)
